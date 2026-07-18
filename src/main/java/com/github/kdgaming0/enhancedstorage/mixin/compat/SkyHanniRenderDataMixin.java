package com.github.kdgaming0.enhancedstorage.mixin.compat;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SkyHanni only posts its chest-GUI overlay render events (which Chest Value
 * and other chest HUDs draw on) while the vanilla InventoryScreen or
 * ContainerScreen is open. Widen the ContainerScreen check to also accept our
 * storage screen. require = 0: if SkyHanni's internals change, the injection
 * is skipped (Mixin logs a warning) instead of crashing.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.data.RenderData", remap = false)
public class SkyHanniRenderDataMixin {

    @ModifyExpressionValue(
            method = "onBackgroundDraw",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/gui/screens/inventory/ContainerScreen"),
            require = 0
    )
    private boolean enhancedstorage$treatStorageScreenAsChest(boolean original) {
        return original || SkyHanniCompat.isStorageScreenOpen();
    }
}
