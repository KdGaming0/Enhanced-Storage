/*
 * Based on code from Firmament:
 * https://github.com/FirmamentMC/Firmament
 *
 * This file contains substantial portions adapted from Firmament's
 * storage overlay implementation.
 *
 * Original code licensed under the GNU General Public License v3.0.
 *
 * Modifications:
 * - Translated from Kotlin to Java
 * - Modified for Enhanced Storage
 */

package com.github.kdgaming0.enhancedstorage.feature.savecursorposition;

import com.github.kdgaming0.enhancedstorage.config.CursorPositionMode;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.integration.SkyblockEnhancementsIntegration;
import com.github.kdgaming0.enhancedstorage.storage.StorageTitleParser;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

/**
 * Preserves the cursor position between inventory screens, modeled on SkyBlock-Enhancements'
 * {@code SaveCursorPosition}.
 *
 * <p>When the player leaves a qualifying screen, vanilla's {@code MouseHandler.grabMouse()} recenters
 * the cursor; we remember the pre-center position. When the player opens another qualifying screen,
 * {@code MouseHandler.releaseMouse()} shows the cursor at the centre; we move it back to the
 * remembered position. A 500 ms freshness window and a screen-centre match guard against applying a
 * stale position to an unrelated screen.
 *
 * <p>State is touched only from the client main thread (grab/release run there), so plain static
 * fields are sufficient.
 */
public final class CursorPositionManager {

    /** A saved position is only restored if reused within this window. */
    private static final long MAX_AGE_MS = 500L;

    private CursorPositionManager() {
    }

    private static CursorPosition pendingOriginal;
    private static SavedCursorPosition saved;

    /**
     * Captures the cursor position before vanilla centres it in {@code grabMouse()}. Stored only
     * when the configured mode allows saving the screen being left.
     *
     * <p>The screen being left cannot be read here: {@code grabMouse()} runs after
     * {@code Minecraft.setScreen(null)} has nulled {@code Minecraft.screen} and called the old
     * screen's {@code removed()}. {@code STORAGE_OVERLAY_ONLY} therefore relies on the persistent
     * overlay singleton; {@code ALL_CONTAINERS} saves unconditionally and lets the restore side
     * filter, mirroring SkyBlock-Enhancements.
     */
    public static void saveCursorOriginal(double cursorX, double cursorY) {
        if (isInactive() || !qualifiesForSave()) {
            return;
        }
        pendingOriginal = new CursorPosition(cursorX, cursorY);
    }

    /**
     * Pairs the captured original with the screen centre after vanilla centres the cursor in
     * {@code grabMouse()}.
     */
    public static void saveCursorMiddle(double middleX, double middleY) {
        if (isInactive()) {
            pendingOriginal = null;
            return;
        }
        CursorPosition original = pendingOriginal;
        pendingOriginal = null;
        if (original == null) {
            return;
        }
        saved = new SavedCursorPosition(original.x(), original.y(), middleX, middleY, System.currentTimeMillis());
    }

    /**
     * Returns the cursor position to restore in {@code releaseMouse()}, or {@code null} to keep the
     * vanilla centre. Restores only when a fresh saved position exists, the screen centre matches,
     * and the new screen qualifies under the configured mode.
     */
    public static @Nullable CursorPosition loadCursor(double middleX, double middleY, Screen newScreen) {
        SavedCursorPosition last = saved;
        saved = null;
        if (isInactive() || last == null) {
            return null;
        }
        if (System.currentTimeMillis() - last.savedAt() > MAX_AGE_MS) {
            return null;
        }
        if (Math.abs(last.middleX() - middleX) >= 1.0 || Math.abs(last.middleY() - middleY) >= 1.0) {
            return null;
        }
        if (!qualifiesForRestore(newScreen)) {
            return null;
        }
        return new CursorPosition(last.cursorX(), last.cursorY());
    }

    /** True when the feature should do nothing: disabled, or SkyBlock-Enhancements is handling it. */
    private static boolean isInactive() {
        return EnhancedStorageConfig.saveCursorPositionMode == CursorPositionMode.DISABLED
                || SkyblockEnhancementsIntegration.isSaveCursorPositionActive();
    }

    /** Whether the screen being left is eligible to have its cursor position remembered. */
    private static boolean qualifiesForSave() {
        return switch (EnhancedStorageConfig.saveCursorPositionMode) {
            case STORAGE_OVERLAY_ONLY -> StorageOverlay.hasActiveOverlay();
            case ALL_CONTAINERS -> true;
            case DISABLED -> false;
        };
    }

    /** Whether the screen being opened is eligible to receive a remembered cursor position. */
    private static boolean qualifiesForRestore(@Nullable Screen screen) {
        return switch (EnhancedStorageConfig.saveCursorPositionMode) {
            // The new screen's overlay may not be attached yet during screen setup, so qualify by
            // parsing the title with the same rules as StorageLifecycle.createOverlay instead.
            case STORAGE_OVERLAY_ONLY -> screen instanceof AbstractContainerScreen<?> container
                    && StorageTitleParser.parse(container.getTitle().getString()).isPresent();
            case ALL_CONTAINERS -> screen instanceof AbstractContainerScreen<?>;
            case DISABLED -> false;
        };
    }

    public record CursorPosition(double x, double y) {
    }

    private record SavedCursorPosition(double cursorX, double cursorY,
                                       double middleX, double middleY,
                                       long savedAt) {
    }
}
