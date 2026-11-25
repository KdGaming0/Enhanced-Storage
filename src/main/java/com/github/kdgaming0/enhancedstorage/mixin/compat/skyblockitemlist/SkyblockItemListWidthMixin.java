package com.github.kdgaming0.enhancedstorage.mixin.compat.skyblockitemlist;

import com.github.kdgaming0.enhancedstorage.compat.SkyblockItemListCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SkyBlock Item List sizes a panel again on resize from its max-width share of the gap; on the storage screen with
 * Make Room on, each panel fills the column kept for it instead (same rule as {@link SkyblockItemListPanelMixin}).
 */
@Pseudo
@Mixin(targets = "com.operationpotato.itemlist.gui.AbstractItemPanel", remap = false)
public abstract class SkyblockItemListWidthMixin {

    @ModifyExpressionValue(method = "updateWidth",
            at = @At(value = "INVOKE",
                    target = "Lcom/operationpotato/itemlist/config/Settings$GeneralSettings;getMaxWidth()F"),
            require = 0)
    private float enhancedstorage$fillColumns(float original) {
        return SkyblockItemListCompat.fillsReservedColumn() ? 1f : original;
    }
}
