package com.github.kdgaming0.enhancedstorage.mixin.compat;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ChestValue.isValidStorage() rejects any screen that is not the vanilla
 * ContainerScreen before its title/bazaar/minion checks even run. Widen that
 * one check to also accept our storage screen; all other validity rules stay
 * SkyHanni's. require = 0 so a SkyHanni refactor skips the injection instead
 * of crashing.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.inventory.ChestValue", remap = false)
public class SkyHanniChestValueMixin {

    @ModifyExpressionValue(
            method = "isValidStorage",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/gui/screens/inventory/ContainerScreen"),
            require = 0
    )
    private boolean enhancedstorage$treatStorageScreenAsChest(boolean original) {
        return original || SkyHanniCompat.isStorageScreenOpen();
    }
}
