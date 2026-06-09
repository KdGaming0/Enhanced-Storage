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

    @Entry(category = "storage", min = 1, max = 6)
    public static int overlayColumns = 3;

    @Entry(category = "storage")
    public static boolean inverseScroll = false;

    @Entry(category = "storage", isSlider = true, min = 0.5, max = 5.0, precision = 1)
    public static double scrollSpeed = 2.0;

    /**
     * Show/hide pages that have not been visited yet.
     */
    @Entry(category = "storage")
    public static boolean showEmptyPages = true;

    /**
     * Auto-scroll the page list so the currently open page is visible on screen init.
     */
    @Entry(category = "storage")
    public static boolean autoScrollToActivePage = true;

    /**
     * Show item tooltips when hovering over items in non-active page previews.
     */
    @Entry(category = "storage")
    public static boolean showPreviewTooltips = true;

    /**
     * Colour applied to matching items during search (format: #RRGGBBAA or #RRGGBB).
     */
    @Entry(category = "storage", isColor = true)
    public static String searchHighlightColor = "#55FF5540";
}