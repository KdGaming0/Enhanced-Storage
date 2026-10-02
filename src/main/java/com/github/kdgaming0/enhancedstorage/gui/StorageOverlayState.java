package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import org.jspecify.annotations.Nullable;

public class StorageOverlayState {

    private static final StorageOverlayState SESSION = new StorageOverlayState();
    private @Nullable StorageKey openKey;
    private String searchQuery = "";
    private double scrollAmount = 0;
    private double navigationScroll = 0;
    private @Nullable Double scrollBeforeCopyFilter;
    // Used to tell page switch from closing the menu and reopening it
    private long navigatingUntil = 0;

    public static StorageOverlayState session() {
        return SESSION;
    }

    public double getScrollAmount() {
        return scrollAmount;
    }

    public void setScrollAmount(double amount) {
        this.scrollAmount = amount;
    }

    public double getNavigationScroll() {
        return navigationScroll;
    }

    public void setNavigationScroll(double amount) {
        this.navigationScroll = amount;
    }

    public @Nullable Double getScrollBeforeCopyFilter() {
        return scrollBeforeCopyFilter;
    }

    public void setScrollBeforeCopyFilter(@Nullable Double scroll) {
        this.scrollBeforeCopyFilter = scroll;
    }

    public void beginNavigation() {
        navigatingUntil = System.currentTimeMillis() + 2000;
    }

    public boolean isNavigating() {
        return System.currentTimeMillis() < navigatingUntil;
    }

    public void onStorageScreenOpened() {
        navigatingUntil = 0;
    }

    public void onStorageScreenClosed() {
        if (isNavigating()) {
            return;
        }
        openKey = null;
        scrollBeforeCopyFilter = null;
        if (!EnhancedStorageConfig.rememberSearchOnReopen) {
            searchQuery = "";
        }
        if (!EnhancedStorageConfig.rememberScrollOnReopen) {
            scrollAmount = 0;
            navigationScroll = 0;
        }
    }

    public String getSearchQuery() {
        return searchQuery == null ? "" : searchQuery;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query;
    }

    public @Nullable StorageKey getOpenKey() {
        return openKey;
    }

    public void setOpenKey(@Nullable StorageKey openKey) {
        this.openKey = openKey;
    }

    public boolean isOpen(StorageKey key) {
        return key.equals(openKey);
    }

    public boolean isSearching() {
        return searchQuery != null && !searchQuery.isBlank();
    }
}
