package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * Per-menu GUI scale for the storage overlay.
 *
 * <p>{@link StorageContainerScreen} uses vanilla's own mechanism rather than working around it: it
 * sets the window's GUI scale on open and puts it back on close, which is all
 * {@code Minecraft.resizeGui()} does. So every integer scale is available and renders exactly as
 * crisply as if the player had set it globally.
 *
 * <p>The catch is that this is <b>global window state</b>: while the overlay is open,
 * {@link Window#getGuiScale()} reports our scale, not the player's setting. Anything that wants the
 * player's actual preference — our compat code, or another mod reading the window during our screen —
 * must use {@link #gameScale(Minecraft)}.
 *
 * <p>Restoration is stateless: {@link #restoreGameScale(Minecraft)} recomputes from the player's
 * options rather than remembering the previous value, so nothing can go stale and a missed restore
 * repairs itself on the next resize. The override is never written to {@code options.txt}, so it
 * can't outlive the process either. Between that and {@code Screen.removed()}, every exit path is
 * covered — no extra lifecycle hook is needed (audited in IMPLEMENTATION_LOG.md).
 */
public final class OverlayScale {

    private OverlayScale() {
    }

    /**
     * The scale the game would be using if the overlay were not open. Derived from the player's options
     * every time, never from the window's current scale, which we may have overwritten.
     */
    public static int gameScale(Minecraft minecraft) {
        return minecraft.getWindow()
                .calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
    }

    /**
     * The GUI scale the overlay should render at. A configured value of {@code 0} means "follow the
     * game's scale", mirroring vanilla's {@code AUTO_GUI_SCALE} convention.
     *
     * <p>The ceiling is vanilla's own — {@code calculateScale(0, ...)} is what its GUI scale slider
     * clamps to — so it guarantees the value is usable, not that the overlay looks good at it. Past a
     * point the layout drops page columns to fit, same as at a high global GUI scale.
     */
    public static int resolveScale(Minecraft minecraft) {
        int configured = EnhancedStorageConfig.overlayGuiScale;
        if (configured <= 0) return gameScale(minecraft);

        int maxScale = minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
        return Math.max(1, Math.min(configured, maxScale));
    }

    /**
     * Brings the window's GUI scale in line with the config. Maintains global state rather than
     * performing a one-shot action: idempotent, and re-run on every {@code init()} so resizes and live
     * config edits settle on the right value.
     */
    public static void sync(Minecraft minecraft) {
        setGuiScale(minecraft, resolveScale(minecraft));
    }

    /**
     * Puts the player's own GUI scale back. Called when the overlay closes or is replaced.
     */
    public static void restoreGameScale(Minecraft minecraft) {
        setGuiScale(minecraft, gameScale(minecraft));
    }

    private static void setGuiScale(Minecraft minecraft, int scale) {
        Window window = minecraft.getWindow();
        if (window.getGuiScale() == scale) return;

        window.setGuiScale(scale);
        // The cursor is now at a different position in GUI coordinates through no movement of the
        // user's; vanilla drops that first synthetic move in resizeGui() for the same reason.
        minecraft.mouseHandler.setIgnoreFirstMove();
    }
}
