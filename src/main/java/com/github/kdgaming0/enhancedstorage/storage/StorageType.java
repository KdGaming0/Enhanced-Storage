package com.github.kdgaming0.enhancedstorage.storage;

/**
 * Distinguishes the two independent storage systems this mod overlays.
 *
 * <p>{@link #MAIN} is the regular SkyBlock storage hub (Ender Chest pages + Backpacks),
 * navigated by chat commands. {@link #RIFT} is the separate two-page Rift Storage menu,
 * which has no command and is navigated by clicking the container's own page buttons.
 *
 * <p>The type is part of a page's identity so the two systems never share a cache map or
 * leak each other's pages into the wrong overlay.
 */
public enum StorageType {
    MAIN,
    RIFT
}
