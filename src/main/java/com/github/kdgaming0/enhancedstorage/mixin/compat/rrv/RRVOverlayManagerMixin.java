package com.github.kdgaming0.enhancedstorage.mixin.compat.rrv;

import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OverlayManager.class, remap = false)
public abstract class RRVOverlayManagerMixin {

    @Inject(method = "renderAll", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$suppressLateRender(CallbackInfo ci) {
        if (RRVCompat.isRenderingEarly()) return; // our own call — let it through
        if (Minecraft.getInstance().screen instanceof StorageContainerScreen) {
            ci.cancel(); // suppress RRV's normal on-top render
        }
    }
}
