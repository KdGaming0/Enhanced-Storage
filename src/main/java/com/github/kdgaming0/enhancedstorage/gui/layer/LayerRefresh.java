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

    /** True when the layer no longer matches {@code key}, or is due for a safety redraw. */
    public boolean needsRedraw(Object key) {
        long age = Util.getMillis() - drawnAt;
        // Animated items are drawn live on top of the layer (LayerItems). Should one still end up inside it, keep it
        // moving at the glint rate rather than freezing it until the safety refresh.
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
