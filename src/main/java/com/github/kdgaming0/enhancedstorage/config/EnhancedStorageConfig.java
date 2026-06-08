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

    @Entry(category = "storage", isColor = true)
    public static String activePageOutlineColor = "#7AB4FF";

    @Entry(category = "storage", isColor = true)
    public static String inactivePageBorderColor = "#1A3060";

    @Entry(category = "storage", isColor = true)
    public static String searchHighlightColor = "#48FF48";
}
