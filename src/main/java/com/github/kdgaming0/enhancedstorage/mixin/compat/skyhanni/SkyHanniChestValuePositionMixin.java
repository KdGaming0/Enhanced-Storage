package com.github.kdgaming0.enhancedstorage.mixin.compat.skyhanni;

import com.github.kdgaming0.enhancedstorage.compat.SkyHanniCompat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Marks the span in which SkyHanni's Chest Value draws its list, so {@link SkyHanniGuiEditManagerMixin} moves only
 * that list beside our pages. Its Position type isn't on our classpath, so the position is swapped where SkyHanni
 * turns it into screen coordinates instead of here. Wrapped (not split over HEAD/TAIL) so the flag is cleared even
 * when the method throws or another mod cancels it. The descriptor is spelled out so a changed signature skips the
 * wrap instead of failing to apply.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.inventory.ChestValue", remap = false)
public class SkyHanniChestValuePositionMixin {

    @WrapMethod(method = "onChestGuiRender()V", require = 0)
    private void enhancedstorage$besidePages(Operation<Void> original) {
        SkyHanniCompat.beginChestValueDraw();
        try {
            original.call();
        } finally {
            SkyHanniCompat.endChestValueDraw();
        }
    }
}
