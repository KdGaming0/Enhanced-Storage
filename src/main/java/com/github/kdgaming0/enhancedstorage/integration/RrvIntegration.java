package com.github.kdgaming0.enhancedstorage.integration;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.Rect;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Optional integration with Reliable Recipe Viewer (RVV).
 *
 * <p>RVV exposes a list of {@code BlockingGuiComponent} rectangles that its overlays avoid when
 * positioning the item list. We register the screen-space bounds of our storage overlay so RVV
 * shrinks or moves its list out of the way instead of rendering underneath our panels.
 *
 * <p>All interaction is done via reflection so that RVV remains a soft dependency.
 */
public final class RrvIntegration {
    private static final String RRV_MOD_ID = "rrv";
    private static final String OVERLAY_MANAGER_CLASS = "cc.cassian.rrv.common.overlay.OverlayManager";
    private static final String BLOCKING_COMPONENT_CLASS = "cc.cassian.rrv.common.overlay.BlockingGuiComponent";

    private static final boolean RRV_PRESENT = FabricLoader.getInstance().isModLoaded(RRV_MOD_ID);

    private static Object overlayManagerInstance;
    private static Constructor<?> blockingComponentConstructor;
    private static Method setGuiBlockingMethod;
    private static Method removeGuiBlockingMethod;
    private static Method updateOverlaysAndWidgetsMethod;
    private static boolean initialized;

    /** Bounds last registered with RVV; lets repeat calls with identical bounds skip all list churn. */
    private static List<Rect> lastBounds;

    private RrvIntegration() {
    }

    private static void initialize() {
        if (initialized || !RRV_PRESENT) {
            return;
        }
        try {
            Class<?> overlayManagerClass = Class.forName(OVERLAY_MANAGER_CLASS);
            overlayManagerInstance = overlayManagerClass.getField("INSTANCE").get(null);

            Class<?> blockingComponentClass = Class.forName(BLOCKING_COMPONENT_CLASS);
            blockingComponentConstructor = blockingComponentClass.getConstructor(
                    Identifier.class, int.class, int.class, int.class, int.class);

            setGuiBlockingMethod = overlayManagerClass.getMethod("setGuiBlocking", blockingComponentClass);
            removeGuiBlockingMethod = overlayManagerClass.getMethod("removeGuiBlocking", Predicate.class, boolean.class);
            updateOverlaysAndWidgetsMethod = overlayManagerClass.getMethod("updateOverlaysAndWidgets", boolean.class);

            initialized = true;
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to initialize Reliable Recipe Viewer integration", e);
        }
    }

    /**
     * Registers the given screen-space rectangles as RVV blocking components.
     * Any previously registered components from this mod are replaced.
     */
    public static void setBlocking(List<Rect> bounds) {
        initialize();
        if (!initialized) {
            return;
        }
        try {
            if (boundsEqual(bounds, lastBounds)) {
                // Same bounds (e.g. page navigation): skip the structural list churn that races
                // RVV's background reader, but STILL ask RVV to re-apply blocking. RVV rebuilds its
                // item list per screen and only repositions it around our (still-registered)
                // components when updateOverlaysAndWidgets runs (-> onScreenChanged ->
                // updateSidePanelIndex -> isPositionBlocked). Skipping it entirely left the list
                // rendering over the overlay after the first screen. This call only *reads* the
                // blocking list, so on its own it can't trigger the CME — only structural
                // (add/remove) mutations can, and those are confined to the bounds-changed path.
                updateOverlaysAndWidgetsMethod.invoke(overlayManagerInstance, true);
                return;
            }
            // Bounds changed: remove our previous components WITHOUT kicking RVV's update, add the
            // new set, then fire a single update at the end — so the list is never structurally
            // mutated after we trigger the background read within this call.
            removeOurComponents(false);
            for (int i = 0; i < bounds.size(); i++) {
                Rect rect = bounds.get(i);
                Identifier id = Identifier.fromNamespaceAndPath(EnhancedStorage.MOD_ID, "storage_overlay_" + i);
                Object component = blockingComponentConstructor.newInstance(
                        id, rect.x, rect.y, rect.width, rect.height);
                setGuiBlockingMethod.invoke(overlayManagerInstance, component);
            }
            updateOverlaysAndWidgetsMethod.invoke(overlayManagerInstance, true);
            lastBounds = copyOf(bounds);
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to set RVV blocking components", e);
            lastBounds = null;
        }
    }

    /**
     * Removes all blocking components owned by this mod and tells RVV to update its overlays.
     * Called on full overlay teardown ({@code StorageOverlay.destroyActive}) — not on the
     * {@code detach()} that runs during each page navigation — so the registration persists across
     * the persistent overlay's screen transitions and avoids per-navigation list churn.
     */
    public static void clearBlocking() {
        initialize();
        if (!initialized) {
            return;
        }
        removeOurComponents(true);
        lastBounds = null;
    }

    private static void removeOurComponents(boolean update) {
        try {
            Predicate<Identifier> filter = id -> EnhancedStorage.MOD_ID.equals(id.getNamespace());
            removeGuiBlockingMethod.invoke(overlayManagerInstance, filter, update);
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to clear RVV blocking components", e);
        }
    }

    private static boolean boundsEqual(List<Rect> a, List<Rect> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Rect r1 = a.get(i);
            Rect r2 = b.get(i);
            if (r1.x != r2.x || r1.y != r2.y || r1.width != r2.width || r1.height != r2.height) {
                return false;
            }
        }
        return true;
    }

    private static List<Rect> copyOf(List<Rect> bounds) {
        List<Rect> copy = new ArrayList<>(bounds.size());
        for (Rect r : bounds) {
            copy.add(new Rect(r.x, r.y, r.width, r.height));
        }
        return copy;
    }
}
