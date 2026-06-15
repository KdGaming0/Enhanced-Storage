package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.OverlayHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Skyblocker's QuickNav buttons from recalculating their coordinates against the
 * vanilla inventory while Enhanced Storage's overlay is active. The overlay repositions the
 * buttons itself via reflection in {@link com.github.kdgaming0.enhancedstorage.integration.SkyblockerIntegration}.
 *
 * <p>Buttons are born invisible when created during an active overlay to prevent a single-frame
 * flash at the default (0, 0) construction position. {@code positionQuickNavButtons()} restores
 * visibility after setting correct overlay coordinates.
 */
@Mixin(targets = "de.hysky.skyblocker.skyblock.quicknav.QuickNavButton")
public class QuickNavButtonMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void es$onConstruct(int index, boolean toggled, String command, ItemStack icon, String tooltip, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof OverlayHolder holder && holder.es$hasOverlay()) {
            ((AbstractWidget) (Object) this).visible = false;
        }
    }

    @Inject(method = "updateCoordinates", at = @At("HEAD"), cancellable = true)
    private void es$onUpdateCoordinates(CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof OverlayHolder holder && holder.es$hasOverlay()) {
            ci.cancel();
        }
    }
}
