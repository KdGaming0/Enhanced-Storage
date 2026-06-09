package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.OverlayHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the vanilla container background texture ({@code generic_54.png})
 * when the Enhanced Storage overlay is active. The {@code super.extractBackground()} call (dark
 * screen overlay) is preserved; only the {@code blit} calls that draw the chest GUI are skipped.
 */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class})
public class ContainerBackgroundMixin {

    @Inject(
            method = "extractBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void es$cancelContainerTexture(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (((OverlayHolder) this).es$hasOverlay()) {
            ci.cancel();
        }
    }
}