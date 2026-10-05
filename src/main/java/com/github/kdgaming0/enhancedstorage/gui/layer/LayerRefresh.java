package com.github.kdgaming0.enhancedstorage.gui.layer;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Decides when one owner's {@link PageLayerCache} layer must be redrawn. The owner describes everything its layer
 * shows as a key (compared with {@code equals}); any difference means the layer is out of date.
 */
public final class LayerRefresh {

    // Redraws the layer now and then even when nothing it tracks changed, so anything missed can't stay stale.
    private static final long SAFETY_REFRESH_MS = 1000;

    private @Nullable Object drawnFrom;
    private long drawnAt;
    private boolean animated;

    /**
     * True when the owner should draw directly this frame instead of from its layer. Smooth glint redraws a layer
     * holding animated items every frame, which costs more than not caching at all. The layer is tried again after
     * the safety refresh in case they're gone.
     */
    public boolean prefersDirect() {
        return drawnFrom != null && animated && EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis <= 0
                && Util.getMillis() - drawnAt < SAFETY_REFRESH_MS;
    }

    /** True when the layer no longer matches {@code key}, or is due for a glint or safety redraw. */
    public boolean needsRedraw(Object key) {
        long age = Util.getMillis() - drawnAt;
        // Enchant glint inside the layer only moves when the layer is redrawn; keep it at the configured rate.
        return !key.equals(drawnFrom) || age >= SAFETY_REFRESH_MS
                || (animated && age >= EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis);
    }

    /** Records a redraw from {@code key}; {@code animated} is what {@link PageLayerCache#endLayer} returned. */
    public void drawn(Object key, boolean animated) {
        this.drawnFrom = key;
        this.drawnAt = Util.getMillis();
        this.animated = animated;
    }
}
