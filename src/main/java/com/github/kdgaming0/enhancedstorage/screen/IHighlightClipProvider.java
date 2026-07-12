package com.github.kdgaming0.enhancedstorage.screen;

import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public interface IHighlightClipProvider {
    /**
     * [left, top, right, bottom] scissor rect for this slot's highlight, or null for no clip.
     */
    @Nullable
    int[] enhancedstorage$getHighlightClip(Slot slot);
}