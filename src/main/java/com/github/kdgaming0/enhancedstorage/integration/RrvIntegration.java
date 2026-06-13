package com.github.kdgaming0.enhancedstorage.integration;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.Rect;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
        clearBlocking();
        try {
            for (int i = 0; i < bounds.size(); i++) {
                Rect rect = bounds.get(i);
                Identifier id = Identifier.fromNamespaceAndPath(EnhancedStorage.MOD_ID, "storage_overlay_" + i);
                Object component = blockingComponentConstructor.newInstance(
                        id, rect.x, rect.y, rect.width, rect.height);
                setGuiBlockingMethod.invoke(overlayManagerInstance, component);
            }
            updateOverlaysAndWidgetsMethod.invoke(overlayManagerInstance, true);
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to set RVV blocking components", e);
        }
    }

    /**
     * Removes all blocking components owned by this mod and tells RVV to update its overlays.
     */
    public static void clearBlocking() {
        initialize();
        if (!initialized) {
            return;
        }
        try {
            Predicate<Identifier> filter = id -> EnhancedStorage.MOD_ID.equals(id.getNamespace());
            removeGuiBlockingMethod.invoke(overlayManagerInstance, filter, true);
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to clear RVV blocking components", e);
        }
    }
}
