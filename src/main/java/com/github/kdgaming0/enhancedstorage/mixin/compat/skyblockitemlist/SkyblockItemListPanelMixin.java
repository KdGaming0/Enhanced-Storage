package com.github.kdgaming0.enhancedstorage.mixin.compat.skyblockitemlist;

import com.github.kdgaming0.enhancedstorage.compat.SkyblockItemListCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shapes SkyBlock Item List's panels on the storage screen when it opens; other screens keep the list's own rules.
 * <ul>
 *     <li>The item list may be narrower than its usual 80px ({@link SkyblockItemListCompat#listMinWidth}).</li>
 *     <li>The favorites panel shows here only while we keep a column for it
 *     ({@link SkyblockItemListCompat#favoritesMinWidth}).</li>
 *     <li>With Make Room on, both panels fill the columns kept for them instead of their max-width share of the gap.</li>
 * </ul>
 * The first 80 belongs to the item list, the second to favorites.
 *
 * <p>The package is the mod id without its dashes; {@code EnhancedStorageMixinPlugin} maps it back.
 */
@Pseudo
@Mixin(targets = "com.operationpotato.itemlist.SkyBlockItemList", remap = false)
public abstract class SkyblockItemListPanelMixin {

    @ModifyExpressionValue(method = "addItemListWidget",
            at = @At(value = "CONSTANT", args = "intValue=80", ordinal = 0), require = 0)
    private int enhancedstorage$minListWidth(int original) {
        return SkyblockItemListCompat.listMinWidth(original);
    }

    @ModifyExpressionValue(method = "addItemListWidget",
            at = @At(value = "CONSTANT", args = "intValue=80", ordinal = 1), require = 0)
    private int enhancedstorage$minFavoritesWidth(int original) {
        return SkyblockItemListCompat.favoritesMinWidth(original);
    }

    @ModifyExpressionValue(method = "addItemListWidget",
            at = @At(value = "INVOKE",
                    target = "Lcom/operationpotato/itemlist/config/Settings$GeneralSettings;getMaxWidth()F"),
            require = 0)
    private float enhancedstorage$fillColumns(float original) {
        return SkyblockItemListCompat.fillsReservedColumn() ? 1f : original;
    }
}
