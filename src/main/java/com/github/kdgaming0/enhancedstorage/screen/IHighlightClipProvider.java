package com.github.kdgaming0.enhancedstorage.screen;

import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public interface IHighlightClipProvider {

    /**
     * Screen-space scissor rect for this slot's highlight, or null for no clip.
     */
    @Nullable
    Rect enhancedstorage$getHighlightClip(Slot slot);
}