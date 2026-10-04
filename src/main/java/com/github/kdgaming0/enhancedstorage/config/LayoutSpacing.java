package com.github.kdgaming0.enhancedstorage.config;

import eu.midnightdust.lib.config.MidnightConfigScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Spacing the overlay layout uses: the density preset's values, or the individual settings when the preset is Custom.
 * Page titles and slot size are the exception: the user's setting always applies, and picking a preset only sets it
 * once (picking Custom restores the choice last made under Custom).
 */
public final class LayoutSpacing {

    private record Preset(int horizontalMargin, int topMargin, int bottomMargin, int innerPadding, int cardSpacing,
                          int scrollbarGap, int modWidgetGap, int panelGap, int columnGap,
                          EnhancedStorageConfig.PageTitleStyle titleStyle, EnhancedStorageConfig.SlotSize slotSize) {
    }


    private static final Preset COMFORTABLE_PRESET = new Preset(15, 15, 25, 6, 3,
            4, 2, 2, 2, EnhancedStorageConfig.PageTitleStyle.NORMAL, EnhancedStorageConfig.SlotSize.NORMAL);
    private static final Preset COMPACT_PRESET = new Preset(8, 8, 14, 4, 1,
            1, 1, 1, 1, EnhancedStorageConfig.PageTitleStyle.COMPACT, EnhancedStorageConfig.SlotSize.NORMAL);
    private static final Preset DENSE_PRESET = new Preset(4, 4, 6, 3, 0,
            0, 0, 0, 0, EnhancedStorageConfig.PageTitleStyle.COMPACT, EnhancedStorageConfig.SlotSize.COMPACT);

    // Density last seen by the open config screen, to catch the moment the user picks a different preset.
    private static EnhancedStorageConfig.LayoutDensity lastDensity;

    private LayoutSpacing() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MidnightConfigScreen)) return;
            lastDensity = EnhancedStorageConfig.layoutDensity;
            syncPanelsBesidePages();
            ScreenEvents.afterTick(screen).register(s -> {
                syncPresetChoices();
                syncPanelsBesidePages();
            });
        });
    }

    private static void syncPresetChoices() {
        // The config screen writes its pending values to the fields every tick, so this sees a change right away.
        EnhancedStorageConfig.LayoutDensity density = EnhancedStorageConfig.layoutDensity;
        if (density == lastDensity) return;
        EnhancedStorageConfig.LayoutDensity previous = lastDensity;
        lastDensity = density;
        // Custom keeps its own title style and slot size: remember them when leaving Custom and put them back when returning.
        if (previous == EnhancedStorageConfig.LayoutDensity.CUSTOM) {
            EnhancedStorageConfig.customPageTitleStyle = EnhancedStorageConfig.pageTitleStyle;
            EnhancedStorageConfig.customSlotSize = EnhancedStorageConfig.slotSize;
        }
        Preset preset = preset();
        EnhancedStorageConfig.setPageTitleStyleFromPreset(
                preset != null ? preset.titleStyle() : EnhancedStorageConfig.customPageTitleStyle);
        EnhancedStorageConfig.setSlotSizeFromPreset(
                preset != null ? preset.slotSize() : EnhancedStorageConfig.customSlotSize);
    }

    // Shows Side Panel Gap (Horizontal) only while something sits beside the pages for it to space out.
    private static void syncPanelsBesidePages() {
        EnhancedStorageConfig.setPanelsBesidePages(
                EnhancedStorageConfig.inventoryPosition != EnhancedStorageConfig.InventoryPosition.BOTTOM
                        || EnhancedStorageConfig.navigationPanel != EnhancedStorageConfig.NavigationPanelSide.DISABLED);
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

    public static int scrollbarGap() {
        Preset preset = preset();
        return preset != null ? preset.scrollbarGap() : EnhancedStorageConfig.scrollbarGap;
    }

    public static int modWidgetGap() {
        Preset preset = preset();
        return preset != null ? preset.modWidgetGap() : EnhancedStorageConfig.modWidgetGap;
    }

    public static int panelGap() {
        Preset preset = preset();
        return preset != null ? preset.panelGap() : EnhancedStorageConfig.panelGap;
    }

    public static int columnGap() {
        Preset preset = preset();
        return preset != null ? preset.columnGap() : EnhancedStorageConfig.columnGap;
    }

    public static EnhancedStorageConfig.PageTitleStyle pageTitleStyle() {
        return EnhancedStorageConfig.pageTitleStyle;
    }

    public static int slotSize() {
        return EnhancedStorageConfig.slotSize == EnhancedStorageConfig.SlotSize.COMPACT ? 16 : 18;
    }
}
