package com.github.kdgaming0.enhancedstorage.mixin.compat.rrv;

import cc.cassian.rrv.common.overlay.OverlayManager;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The background counterpart of {@link RRVOverlayManagerMixin}: on our screen RRV's panel backgrounds are drawn early,
 * together with the item list, so RRV's own after-background draw would paint over the items and Quick Nav tabs.
 * Kept separate so an RRV version without {@code renderAllBackground} only loses this, not the item list fix.
 */
@Mixin(value = OverlayManager.class, remap = false)
public abstract class RRVOverlayManagerBackgroundMixin {

    @Inject(method = "renderAllBackground", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$suppressLateBackground(CallbackInfo ci) {
        if (RRVCompat.isRenderingEarly()) return; // our own call — let it through
        if (Minecraft.getInstance().screen instanceof StorageContainerScreen) {
            ci.cancel();
        }
    }
}
