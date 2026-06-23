package com.github.kdgaming0.enhancedstorage.config;

import eu.midnightdust.lib.config.MidnightConfig;

/**
 * MidnightLib-based configuration for Enhanced Storage.
 * All fields must be {@code public static}.
 */
public class EnhancedStorageConfig extends MidnightConfig {

    @Comment(category = "storage", centered = true)
    public static Comment storageOverlayHeader;

    @Entry(category = "storage")
    public static boolean enableStorageOverlay = true;

    @Entry(category = "storage")
    public static boolean enableRiftStorageOverlay = true;

    @Entry(category = "storage", min = 1, max = 5, isSlider = true)
    public static int overlayColumns = 3;

    @Entry(category = "storage")
    public static boolean inverseScroll = false;

    @Entry(category = "storage", isSlider = true, min = 0.5, max = 5.0, precision = 1)
    public static double scrollSpeed = 1.0;

    @Entry(category = "storage")
    public static boolean showEmptyPages = true;

    @Entry(category = "storage")
    public static boolean showUnavailablePages = false;

    @Entry(category = "storage")
    public static boolean autoScrollToActivePage = true;

    @Entry(category = "storage")
    public static boolean showPreviewTooltips = true;

    @Entry(category = "storage", isColor = true)
    public static String searchHighlightColor = "#00ff33";

    @Entry(category = "storage")
    public static OverlayTheme overlayTheme = OverlayTheme.TRANSPARENT;

    @Entry(category = "storage")
    public static StorageOverviewVisibility storageOverviewVisibility = StorageOverviewVisibility.ALWAYS_SHOW;

    @Entry(category = "storage")
    public static boolean shiftLeftClickOpensHub = true;

    @Entry(category = "storage")
    public static CursorPositionMode saveCursorPositionMode = CursorPositionMode.STORAGE_OVERLAY_ONLY;
}