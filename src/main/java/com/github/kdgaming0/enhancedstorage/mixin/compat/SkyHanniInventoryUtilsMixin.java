package com.github.kdgaming0.enhancedstorage.mixin.compat;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * InventoryUtils.getItemsInOpenChestWithNull() returns an empty list unless
 * the open screen is the vanilla ContainerScreen (it casts, so the boolean
 * widening used elsewhere is not safe here). When our storage screen is open,
 * short-circuit with the equivalent slot list from our ChestMenu instead.
 * getItemsInOpenChest() filters this method's result, so both are covered.
 * require = 0 so a SkyHanni refactor skips the injection instead of crashing.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.utils.InventoryUtils", remap = false)
public class SkyHanniInventoryUtilsMixin {

    @Inject(method = "getItemsInOpenChestWithNull", at = @At("HEAD"), cancellable = true, require = 0)
    private void enhancedstorage$storageScreenSlots(CallbackInfoReturnable<List<Slot>> cir) {
        List<Slot> slots = SkyHanniCompat.containerSlotsOfOpenStorageScreen();
        if (slots != null) {
            cir.setReturnValue(slots);
        }
    }

    /**
     * inInventory() is "screen instanceof ContainerScreen" — false for our
     * storage screen, so Item Pickup Log never freezes its inventory snapshot
     * while our screen is open and logs every intermediate move as a
     * gain/loss. Widening the check restores the freeze-and-batch behavior
     * (only the net change of the session is logged). Also read by other
     * SkyHanni features (page scrolling, HUD positioning), which likewise
     * should treat our screen as a chest.
     */
    @ModifyExpressionValue(
            method = "inInventory",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/gui/screens/inventory/ContainerScreen"),
            require = 0
    )
    private boolean enhancedstorage$treatStorageScreenAsInventory(boolean original) {
        return original || SkyHanniCompat.isStorageScreenOpen();
    }
}
