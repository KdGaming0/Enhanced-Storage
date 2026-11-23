package com.github.kdgaming0.enhancedstorage.mixin.compat.skyocean;

import com.github.kdgaming0.enhancedstorage.compat.SkyOceanCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SkyOcean sizes its craft helper panel from the gap left of the container ({@code getLeft(screen)}). On the storage
 * screen that gap is nearly zero, so the panel's maximum width is replaced by the column the overlay keeps free for
 * it (see {@link SkyOceanCompat}). Other screens are left alone.
 *
 * <p>The panel is also captured as soon as it's built: SkyOcean clears its own reference on the next container close,
 * which for page switches arrives after the new page has built its panel.
 */
@Pseudo
@Mixin(targets = "me.owdding.skyocean.features.recipe.crafthelper.display.CraftHelperDisplay", remap = false)
public abstract class SkyOceanCraftHelperDisplayMixin {

    @ModifyExpressionValue(
            method = "onScreenInit",
            at = @At(value = "INVOKE",
                    target = "Ltech/thatgravyboat/skyblockapi/utils/extentions/MixinExtensionsKt;getLeft(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;)I"),
            require = 0)
    private int enhancedstorage$panelRoom(int original) {
        return SkyOceanCompat.panelRoom(original);
    }

    @Inject(method = "onScreenInit", at = @At("HEAD"), require = 0)
    private void enhancedstorage$clearOldPanel(CallbackInfo ci) {
        SkyOceanCompat.beforeSkyOceanInit();
    }

    @Inject(method = "onScreenInit", at = @At("RETURN"), require = 0)
    private void enhancedstorage$capturePanel(CallbackInfo ci) {
        SkyOceanCompat.capturePanel();
    }
}
