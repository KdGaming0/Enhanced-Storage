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

    @Entry(category = "storage", min = 1, max = 5, isSlider = true)
    public static int overlayColumns = 3;

    @Entry(category = "storage")
    public static boolean inverseScroll = false;

    @Entry(category = "storage", isSlider = true, min = 0.5, max = 5.0, precision = 1)
    public static double scrollSpeed = 1.0;

    /**
     * Show/hide pages that have not been visited yet.
     */
    @Entry(category = "storage")
    public static boolean showEmptyPages = true;

    /**
     * Show pages that cannot be used: locked ender chest pages and locked or empty backpack slots.
     */
    @Entry(category = "storage")
    public static boolean showUnavailablePages = false;

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
    public static String searchHighlightColor = "#00ff33";

    /**
     * Visual theme for the storage overlay sprites.
     */
    @Entry(category = "storage")
    public static OverlayTheme overlayTheme = OverlayTheme.TRANSPARENT;

    /**
     * When to display the storage overview (navigation) panel left of the inventory.
     */
    @Entry(category = "storage")
    public static StorageOverviewVisibility storageOverviewVisibility = StorageOverviewVisibility.ALWAYS_SHOW;
}