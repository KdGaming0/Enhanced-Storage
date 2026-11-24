package com.github.kdgaming0.enhancedstorage.mixin.compat.skyhanni;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SkyHanni turns a GUI element's saved Position into screen coordinates here, both to draw it and to work out where
 * the mouse is over it. While Chest Value draws on the storage screen (see {@link SkyHanniChestValuePositionMixin}),
 * these return the spot beside our pages instead; everything else keeps SkyHanni's own position. Argument-less
 * handlers, so SkyHanni's Position type isn't needed; require = 0 so a SkyHanni refactor skips them instead of
 * crashing.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.data.GuiEditManager", remap = false)
public class SkyHanniGuiEditManagerMixin {

    @Inject(method = "getAbsX", at = @At("HEAD"), cancellable = true, require = 0)
    private void enhancedstorage$chestValueX(CallbackInfoReturnable<Integer> cir) {
        Integer x = SkyHanniCompat.chestValueAbsX();
        if (x != null) cir.setReturnValue(x);
    }

    @Inject(method = "getAbsY", at = @At("HEAD"), cancellable = true, require = 0)
    private void enhancedstorage$chestValueY(CallbackInfoReturnable<Integer> cir) {
        Integer y = SkyHanniCompat.chestValueAbsY();
        if (y != null) cir.setReturnValue(y);
    }
}
