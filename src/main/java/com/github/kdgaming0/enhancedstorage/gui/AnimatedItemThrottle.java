package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.layer.PageLayerCache;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * Caps how often animated GUI items (enchant glint, animated textures) are redrawn while a storage screen is open.
 * <p>
 * Vanilla's GUI item atlas draws static items once and reuses them, but flags animated ones to be freed after every
 * frame, so each distinct animated item is fully re-rendered every frame. A storage overlay full of enchanted items
 * makes that the big per-frame cost. Between refreshes the flag is cleared, so the atlas keeps the last image like
 * a static item; on a refresh frame it is passed through, the slot is freed at frame end, and the next frame redraws it.
 */
public final class AnimatedItemThrottle {

    private static boolean refreshThisFrame = true;
    private static long nextRefreshAt;

    private AnimatedItemThrottle() {
    }

    /**
     * The atlas's "free this slot after the frame" flag for an item, throttled while a storage screen is open. The
     * page layer's renderer keeps vanilla behaviour: it only runs when its layer is redrawn.
     */
    public static boolean discardAfterFrame(boolean animated) {
        if (PageLayerCache.isRendering()) return animated;
        return animated && refreshThisFrame;
    }

    /** Called after an atlas frees its slots; decides whether the next frame refreshes. */
    public static void endFrame() {
        // The page layer's atlas ends its own frame too; only the main GUI's counts.
        if (PageLayerCache.isRendering()) return;
        if (!isLimiting()) {
            refreshThisFrame = true;
            return;
        }
        long now = Util.getMillis();
        refreshThisFrame = now >= nextRefreshAt;
        if (refreshThisFrame) nextRefreshAt = now + EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis;
    }

    /** True while animated items are redrawn less often than every frame. */
    public static boolean isLimiting() {
        return EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis > 0
                && isStorageScreen(Minecraft.getInstance().screen);
    }

    private static boolean isStorageScreen(Screen screen) {
        return screen instanceof StorageContainerScreen || screen instanceof StorageOverlay;
    }
}
