package com.github.kdgaming0.enhancedstorage.mixin.compat;

import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;

import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemViewOverlay.class, remap = false)
public abstract class RRVItemViewOverlayMixin {

    @Inject(method = "renderItemHighlighting", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$deferHighlight(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof StorageContainerScreen
                && RRVCompat.canDeferHighlight()
                && !RRVCompat.isRenderingHighlightLate()) {
            ci.cancel(); // skip in the sunk/early pass; we re-draw it on top later
        }
    }
}
