package com.github.kdgaming0.enhancedstorage.integration;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

/**
 * Optional integration with Skyblocker.
 *
 * <p>Skyblocker adds {@code QuickNavButton} widgets around container screens. When Enhanced
 * Storage's overlay is active we reposition those buttons so they sit above and below our custom
 * panels like creative-inventory tabs, and we reserve vertical space so they don't overlap the
 * overlay content.
 *
 * <p>The actual coordinate-update suppression is done by {@code QuickNavButtonMixin} (only applied
 * when Skyblocker is loaded). Positioning is done via reflection here so Skyblocker remains a soft
 * dependency.
 */
public final class SkyblockerIntegration {
    private static final String SKYBLOCKER_MOD_ID = "skyblocker";
    private static final String QUICK_NAV_BUTTON_CLASS = "de.hysky.skyblocker.skyblock.quicknav.QuickNavButton";
    private static final String ABSTRACT_WIDGET_CLASS = "net.minecraft.client.gui.components.AbstractWidget";

    private static final boolean SKYBLOCKER_PRESENT = FabricLoader.getInstance().isModLoaded(SKYBLOCKER_MOD_ID);

    private static Class<?> quickNavButtonClass;
    private static Method setXMethod;
    private static Method setYMethod;
    private static boolean initialized;

    private SkyblockerIntegration() {
    }

    private static void initialize() {
        if (initialized || !SKYBLOCKER_PRESENT) {
            return;
        }
        try {
            quickNavButtonClass = Class.forName(QUICK_NAV_BUTTON_CLASS);
            Class<?> abstractWidgetClass = Class.forName(ABSTRACT_WIDGET_CLASS);
            setXMethod = abstractWidgetClass.getMethod("setX", int.class);
            setYMethod = abstractWidgetClass.getMethod("setY", int.class);
            initialized = true;
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to initialize Skyblocker integration", e);
        }
    }

    /**
     * Returns {@code true} when Skyblocker is loaded.
     */
    public static boolean isActive() {
        return SKYBLOCKER_PRESENT;
    }

    /**
     * Returns {@code true} when Skyblocker is loaded, reflection is initialized, and the given
     * screen actually contains QuickNav buttons. Use this to gate padding reservation so that
     * disabling QuickNav in Skyblocker's config doesn't leave unused space in the overlay.
     */
    public static boolean hasQuickNavButtons(Screen screen) {
        if (!SKYBLOCKER_PRESENT) return false;
        initialize();
        if (!initialized) return false;
        for (Object child : screen.children()) {
            if (quickNavButtonClass.isInstance(child)) return true;
        }
        return false;
    }

    /**
     * Repositions any Skyblocker QuickNav buttons on the given screen.
     *
     * <p>Buttons 0-6 are laid out horizontally starting at {@code topX, topY}; buttons 7-13 start
     * at {@code bottomX, bottomY}. Skyblocker's own coordinate update is suppressed by the mixin
     * while the overlay is active, so these positions stick.
     */
    public static void positionQuickNav(Screen screen, int topX, int topY, int bottomX, int bottomY) {
        initialize();
        if (!initialized) {
            return;
        }
        try {
            int buttonCount = 0;
            for (Object child : screen.children()) {
                if (!quickNavButtonClass.isInstance(child)) {
                    continue;
                }
                // Skyblocker creates buttons 0-6 as top tabs and 7-13 as bottom tabs, in order.
                if (buttonCount < 7) {
                    setXMethod.invoke(child, topX + buttonCount * 25);
                    setYMethod.invoke(child, topY);
                } else {
                    setXMethod.invoke(child, bottomX + (buttonCount - 7) * 25);
                    setYMethod.invoke(child, bottomY);
                }
                buttonCount++;
            }
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to position Skyblocker QuickNav buttons", e);
        }
    }

    /**
     * Hides Skyblocker's chest-value button (the "$" button) when the storage overlay is active.
     * The vanilla container screen is still present but not rendered, so Skyblocker widgets added
     * to it would otherwise show through the overlay.
     *
     * @return {@code true} if the button was found and hidden
     */
    public static boolean hideChestValueButton(Screen screen) {
        if (!SKYBLOCKER_PRESENT) {
            return false;
        }
        try {
            for (Object listener : screen.children()) {
                if (listener instanceof Button button && "$".equals(button.getMessage().getString())) {
                    button.visible = false;
                    button.active = false;
                    EnhancedStorage.LOGGER.info("[Enhanced Storage] Hidden Skyblocker ChestValue button");
                    return true;
                }
            }
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to hide Skyblocker ChestValue button", e);
        }
        return false;
    }
}
