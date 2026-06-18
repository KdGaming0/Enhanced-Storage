package com.github.kdgaming0.enhancedstorage.config;

/**
 * Controls when the cursor position is preserved between inventory screens.
 *
 * <ul>
 *   <li>{@link #DISABLED} — never save or restore.</li>
 *   <li>{@link #STORAGE_OVERLAY_ONLY} — only when leaving a screen with an active Enhanced Storage
 *       overlay and arriving at another storage container (default).</li>
 *   <li>{@link #ALL_CONTAINERS} — between any two container screens.</li>
 * </ul>
 */
public enum CursorPositionMode {
    DISABLED,
    STORAGE_OVERLAY_ONLY,
    ALL_CONTAINERS
}
