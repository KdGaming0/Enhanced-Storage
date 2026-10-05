package com.github.kdgaming0.enhancedstorage.config;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import eu.midnightdust.lib.config.EntryInfo;
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
    public static InventoryPosition inventoryPosition = InventoryPosition.BOTTOM;

    @Entry(category = CATEGORY_INTERFACE)
    public static OverlayCentering overlayCentering = OverlayCentering.WHOLE_WHEN_SIDE;

    @Entry(category = CATEGORY_INTERFACE)
    public static PageTitleStyle pageTitleStyle = PageTitleStyle.NORMAL;

    // The Page Titles choice made while Layout Density was Custom; a preset overwrites pageTitleStyle, so switching
    // back to Custom restores this (see LayoutSpacing).
    @Hidden
    @Entry(category = CATEGORY_INTERFACE)
    public static PageTitleStyle customPageTitleStyle = PageTitleStyle.NORMAL;

    @Entry(category = CATEGORY_INTERFACE)
    public static SlotSize slotSize = SlotSize.NORMAL;

    // The Slot Size choice made while Layout Density was Custom, kept the same way as customPageTitleStyle.
    @Hidden
    @Entry(category = CATEGORY_INTERFACE)
    public static SlotSize customSlotSize = SlotSize.NORMAL;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean showEmptySlotCount = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean hideEnchantGlintOnCachedItems = false;

    @Comment(category = CATEGORY_INTERFACE)
    public static Comment spacerEditMode;

    @Comment(category = CATEGORY_INTERFACE, centered = true)
    public static Comment sectionEditMode;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean collapseSectionsWhileDragging = true;

    @Entry(category = CATEGORY_INTERFACE)
    public static boolean hideItemTooltipsInEditMode = true;

    // --- Layout ---

    @Entry(category = CATEGORY_LAYOUT)
    public static LayoutDensity layoutDensity = LayoutDensity.CUSTOM;

    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionPages;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int maxPagePerRow = 3;

    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 2, max = 18, precision = 1)
    public static int pageWidth = 9;

    // Custom spacing: only shown while Layout Density is Custom; the presets supply these values otherwise (see LayoutSpacing).

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Comment(category = CATEGORY_LAYOUT)
    public static Comment spacerCustomSpacing;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionCustomSpacing;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int cardSpacing = 3;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int horizontalMargin = 15;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewTopMargin = 15;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int overviewBottomMargin = 25;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 2, max = 12, precision = 1)
    public static int innerPadding = 6;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int scrollbarGap = 4;

    // Not a setting: LayoutSpacing keeps it equal to "the inventory or the navigation panel sits beside the pages", so
    // Side Panel Gap (Horizontal) can be shown for either. MidnightLib conditions must all match and only see listed
    // entries, so this is a real entry whose own condition can never match, which keeps it off the screen.
    @Condition(requiredOption = "layoutDensity", requiredValue = "NEVER")
    @Entry(category = CATEGORY_LAYOUT)
    public static boolean panelsBesidePages = false;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Condition(requiredOption = "panelsBesidePages", requiredValue = "true")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int panelGap = 2;

    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Condition(requiredOption = "inventoryPosition", requiredValue = {"LEFT", "RIGHT"})
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int columnGap = 2;

    // Only used while Room for Other Mods is Automatic.
    @Condition(requiredOption = "layoutDensity", requiredValue = "CUSTOM")
    @Condition(requiredOption = "modWidgetSpacing", requiredValue = "AUTOMATIC")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 10, precision = 1)
    public static int modWidgetGap = 2;

    @Comment(category = CATEGORY_LAYOUT)
    public static Comment spacerModCompat;

    @Comment(category = CATEGORY_LAYOUT, centered = true)
    public static Comment sectionModCompat;

    @Entry(category = CATEGORY_LAYOUT)
    public static ModWidgetSpacing modWidgetSpacing = ModWidgetSpacing.AUTOMATIC;

    // Add extra margins to give space for skyblocker quick nav buttons, but defaults to 10 to not make the overlay too small
    // we are just using of the exciting padding added before, so now there is no space between the quick nav buttons and the top and bottom of the screen
    @Condition(requiredOption = "modWidgetSpacing", requiredValue = "MANUAL")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraTopAndBottomMarginForQuickNav = 10;

    @Condition(requiredOption = "modWidgetSpacing", requiredValue = "MANUAL")
    @Entry(category = CATEGORY_LAYOUT, isSlider = true, min = 0, max = 50, precision = 1)
    public static int extraBottomMarginForRecipeSearchBar = 35;

    // --- Search ---

    @Entry(category = CATEGORY_SEARCH)
    public static SearchMode searchMode = SearchMode.FILTER;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean searchPageAndSectionNames = false;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean rememberSearchOnReopen = false;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean searchBarAutofocus = false;

    @Entry(category = CATEGORY_SEARCH, isColor = true, width = 7, min = 7)
    public static String searchHighlightColor = "#33cc33";

    @Entry(category = CATEGORY_SEARCH, isSlider = true, min = 0, max = 100, precision = 1)
    public static int searchHighlightOpacity = 50;

    @Comment(category = CATEGORY_SEARCH)
    public static Comment spacerCopies;

    @Comment(category = CATEGORY_SEARCH, centered = true)
    public static Comment sectionCopies;

    @Entry(category = CATEGORY_SEARCH)
    public static HighlightCopiesMode highlightCopies = HighlightCopiesMode.HOLD_KEY;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean hidePagesWithoutCopies = false;

    @Entry(category = CATEGORY_SEARCH)
    public static boolean showItemTotals = true;

    @Entry(category = CATEGORY_SEARCH, isColor = true, width = 7, min = 7)
    public static String copyHighlightColor = "#33ccff";

    @Entry(category = CATEGORY_SEARCH, isSlider = true, min = 0, max = 100, precision = 1)
    public static int copyHighlightOpacity = 50;

    // --- Scrolling & Navigation ---

    @Entry(category = CATEGORY_NAVIGATION)
    public static AutoScrollMode autoScrollToOpenPage = AutoScrollMode.IF_PARTLY_HIDDEN;

    @Entry(category = CATEGORY_NAVIGATION, isSlider = true, min = 1, max = 60, precision = 1)
    public static int overlayScrollSpeed = 18;

    @Entry(category = CATEGORY_NAVIGATION)
    public static boolean invertScrollDirection = false;

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

    @Entry(category = CATEGORY_MISC)
    public static AnimatedItemRefreshRate animatedItemRefreshRate = AnimatedItemRefreshRate.FPS_30;

    @Entry(category = CATEGORY_MISC)
    public static boolean cachePageRendering = true;

    /**
     * Sets Page Titles from code while the config screen may be open: updates the screen's pending value too (the
     * screen writes it back to the field every tick) and rebuilds the list so the button shows the new style.
     */
    static void setPageTitleStyleFromPreset(PageTitleStyle style) {
        pageTitleStyle = style;
        EntryInfo info = entries.get(EnhancedStorage.MOD_ID + ":pageTitleStyle");
        if (info != null) info.setValue(style);
        if (configInstances.get(EnhancedStorage.MOD_ID) instanceof EnhancedStorageConfig config) {
            config.reloadScreen = true;
        }
    }

    /** Sets Slot Size from code while the config screen may be open, like {@link #setPageTitleStyleFromPreset}. */
    static void setSlotSizeFromPreset(SlotSize size) {
        slotSize = size;
        EntryInfo info = entries.get(EnhancedStorage.MOD_ID + ":slotSize");
        if (info != null) info.setValue(size);
        if (configInstances.get(EnhancedStorage.MOD_ID) instanceof EnhancedStorageConfig config) {
            config.reloadScreen = true;
        }
    }

    /** Sets the hidden panelsBesidePages helper while the config screen may be open, so conditions see it right away. */
    static void setPanelsBesidePages(boolean beside) {
        if (panelsBesidePages == beside) return;
        panelsBesidePages = beside;
        EntryInfo info = entries.get(EnhancedStorage.MOD_ID + ":panelsBesidePages");
        if (info != null) info.setValue(beside);
    }

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

    public enum InventoryPosition {
        BOTTOM, LEFT, RIGHT
    }

    public enum OverlayCentering {
        WHOLE_OVERLAY, STORAGE_ONLY, WHOLE_WHEN_SIDE
    }

    public enum PageTitleStyle {
        NORMAL, COMPACT, HIDDEN
    }

    public enum SlotSize {
        NORMAL, COMPACT
    }

    public enum LayoutDensity {
        CUSTOM, COMFORTABLE, COMPACT, DENSE
    }

    public enum ModWidgetSpacing {
        AUTOMATIC, MANUAL
    }

    public enum SearchMode {
        FILTER, DIM
    }

    public enum HighlightCopiesMode {
        OFF, HOLD_KEY, ALWAYS
    }

    public enum AnimatedItemRefreshRate {
        SMOOTH(0), FPS_30(1000 / 30), FPS_20(1000 / 20);

        public final int intervalMillis;

        AnimatedItemRefreshRate(int intervalMillis) {
            this.intervalMillis = intervalMillis;
        }
    }
}
