package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Room for SkyOcean's craft helper panel on the storage screen.
 *
 * <p>SkyOcean adds the panel to every container screen, at the screen's left or right edge (or a corner), and sizes
 * it from the gap left of the container: {@code max(50, leftPos - 2 * margin - 28)}. Our reported container spans
 * nearly the whole screen, so the panel came out squashed and on top of the pages. Instead the overlay keeps a column
 * free on the panel's side, and {@code SkyOceanCraftHelperDisplayMixin} lets the panel size itself to its content up
 * to {@link #MAX_SCREEN_SHARE} of the screen. The column is then measured from the panel SkyOcean built.
 *
 * <p>Whether a panel will show can't be told in advance: removing a recipe leaves an empty recipe behind rather than
 * none, and SkyOcean only finds out it has nothing to show while building. So the column follows the panel SkyOcean
 * actually built, and a new screen starts with whatever the previous storage screen had.
 *
 * <p>SkyOcean only adds the panel when Fabric's after-init event fires, which our own relayouts
 * ({@code rebuildWidgets}) don't do, so the screen calls {@link #addPanel} after them.
 *
 * <p>SkyOcean isn't a compile dependency (it's Kotlin, built per Minecraft version), so everything here is reflective.
 * If anything has moved, the compat logs once and turns off: no column is reserved and the panel behaves as before.
 */
public final class SkyOceanCompat {

    public enum Side { NONE, LEFT, RIGHT }

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("skyocean");

    private static final String CONFIG = "me.owdding.skyocean.config.features.misc.crafthelper.CraftHelperConfig";
    private static final String STORAGE = "me.owdding.skyocean.data.profile.CraftHelperStorage";
    private static final String DISPLAY = "me.owdding.skyocean.features.recipe.crafthelper.display.CraftHelperDisplay";
    private static final String CATHARSIS = "me.owdding.skyocean.compat.CatharsisSupport";
    private static final String INIT_EVENT = "tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent";
    private static final String CATHARSIS_ELEMENT = "skyocean:crafthelper";

    /** The panel's background adds 14px on each side of its content; SkyOcean subtracts this from the gap. */
    private static final int BACKGROUND_PADDING = 28;
    /** SkyOcean's minimum content width. */
    private static final int MIN_CONTENT_WIDTH = 50;
    /** The panel (with its margin) never takes more than this share of the screen width. */
    private static final double MAX_SCREEN_SHARE = 0.25;

    private static boolean resolved;
    private static boolean failed;
    private static Object config;
    private static Method getEnabled;
    private static Method getPosition;
    private static Method getMargin;
    private static Object storage;
    private static Method getData;
    private static @Nullable Object catharsis;
    private static @Nullable Method isModElementHidden;
    private static Object display;
    private static Method onScreenInit;
    private static Field craftHelperLayout;
    private static Constructor<?> initEvent;

    // The panel SkyOcean built for the storage screen, kept here because SkyOcean clears its own reference when a
    // container closes, and the previous page's close arrives after the next page has built its panel.
    private static @Nullable LayoutElement panel;
    // Panel extent (screen edge to its far side) on the last storage screen, 0 when it had none, so the next page
    // opens with the same layout.
    private static int lastExtent;

    private SkyOceanCompat() {
    }

    /**
     * Which side SkyOcean's position setting puts the panel on, or NONE when the craft helper is off, is hidden by a
     * Catharsis pack, or SkyOcean is absent or unreadable. Says nothing about whether there's a recipe to show.
     */
    public static Side panelSide() {
        if (!resolve()) return Side.NONE;
        try {
            if (!(boolean) getEnabled.invoke(config)) return Side.NONE;
            if (isModElementHidden != null && (boolean) isModElementHidden.invoke(catharsis, CATHARSIS_ELEMENT)) {
                return Side.NONE;
            }
            String position = ((Enum<?>) getPosition.invoke(config)).name();
            return position.contains("LEFT") ? Side.LEFT : Side.RIGHT;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("reading the craft helper settings failed", e);
            return Side.NONE;
        }
    }

    /**
     * The current recipe object. SkyOcean replaces it whenever a recipe is set or removed, so a different object means
     * the panel may have appeared or gone. Null when SkyOcean is absent or unreadable.
     */
    public static @Nullable Object recipe() {
        if (!resolve()) return null;
        try {
            return getData.invoke(storage);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("reading the craft helper recipe failed", e);
            return null;
        }
    }

    /**
     * Width to keep free before this screen's panel has been measured: what the last storage screen's panel took.
     */
    public static int expectedExtent() {
        return lastExtent;
    }

