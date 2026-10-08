package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.gui.AnimatedItemThrottle;
import com.github.kdgaming0.enhancedstorage.gui.layer.PageLayerCache;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Throttles per-frame redraws of animated items in the GUI item atlas; see {@link AnimatedItemThrottle}.
 * <p>
 * Only vanilla's own atlas is throttled. Subclasses may not keep a slot's image between frames: ImmediatelyFast's
 * animated item atlas clears its whole texture every frame, so a kept slot would show nothing. While the throttle
 * is limiting, that atlas declines to prepare, so ImmediatelyFast leaves animated items in vanilla's atlas (which has
 * already made room for them) and the configured rate applies to them too.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {

    @Unique
    private static final String IMMEDIATELYFAST_ANIMATED_ATLAS =
            "net.raphimc.immediatelyfast.feature.batch_animated_item_updates.AnimatedItemAtlas";

    @ModifyArg(
            method = "getOrUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"
            ),
            index = 1
    )
    private boolean enhancedstorage$throttleAnimated(boolean discardAfterFrame) {
        if (!enhancedstorage$isVanillaAtlas()) return discardAfterFrame;
        return AnimatedItemThrottle.discardAfterFrame(discardAfterFrame);
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void enhancedstorage$endFrame(CallbackInfo ci) {
        if (enhancedstorage$isVanillaAtlas()) AnimatedItemThrottle.endFrame();
    }

    // ImmediatelyFast only uses its animated atlas for a frame when this returns true (checked against 1.16.5+26.2).
    // The page layer's own renderer keeps vanilla behaviour there, so ImmediatelyFast may batch it as usual.
    @Inject(method = "tryPrepareFor", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$standAsideWhileLimited(CallbackInfoReturnable<Boolean> cir) {
        if (AnimatedItemThrottle.isLimiting() && !PageLayerCache.isRendering()
                && ((Object) this).getClass().getName().equals(IMMEDIATELYFAST_ANIMATED_ATLAS)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean enhancedstorage$isVanillaAtlas() {
        return ((Object) this).getClass() == GuiItemAtlas.class;
    }
}
