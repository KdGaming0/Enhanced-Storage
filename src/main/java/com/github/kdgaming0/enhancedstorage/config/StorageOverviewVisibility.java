package com.github.kdgaming0.enhancedstorage.config;

/**
 * Controls when the storage overview (navigation) panel to the left of the inventory is shown.
 */
public enum StorageOverviewVisibility {
    /** Always render the panel — on both the overview screen and individual storage pages. */
    ALWAYS_SHOW,
    /** Hide the panel while viewing a storage page; still show it on the main overview screen. */
    HIDE_ON_PAGES,
    /** Never render the panel on any screen. */
    ALWAYS_HIDE
}