    /**
     * Width the panel SkyOcean last built takes from its screen edge (its margin plus its own width), or 0 when no
     * panel was built since {@link #forgetPanel}. The panel keeps its object when it resizes itself (expanding its
     * tree), so this follows that too.
     */
    public static int measuredExtent() {
        LayoutElement current = panel;
        int extent = 0;
        if (current != null && resolve()) {
            try {
                extent = margin() + current.getWidth();
            } catch (ReflectiveOperationException | RuntimeException e) {
                fail("measuring the craft helper panel failed", e);
            }
        }
        lastExtent = extent;
        return extent;
    }

    /**
     * Forgets the last panel, so {@link #measuredExtent} only sees a panel built after this.
     */
    public static void forgetPanel() {
        panel = null;
    }

    /**
     * Called before SkyOcean sets up a screen. It only sets its panel reference when it builds a panel, so clearing it
     * first means {@link #capturePanel} can't pick up the previous screen's panel.
     */
    public static void beforeSkyOceanInit() {
        if (!(Minecraft.getInstance().screen instanceof StorageContainerScreen) || !resolve()) return;
        try {
            craftHelperLayout.set(null, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("clearing the craft helper panel failed", e);
        }
    }

    /**
     * Called when SkyOcean has finished setting up a screen: keeps the panel it just built for the storage screen.
     */
    public static void capturePanel() {
        if (!(Minecraft.getInstance().screen instanceof StorageContainerScreen) || !resolve()) return;
        try {
            panel = craftHelperLayout.get(null) instanceof LayoutElement built ? built : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("reading the craft helper panel failed", e);
        }
    }

    /**
     * Has SkyOcean add its panel to the screen, as it does when the screen first opens.
     */
    public static void addPanel(Screen screen) {
        if (!resolve()) return;
        forgetPanel();
        try {
            onScreenInit.invoke(display, initEvent.newInstance(screen));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("adding the craft helper panel failed", e);
        }
    }

    /**
     * Replaces SkyOcean's "left of the container" value on our screen so the panel's maximum width comes out as the
     * largest column we're willing to give it. The panel still shrinks to its content.
     */
    public static int panelRoom(int original) {
        if (!(Minecraft.getInstance().screen instanceof StorageContainerScreen screen) || !resolve()) return original;
        try {
            int margin = margin();
            int content = Math.max(MIN_CONTENT_WIDTH, maxExtent(screen.width) - margin - BACKGROUND_PADDING);
            return content + 2 * margin + BACKGROUND_PADDING;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("sizing the craft helper panel failed", e);
            return original;
        }
    }

    private static int maxExtent(int screenWidth) {
        return (int) (screenWidth * MAX_SCREEN_SHARE);
    }

    private static int margin() throws ReflectiveOperationException {
        return (int) getMargin.invoke(config);
    }

    private static boolean resolve() {
        if (!LOADED || failed) return false;
        if (resolved) return true;
        resolved = true;
        try {
            Class<?> configClass = Class.forName(CONFIG);
            config = configClass.getField("INSTANCE").get(null);
            getEnabled = configClass.getMethod("getEnabled");
            getPosition = configClass.getMethod("getPosition");
            getMargin = configClass.getMethod("getMargin");

            Class<?> storageClass = Class.forName(STORAGE);
            storage = storageClass.getField("INSTANCE").get(null);
            getData = storageClass.getMethod("getData");

            Class<?> displayClass = Class.forName(DISPLAY);
            display = displayClass.getField("INSTANCE").get(null);
            Class<?> eventClass = Class.forName(INIT_EVENT);
            initEvent = eventClass.getConstructor(Screen.class);
            onScreenInit = displayClass.getDeclaredMethod("onScreenInit", eventClass);
            onScreenInit.setAccessible(true);
            craftHelperLayout = displayClass.getDeclaredField("craftHelperLayout");
            craftHelperLayout.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail("this SkyOcean version doesn't match what Enhanced Storage expects", e);
            return false;
        }
        try {
            // Optional: older or newer versions may not support hiding the panel from a pack.
            Class<?> catharsisClass = Class.forName(CATHARSIS);
            catharsis = catharsisClass.getField("INSTANCE").get(null);
            isModElementHidden = catharsisClass.getMethod("isModElementHidden", String.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            catharsis = null;
            isModElementHidden = null;
        }
        return true;
    }

    private static void fail(String what, Throwable e) {
        if (failed) return;
        failed = true;
        EnhancedStorage.LOGGER.warn("SkyOcean craft helper support disabled: {}. The panel will show as it would without Enhanced Storage's layout support.", what, e);
    }
}
