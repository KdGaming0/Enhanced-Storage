package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.screen.IHighlightClipProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenHighlightClipMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @WrapOperation(
            method = "extractContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
            )
    )
    private void enhancedstorage$clipHighlightBack(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                                   Operation<Void> original) {
        enhancedstorage$clipped(graphics, () -> original.call(screen, graphics));
    }

    @WrapOperation(
            method = "extractContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
            )
    )
    private void enhancedstorage$clipHighlightFront(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                                    Operation<Void> original) {
        enhancedstorage$clipped(graphics, () -> original.call(screen, graphics));
    }

    @Unique
    private void enhancedstorage$clipped(GuiGraphicsExtractor graphics, Runnable draw) {
        Rect clip = enhancedstorage$highlightClip();
        if (clip == null) {
            draw.run();
            return;
        }

        graphics.enableScissor(clip.x() - leftPos, clip.y() - topPos, clip.right() - leftPos, clip.bottom() - topPos);
        try {
            draw.run();
        } finally {
            graphics.disableScissor();
        }
    }

    @Unique
    private @Nullable Rect enhancedstorage$highlightClip() {
        if (hoveredSlot == null || !(((Object) this) instanceof IHighlightClipProvider provider)) return null;
        return provider.enhancedstorage$getHighlightClip(hoveredSlot);
    }
}
