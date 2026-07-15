package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.screen.IHighlightClipProvider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenHighlightClipMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"))
    private void enhancedstorage$clipBackStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        enhancedstorage$apply(graphics);
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("TAIL"))
    private void enhancedstorage$clipBackEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        enhancedstorage$remove(graphics);
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"))
    private void enhancedstorage$clipFrontStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        enhancedstorage$apply(graphics);
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("TAIL"))
    private void enhancedstorage$clipFrontEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        enhancedstorage$remove(graphics);
    }

    @Unique
    private void enhancedstorage$apply(GuiGraphicsExtractor graphics) {
        if (hoveredSlot == null || !(((Object) this) instanceof IHighlightClipProvider provider)) return;
        int[] clip = provider.enhancedstorage$getHighlightClip(hoveredSlot);
        if (clip != null) {
            graphics.enableScissor(clip[0] - leftPos, clip[1] - topPos, clip[2] - leftPos, clip[3] - topPos);
        }
    }

    @Unique
    private void enhancedstorage$remove(GuiGraphicsExtractor graphics) {
        if (hoveredSlot == null || !(((Object) this) instanceof IHighlightClipProvider provider)) return;
        if (provider.enhancedstorage$getHighlightClip(hoveredSlot) != null) {
            graphics.disableScissor();
        }
    }
}