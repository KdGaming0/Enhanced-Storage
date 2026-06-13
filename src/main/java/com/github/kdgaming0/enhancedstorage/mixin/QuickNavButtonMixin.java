package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.OverlayHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Skyblocker's QuickNav buttons from recalculating their coordinates against the
 * vanilla inventory while Enhanced Storage's overlay is active. The overlay repositions the
 * buttons itself via reflection in {@link com.github.kdgaming0.enhancedstorage.integration.SkyblockerIntegration}.
 */
@Mixin(targets = "de.hysky.skyblocker.skyblock.quicknav.QuickNavButton")
public class QuickNavButtonMixin {

    @Inject(method = "updateCoordinates", at = @At("HEAD"), cancellable = true)
    private void es$onUpdateCoordinates(CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof OverlayHolder holder && holder.es$hasOverlay()) {
            ci.cancel();
        }
    }
}
