package com.github.kdgaming0.enhancedstorage.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class EnhancedStorageConfig extends MidnightConfig {
    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_INTERFACE = "interface";
    public static final String CATEGORY_LAYOUT = "layout";
    public static final String CATEGORY_SEARCH = "search";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_MISC = "misc";

    // --- General ---

    @Entry(category = CATEGORY_GENERAL)
    public static boolean enableOverlay = true;

    @Entry(category = CATEGORY_GENERAL)
    public static boolean enableRiftOverlay = true;

    @Entry(category = CATEGORY_GENERAL)
    public static BackgroundType backgroundType = BackgroundType.TRANSPARENT;

    @Entry(category = CATEGORY_GENERAL, isSlider = true, min = 0, max = 10, precision = 1)
    public static int overlayGuiScale = 0;

    // --- Interface ---

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showStorageOverviewCard = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showToolkitButton = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showSettingsButton = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showThemeButton = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showEditButton = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static NavigationPanelSide navigationPanel = NavigationPanelSide.DISABLED;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showEmptySlotCount = true;

    @Comment(category = CATEGORY_INTERFACE)
    public static Comment spacerEditMode;

    @Comment(category = CATEGORY_INTERFACE, centered = true)
    public static Comment sectionEditMode;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean collapseSectionsWhileDragging = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean hideItemTooltipsInEditMode = true;

    // --- Layout ---

    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionPages;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 1, max = 10, precision = 1)
    public static int maxPagePerRow = 3;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 2, max = 18, precision = 1)
    public static int pageWidth = 9;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int cardSpacing = 3;

    @Comment(category = CATEGORY_LAYOUT)
    public static Comment spacerMargins;

    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionMargins;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int horizontalMargin = 15;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewTopMargin = 15;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewBottomMargin = 25;

    @Comment(category = CATEGORY_LAYOUT)
    public static Comment spacerModCompat;

    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionModCompat;

    // Add extra margins to give space for skyblocker quick nav buttons, but defaults to 10 to not make the overlay too small
    // we are just using of the exciting padding added before, so now there is no space between the quick nav buttons and the top and bottom of the screen
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraTopAndBottomMarginForQuickNav = 10;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraBottomMarginForRecipeSearchBar = 35;

    // --- Search ---

    @Entry(category = CATEGORY_SEARCH)
    public static SearchMode searchMode = SearchMode.FILTER;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean searchPageAndSectionNames = false;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean rememberSearchOnReopen = false;

    @Comment(category = CATEGORY_SEARCH)
    public static Comment spacerCopies;

    @Comment(category = CATEGORY_SEARCH, centered = true)
    public static Comment sectionCopies;

    @Entry(category = CATEGORY_SEARCH)
    public static HighlightCopiesMode highlightCopies = HighlightCopiesMode.HOLD_KEY;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean hidePagesWithoutCopies = false;

    // --- Scrolling & Navigation ---

    @Entry(category = CATEGORY_NAVIGATION)
    public static AutoScrollMode autoScrollToOpenPage = AutoScrollMode.IF_PARTLY_HIDDEN;

    @Entry(category = CATEGORY_NAVIGATION, isSlider = true, min = 1, max = 60, precision = 1)
    public static int overlayScrollSpeed = 18;

    @Entry(category = CATEGORY_NAVIGATION)
    public static boolean rememberScrollOnReopen = false;

    @Entry(category = CATEGORY_NAVIGATION)
    public static boolean saveCursorPosition = true;

    @Entry(category = CATEGORY_NAVIGATION, isSlider = true, min = 0.01, max = 5, precision = 1000)
    public static double saveCursorPositionWindow = 0.5;

    // --- Misc ---

    @Comment(category = CATEGORY_MISC, centered = true)
    public static Comment sectionPerformance;

    @Entry(category = CATEGORY_MISC)
    public static boolean showItemTooltipsOnCachedItems = true;

    @Entry(category = CATEGORY_MISC)
    public static boolean fastCachedItemDecorations = true;

    public enum BackgroundType {
        LIGHT, DARK, TRANSPARENT
    }

    public enum AutoScrollMode {
        OFF,
        IF_PARTLY_HIDDEN,
        IF_FULLY_HIDDEN
    }

    public enum NavigationPanelSide {
        LEFT, RIGHT, DISABLED
    }

    public enum SearchMode {
        FILTER, DIM
    }

    public enum HighlightCopiesMode {
        OFF, HOLD_KEY, ALWAYS
    }
}
