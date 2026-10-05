package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.gui.AnimatedItemThrottle;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Throttles per-frame redraws of animated items in the GUI item atlas; see {@link AnimatedItemThrottle}. */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {

    @ModifyArg(
            method = "getOrUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"
            ),
            index = 1
    )
    private boolean enhancedstorage$throttleAnimated(boolean discardAfterFrame) {
        return AnimatedItemThrottle.discardAfterFrame(discardAfterFrame);
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void enhancedstorage$endFrame(CallbackInfo ci) {
        AnimatedItemThrottle.endFrame();
    }
}
