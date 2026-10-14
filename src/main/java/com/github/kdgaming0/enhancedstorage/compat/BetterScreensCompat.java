package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Better Screens' container scale: while it's on, Better Screens draws container screens (and, through
 * {@code BetterScreensContainerScaleMixin}, Edit Mode) at its own GUI scale with a second GUI renderer. It switches
 * the window to that scale only while the screen is extracted and drawn, resizes the screen to it once before the
 * first frame, and converts the mouse for it.
 *
 * <p>Our screens then have to leave the window's scale alone and keep the size Better Screens gave them; see
 * {@link com.github.kdgaming0.enhancedstorage.gui.OverlayScale#sync}. Better Screens isn't a dependency, so its own
 * check is called by name. It reads the current screen, which vanilla sets before {@code init}, and exists in both
 * the 26.1 (2.x) and 26.2 (3.x) versions. If it can't be called, the overlay behaves as without Better Screens.
 */
public final class BetterScreensCompat {

    private static final @Nullable MethodHandle SHOULD_SCALE = resolve();
    private static boolean warned;

    private BetterScreensCompat() {
    }

    /**
     * True while Better Screens draws the current screen at its own scale.
     */
    public static boolean scalesCurrentScreen() {
        if (SHOULD_SCALE == null) return false;
        try {
            return (boolean) SHOULD_SCALE.invokeExact();
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                EnhancedStorage.LOGGER.warn("Could not ask Better Screens whether it scales this screen; using our own GUI scale.", t);
            }
            return false;
        }
    }

    private static @Nullable MethodHandle resolve() {
        if (!FabricLoader.getInstance().isModLoaded("betterscreens")) return null;
        try {
            Class<?> hook = Class.forName("dev.microcontrollers.betterscreens.hook.ContainerScaleHook");
            return MethodHandles.publicLookup().findStatic(hook, "shouldScale", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            EnhancedStorage.LOGGER.warn("Could not find Better Screens' container scale check; using our own GUI scale.", e);
            return null;
        }
    }
}
