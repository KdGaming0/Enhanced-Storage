package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.config.LayoutSpacing;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Room the overlay leaves at the top and bottom of the screen for other mods' widgets: Skyblocker's Quick Nav tabs,
 * RRV's search bar and the SkyRecipes category buttons above it.
 *
 * <p>Automatic mode works the room out from where those mods place their widgets, so the margin is only as big as
 * the widgets that are actually shown. The widgets sit inside the user's margin rather than on top of it. Manual mode
 * adds the fixed extra margins from the config instead, and so does Automatic when a mod's config can't be read
 * (a changed field in a newer version), so the overlay never crashes over it.
 *
 * <p>Skyblocker and RRV are compile-only dependencies: every reference to their classes stays behind a loaded check.
 * SkyRecipes isn't a dependency at all, so its settings are read by reflection.
 *
 * <p>The SkyRecipes buttons can appear and disappear while the overlay is open (they can be set to show only while
 * searching), so the result can change without a rebuild; the screen polls {@link #bottomMargin} to catch that.
 */
public final class ModWidgetSpace {

    private static final boolean SKYBLOCKER_LOADED = FabricLoader.getInstance().isModLoaded("skyblocker");
    private static final boolean RRV_LOADED = FabricLoader.getInstance().isModLoaded("rrv");
    private static final boolean SKYRECIPES_LOADED = FabricLoader.getInstance().isModLoaded("skyrecipes");
    // Quick Nav tabs are 32px tall and reach 28px past the screen bounds we report: Skyblocker's QuickNavButton puts
    // the top row at topPos - 28 and the bottom row at topPos + imageHeight - 4.
    private static final int QUICK_NAV_TAB_REACH = 28;
    // Skyblocker uses vanilla's creative tab sprites, and an unselected bottom tab only draws its top 28 of 32 rows.
    private static final int QUICK_NAV_UNSELECTED_BOTTOM_TAB_REACH = 24;
    // RRV's search bar is 20px tall at screenHeight - 22.
    private static final int RRV_SEARCH_BAR_REACH = 22;
    // SkyRecipes' CategoryButtonMixin: 16px buttons 2px apart, in rows starting 2px above the search bar,
    // wrapping when they don't fit the bar's width.
    private static final int CATEGORY_BUTTON_SIZE = 16;
    private static final int CATEGORY_BUTTON_GAP = 2;
    private static final int FALLBACK_CATEGORY_COUNT = 9;
    // The old fixed margins already overlapped the bottom tabs with the bottom margin by this much.
    private static final int MANUAL_QUICK_NAV_BOTTOM_OVERLAP = 5;

    private static boolean warnedFallback;
    private static boolean skyRecipesResolved;
    private static Field hideCategoryButtons;
    private static Field hideCategoryButtonsWhenNotSearching;
    private static int categoryCount = FALLBACK_CATEGORY_COUNT;

    private ModWidgetSpace() {
    }

    /** The top margin, given the margin the layout density asks for. */
    public static int topMargin(int margin) {
        if (!quickNavEnabled()) return margin;
        if (automatic()) {
            try {
                return quickNavRowShown(true) ? Math.max(margin, QUICK_NAV_TAB_REACH + LayoutSpacing.modWidgetGap()) : margin;
            } catch (LinkageError e) {
                warnFallback(e);
            }
        }
        return margin + EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav;
    }

    /**
     * The bottom margin below everything the overlay draws, given the margin the layout density asks for and whether
     * one of the bottom Quick Nav tabs is the selected one (drawn full height).
     */
    public static int bottomMargin(int margin, boolean bottomTabSelected) {
        if (automatic()) {
            try {
                // Everything is centred on the overlay, so it stacks: the tabs hang off the overlay, the bar sits at
                // the screen edge, and the category buttons sit on top of the bar.
                int reserve = 0;
                if (quickNavEnabled() && quickNavRowShown(false)) {
                    reserve += bottomTabSelected ? QUICK_NAV_TAB_REACH : QUICK_NAV_UNSELECTED_BOTTOM_TAB_REACH;
                }
                if (RRV_LOADED && rrvSearchBarAtBottom()) reserve += RRV_SEARCH_BAR_REACH + categoryButtonsHeight();
                return reserve == 0 ? margin : Math.max(margin, reserve + LayoutSpacing.modWidgetGap());
            } catch (LinkageError e) {
                warnFallback(e);
            }
        }
        if (quickNavEnabled()) {
            margin += EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav - MANUAL_QUICK_NAV_BOTTOM_OVERLAP;
        }
        if (RRV_LOADED) margin += EnhancedStorageConfig.extraBottomMarginForRecipeSearchBar;
        return margin;
    }

    private static boolean automatic() {
        return EnhancedStorageConfig.modWidgetSpacing == EnhancedStorageConfig.ModWidgetSpacing.AUTOMATIC;
    }

    private static boolean quickNavEnabled() {
        if (!SKYBLOCKER_LOADED) return false;
        try {
            return de.hysky.skyblocker.config.SkyblockerConfigManager.get().quickNav.enableQuickNav;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /** Whether any tab of the top row (buttons 1-7) or bottom row (buttons 8-14) is shown; button N gets row position N - 1. */
    private static boolean quickNavRowShown(boolean top) {
        var quickNav = de.hysky.skyblocker.config.SkyblockerConfigManager.get().quickNav;
        if (top) {
            return quickNav.button1.render || quickNav.button2.render || quickNav.button3.render
                    || quickNav.button4.render || quickNav.button5.render || quickNav.button6.render
                    || quickNav.button7.render;
        }
        return quickNav.button8.render || quickNav.button9.render || quickNav.button10.render
                || quickNav.button11.render || quickNav.button12.render || quickNav.button13.render
                || quickNav.button14.render;
    }

    /**
     * Whether RRV's search bar sits at the bottom of the screen over the overlay. It's hidden when the item list is
     * off or in JEI-panel mode, and without Center Search it sits inside RRV's item list beside the overlay instead.
     */
    private static boolean rrvSearchBarAtBottom() {
        var settings = cc.cassian.rrv.common.config.Configs.CLIENT_SETTINGS;
        boolean visible = settings.isShowItemView() != cc.cassian.rrv.common.config.options.OverlayDisplay.DISABLED
                && !settings.isJeiPanel();
        return visible && settings.isCenterSearch();
    }

    /**
     * Height of the SkyRecipes category button rows above RRV's search bar, or 0 while they're hidden. Mirrors
     * SkyRecipes' own visibility rule; if its settings can't be read, the buttons are assumed to be shown so they
     * never cover the overlay.
     */
    private static int categoryButtonsHeight() {
        if (!SKYRECIPES_LOADED || !categoryButtonsShown()) return 0;

        // The bar is centred and min(imageWidth, screenWidth / 2) wide; our overlay is always wider than half the
        // screen or than the 9 buttons, so the screen half is the width that decides the wrapping.
        int barWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
        int perRow = Math.max(1, (barWidth + CATEGORY_BUTTON_GAP) / (CATEGORY_BUTTON_SIZE + CATEGORY_BUTTON_GAP));
        int rows = Math.ceilDiv(categoryCount, perRow);
        return rows * (CATEGORY_BUTTON_SIZE + CATEGORY_BUTTON_GAP);
    }

    private static boolean categoryButtonsShown() {
        resolveSkyRecipes();
        if (hideCategoryButtons == null || hideCategoryButtonsWhenNotSearching == null) return true;
        try {
            if (hideCategoryButtons.getBoolean(null)) return false;
            var mode = cc.cassian.rrv.common.config.Configs.CLIENT_SETTINGS.isShowItemView();
            if (mode == cc.cassian.rrv.common.config.options.OverlayDisplay.WHEN_SEARCHING) {
                return !hideCategoryButtonsWhenNotSearching.getBoolean(null)
                        || cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay.INSTANCE.isSearching();
            }
            return mode != cc.cassian.rrv.common.config.options.OverlayDisplay.DISABLED;
        } catch (IllegalAccessException e) {
            return true;
        }
    }

    private static void resolveSkyRecipes() {
        if (skyRecipesResolved) return;
        skyRecipesResolved = true;
        try {
            Class<?> config = Class.forName("com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig");
            hideCategoryButtons = config.getField("hideCategoryButtons");
            hideCategoryButtonsWhenNotSearching = config.getField("hideCategoryButtonsWhenNotSearching");
            Class<?> category = Class.forName("com.github.kdgaming0.skyrecipes.core.model.SkyblockItemCategory");
            categoryCount = ((List<?>) category.getField("BUTTON_CATEGORIES").get(null)).size();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            EnhancedStorage.LOGGER.warn("Could not read SkyRecipes settings; leaving room for its category buttons.", e);
        }
    }

    private static void warnFallback(LinkageError e) {
        if (warnedFallback) return;
        warnedFallback = true;
        EnhancedStorage.LOGGER.warn("Could not read Skyblocker/RRV settings for automatic mod spacing; using the manual margins instead.", e);
    }
}
