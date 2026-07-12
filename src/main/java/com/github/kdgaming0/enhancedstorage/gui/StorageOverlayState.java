package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import org.jspecify.annotations.Nullable;

public class StorageOverlayState {

    private static final StorageOverlayState SESSION = new StorageOverlayState();

    public static StorageOverlayState session() {
        return SESSION;
    }

    private @Nullable StorageKey openKey;
    private @Nullable StorageKey renamingKey;
    private String searchQuery = "";
    private double scrollAmount = 0;

    // Used to tell page switch from closing the menu and reopening it
    private long navigatingUntil = 0;

    public double getScrollAmount() { return scrollAmount; }
    public void setScrollAmount(double amount) { this.scrollAmount = amount; }

    public void beginNavigation() {
        navigatingUntil = System.currentTimeMillis() + 2000;
    }

    private boolean isNavigating() {
        return System.currentTimeMillis() < navigatingUntil;
    }

    public void onStorageScreenOpened() {
        navigatingUntil = 0;
    }

    public void onStorageScreenClosed() {
        renamingKey = null;
        if (isNavigating()) {
            return;
        }
        openKey = null;
        if (!EnhancedStorageConfig.rememberSearchOnReopen) {
            searchQuery = "";
        }
        if (!EnhancedStorageConfig.rememberScrollOnReopen) {
            scrollAmount = 0;
        }
    }

    public String getSearchQuery() { return searchQuery == null ? "" : searchQuery; }

    public @Nullable StorageKey getOpenKey() {return openKey; }

    public void setOpenKey(@Nullable StorageKey openKey) { this.openKey = openKey; }

    public boolean isOpen(StorageKey key) { return key.equals(openKey); }

    public void setSearchQuery(String query) { this.searchQuery = query == null ? "" : query; }

    public boolean isSearching() { return searchQuery != null && !searchQuery.isBlank(); }

    public @Nullable StorageKey getRenamingKey() { return renamingKey; }

    public void setRenamingKey(@Nullable StorageKey key) { this.renamingKey = key; }

    public boolean isRenaming() { return renamingKey != null; }
}
