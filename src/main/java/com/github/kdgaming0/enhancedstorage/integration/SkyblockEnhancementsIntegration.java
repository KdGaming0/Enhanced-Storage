package com.github.kdgaming0.enhancedstorage.integration;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/**
 * Optional soft-dependency detector for SkyBlock-Enhancements.
 *
 * <p>SkyBlock-Enhancements ships an identical cursor-position-save feature via its own
 * {@code MouseHandler} mixin. When that mod is loaded and its feature is enabled, Enhanced Storage
 * defers to it to avoid both mods restoring the cursor at once. The check is read-only reflection
 * into a {@code public static boolean} config field, evaluated each call so a mid-session toggle of
 * SkyBlock-Enhancements' config is respected without a restart.
 */
public final class SkyblockEnhancementsIntegration {
    private static final String MOD_ID = "skyblock_enhancements";
    private static final String CONFIG_CLASS =
            "com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig";
    private static final String FIELD_NAME = "saveCursorPosition";

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private static Field saveCursorPositionField;
    private static boolean initialized;
    private static boolean reflectionFailed;

    private SkyblockEnhancementsIntegration() {
    }

    private static void initialize() {
        if (initialized || reflectionFailed || !PRESENT) {
            return;
        }
        try {
            saveCursorPositionField = Class.forName(CONFIG_CLASS).getField(FIELD_NAME);
            initialized = true;
        } catch (Exception e) {
            reflectionFailed = true;
            EnhancedStorage.LOGGER.error("Failed to initialize SkyBlock-Enhancements integration", e);
        }
    }

    /**
     * Returns {@code true} only when SkyBlock-Enhancements is loaded and its own cursor-save feature
     * is currently enabled. Any reflection failure is treated as "not active".
     */
    public static boolean isSaveCursorPositionActive() {
        if (!PRESENT) {
            return false;
        }
        initialize();
        if (!initialized) {
            return false;
        }
        try {
            return saveCursorPositionField.getBoolean(null);
        } catch (Exception e) {
            reflectionFailed = true;
            EnhancedStorage.LOGGER.error("Failed to read SkyBlock-Enhancements cursor-save state", e);
            return false;
        }
    }

    /** Returns {@code true} when SkyBlock-Enhancements is loaded (regardless of its config state). */
    public static boolean isPresent() {
        return PRESENT;
    }
}
