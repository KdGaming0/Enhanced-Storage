package com.github.kdgaming0.enhancedstorage.mixin.compat.skyhanni;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SkyHanni's GUI editor hotkey only works with no screen open or on the vanilla inventory, chest or a sign screen, so
 * pressing it on the storage screen did nothing. Widen the chest check to also accept our screen; the editor itself
 * takes any container screen. Kept apart from {@link SkyHanniGuiEditManagerMixin} so a change here can't disable the
 * Chest Value placement. require = 0 so a SkyHanni refactor skips the injection instead of crashing.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.data.GuiEditManager", remap = false)
public class SkyHanniGuiEditorHotkeyMixin {

    @ModifyExpressionValue(
            method = "onKeyPress",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/gui/screens/inventory/ContainerScreen"),
            require = 0
    )
    private boolean enhancedstorage$allowOnStorageScreen(boolean original) {
        return original || SkyHanniCompat.isStorageScreenOpen();
    }
}
