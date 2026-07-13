package com.github.kdgaming0.enhancedstorage.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class EnhancedStorageConfig extends MidnightConfig {
    public static final String CATEGORY_GENERAL = "general";

    @Entry(category = CATEGORY_GENERAL)
    public static boolean enableOverlay = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean enableRiftOverlay = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean showStorageOverviewCard = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean showToolkitButton = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean showSettingsButton = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean showThemeButton = true;

    @Entry(category = CATEGORY_GENERAL)
    public static BackgroundType backgroundType = BackgroundType.TRANSPARENT;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 1, max = 5, precision = 1)
    public static int maxPagePerRow = 3;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 10, precision = 1)
    public static int cardSpacing = 3;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 50, precision = 1)
    public static int horizontalMargin = 15;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewTopMargin = 15;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewBottomMargin = 25;

    // Add extra margins to give space for skyblocker quick nav buttons, but defaults to 10 to not make the overlay too small
    // we are just using of the exciting padding added before, so now there is no space between the quick nav buttons and the top and bottom of the screen
    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraTopAndBottomMarginForQuickNav = 10;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraBottomMarginForRecipeSearchBar = 35;


    @Entry(category = CATEGORY_GENERAL)
    public static boolean showItemTooltipsOnCachedItems = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean rememberSearchOnReopen = false;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean rememberScrollOnReopen = false;

    @Entry(category = CATEGORY_GENERAL)
    public static AutoScrollMode autoScrollToOpenPage = AutoScrollMode.IF_PARTLY_HIDDEN;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 1, max = 60, precision = 1)
    public static int overlayScrollSpeed = 18;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean saveCursorPosition = true;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0.01, max = 5, precision = 1000)
    public static double saveCursorPositionWindow = 0.5;

    public enum BackgroundType {
        LIGHT, DARK, TRANSPARENT
    }

    public enum AutoScrollMode {
        OFF,
        IF_PARTLY_HIDDEN,
        IF_FULLY_HIDDEN
    }
}
