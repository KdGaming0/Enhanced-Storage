package com.github.kdgaming0.enhancedstorage.config;

import eu.midnightdust.lib.config.MidnightConfigScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Spacing the overlay layout uses: the density preset's values, or the individual settings when the preset is Custom.
 * Page titles are the exception: the user's setting always applies, and picking a preset only sets it once.
 */
public final class LayoutSpacing {

    private record Preset(int horizontalMargin, int topMargin, int bottomMargin, int innerPadding, int cardSpacing,
                          EnhancedStorageConfig.PageTitleStyle titleStyle) {
    }

    private static final Preset COMFORTABLE_PRESET =
            new Preset(15, 15, 25, 6, 3, EnhancedStorageConfig.PageTitleStyle.NORMAL);
    private static final Preset COMPACT_PRESET =
            new Preset(8, 8, 14, 4, 1, EnhancedStorageConfig.PageTitleStyle.COMPACT);
    private static final Preset DENSE_PRESET =
            new Preset(4, 4, 6, 2, 0, EnhancedStorageConfig.PageTitleStyle.COMPACT);

    // Density last seen by the open config screen, to catch the moment the user picks a different preset.
    private static EnhancedStorageConfig.LayoutDensity lastDensity;

    private LayoutSpacing() {
    }

    /** Applies a newly picked preset's title style to the Page Titles setting while the config screen is open. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MidnightConfigScreen)) return;
            lastDensity = EnhancedStorageConfig.layoutDensity;
            ScreenEvents.afterTick(screen).register(s -> syncPageTitleStyle());
        });
    }

    private static void syncPageTitleStyle() {
        // The config screen writes its pending values to the fields every tick, so this sees a change right away.
        EnhancedStorageConfig.LayoutDensity density = EnhancedStorageConfig.layoutDensity;
        if (density == lastDensity) return;
        lastDensity = density;
        Preset preset = preset();
        if (preset != null) EnhancedStorageConfig.setPageTitleStyleFromPreset(preset.titleStyle());
    }

    private static @Nullable Preset preset() {
        return switch (EnhancedStorageConfig.layoutDensity) {
            case CUSTOM -> null;
            case COMFORTABLE -> COMFORTABLE_PRESET;
            case COMPACT -> COMPACT_PRESET;
            case DENSE -> DENSE_PRESET;
        };
    }

    public static int horizontalMargin() {
        Preset preset = preset();
        return preset != null ? preset.horizontalMargin() : EnhancedStorageConfig.horizontalMargin;
    }

    public static int topMargin() {
        Preset preset = preset();
        return preset != null ? preset.topMargin() : EnhancedStorageConfig.overviewTopMargin;
    }

    public static int bottomMargin() {
        Preset preset = preset();
        return preset != null ? preset.bottomMargin() : EnhancedStorageConfig.overviewBottomMargin;
    }

    public static int innerPadding() {
        Preset preset = preset();
        return preset != null ? preset.innerPadding() : EnhancedStorageConfig.innerPadding;
    }

    public static int cardSpacing() {
        Preset preset = preset();
        return preset != null ? preset.cardSpacing() : EnhancedStorageConfig.cardSpacing;
    }

    public static EnhancedStorageConfig.PageTitleStyle pageTitleStyle() {
        return EnhancedStorageConfig.pageTitleStyle;
    }
}