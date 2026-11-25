package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.operationpotato.itemlist.SkyBlockItemList;
import com.operationpotato.itemlist.config.ConfigManager;
import com.operationpotato.itemlist.config.Settings;
import com.operationpotato.itemlist.favorites.Favorites;
import com.operationpotato.itemlist.favorites.FavoritesManager;
import com.operationpotato.itemlist.gui.AbstractItemPanel;
import com.operationpotato.itemlist.gui.favorites.FavoritesPanel;
import com.operationpotato.itemlist.utils.ItemListSide;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls into SkyBlock Item List. Every public method is safe to call without it installed: it does nothing then.
 *
 * <p>The list sits in the gap between the screen edge and our overlay, and our overlay fills nearly the whole width,
 * so on its own the list gets no room. With Make Room for SkyBlock Item List on, the overlay keeps a column of the
 * picked width free on the list's side, and the list fills that whole column (see the mixins in
 * {@code mixin.compat.skyblockitemlist}). Its favorites panel gets a column of its own width on the other side, but
 * only while there are favorites to show, and isn't shown on our screen otherwise.
 *
 * <p>The list adds its panels in Fabric's after-init event, which our relayouts don't fire, so the screen hands them
 * back with {@link #panelsOn}/{@link #restorePanels} around a rebuild. Its settings are internal classes rather than
 * API, so any linkage error turns the integration off quietly instead of crashing.
 */
public final class SkyblockItemListCompat {

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("skyblock-item-list");
    // SkyBlock Item List hides a panel narrower than 80px; on our screen it may be as narrow as this.
    private static final int MIN_PANEL_WIDTH = 40;

    private static boolean failed;

    private SkyblockItemListCompat() {
    }

    private static boolean isLoaded() {
        return LOADED && !failed;
    }

    private static boolean onStorageScreen() {
        return Minecraft.getInstance().screen instanceof StorageContainerScreen;
    }

    /**
     * Whether the panels take the whole column we keep free for them, rather than their own share of the gap.
     */
    public static boolean fillsReservedColumn() {
        return EnhancedStorageConfig.roomForItemList && onStorageScreen();
    }

    /**
     * The width under which SkyBlock Item List hides its item list, given its own limit of 80px: the gap beside our
     * overlay is usually narrower than that.
     */
    public static int listMinWidth(int original) {
        return onStorageScreen() ? Math.min(original, MIN_PANEL_WIDTH) : original;
    }

    /**
     * The width under which SkyBlock Item List hides its favorites panel, given its own limit: on our screen the panel
     * shows only while we keep a column for it.
     */
    public static int favoritesMinWidth(int original) {
        if (!onStorageScreen()) return original;
        return showsFavorites() ? Math.min(original, MIN_PANEL_WIDTH) : Integer.MAX_VALUE;
    }

    /**
     * Screen width to keep free at the left and right edge for the list and its favorites: {@code [left, right]}.
     */
    public static int[] reservedSides() {
        if (!isLoaded() || !EnhancedStorageConfig.roomForItemList) return new int[]{0, 0};
        try {
            Settings.GeneralSettings general = ConfigManager.INSTANCE.get().getGeneral();
            if (!general.getEnabled()) return new int[]{0, 0};
            int list = EnhancedStorageConfig.itemListWidth;
            int favorites = showsFavorites() ? EnhancedStorageConfig.itemListFavoritesWidth : 0;
            return general.getListSide() == ItemListSide.RIGHT ? new int[]{favorites, list} : new int[]{list, favorites};
        } catch (LinkageError e) {
            fail(e);
            return new int[]{0, 0};
        }
    }

    /**
     * Whether the favorites panel gets a column on our screen: both our options and the list's favorites are on, and
     * there is at least one favorite.
     */
    private static boolean showsFavorites() {
        if (!isLoaded() || !EnhancedStorageConfig.roomForItemList || !EnhancedStorageConfig.roomForItemListFavorites) {
            return false;
        }
        try {
            Settings settings = ConfigManager.INSTANCE.get();
            if (!settings.getGeneral().getEnabled() || !settings.getFavoritesList().getEnableFavorites()) return false;
            Favorites favorites = FavoritesManager.INSTANCE.getFavorites();
            return !favorites.getFavoriteItems().isEmpty() || !favorites.getFavoriteRecipes().isEmpty()
                    || !favorites.getFavoriteMobs().isEmpty() || favorites.getPinnedRecipe().isPresent();
        } catch (LinkageError e) {
            fail(e);
            return false;
        }
    }

    /**
     * The list's panels that are on the screen now, to put back after a rebuild drops them.
     */
    public static List<GuiEventListener> panelsOn(Screen screen) {
        if (!isLoaded()) return List.of();
        try {
            List<GuiEventListener> panels = new ArrayList<>(2);
            List<? extends GuiEventListener> children = screen.children();
            AbstractItemPanel list = SkyBlockItemList.INSTANCE.getInstance();
            AbstractItemPanel favorites = SkyBlockItemList.INSTANCE.getFavoriteInstance();
            if (list != null && children.contains(list)) panels.add(list);
            if (favorites != null && children.contains(favorites)) panels.add(favorites);
            return panels;
        } catch (LinkageError e) {
            fail(e);
            return List.of();
        }
    }

    /**
     * Puts panels taken with {@link #panelsOn} back on the screen and sizes them to the new layout.
     */
    public static void restorePanels(Screen screen, List<GuiEventListener> panels) {
        if (panels.isEmpty() || !isLoaded()) return;
        try {
            AbstractItemPanel list = SkyBlockItemList.INSTANCE.getInstance();
            for (GuiEventListener panel : panels) {
                if (screen.children().contains(panel)) continue;
                AbstractItemPanel itemPanel = (AbstractItemPanel) panel;
                Screens.getWidgets(screen).add(itemPanel);
                itemPanel.updateWidth();
                // The list sets this only when the screen opens, so a column made for the first favorite since (or
                // dropped with the last one) would keep its old visibility. Shown only beside a shown list, which also
                // keeps it hidden where the list hid both.
                if (itemPanel instanceof FavoritesPanel) {
                    itemPanel.visible = showsFavorites() && list != null && list.visible
                            && itemPanel.getWidth() >= MIN_PANEL_WIDTH;
                }
            }
        } catch (LinkageError e) {
            fail(e);
        }
    }

    private static void fail(LinkageError e) {
        if (failed) return;
        failed = true;
        EnhancedStorage.LOGGER.warn("SkyBlock Item List has changed in a way Enhanced Storage doesn't recognise; "
                + "its integration with the storage overlay is turned off.", e);
    }
}
