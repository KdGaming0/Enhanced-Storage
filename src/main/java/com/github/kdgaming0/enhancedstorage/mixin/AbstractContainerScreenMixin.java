package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.OverlayHolder;
import com.github.kdgaming0.enhancedstorage.gui.Rect;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.storage.StorageLifecycle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin<T extends AbstractContainerMenu> implements OverlayHolder {

    @Unique
    private StorageOverlay es$overlay;

    @Override
    public boolean es$hasOverlay() {
        return es$overlay != null;
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void es$onInit(CallbackInfo ci) {
        if (es$overlay == null) {
            es$overlay = StorageLifecycle.createOverlay((AbstractContainerScreen<?>)(Object)this);
        }
        if (es$overlay != null) {
            es$overlay.onInit(((Screen)(Object)this).width, ((Screen)(Object)this).height);
            EditBox searchField = es$overlay.getSearchField();
            if (searchField != null) {
                ((ScreenAccessor) this).es$addWidget(searchField);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void es$preExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (es$overlay != null) es$overlay.preRender(mouseX, mouseY);
    }

    @Inject(
            method = "extractContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void es$afterSuperExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (es$overlay == null) return;
        es$overlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Cancel vanilla label rendering; our panels draw all labels. */
    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void es$onExtractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (es$overlay != null) ci.cancel();
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void es$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay != null && es$overlay.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void es$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay != null && es$overlay.mouseReleased(event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z", at = @At("HEAD"), cancellable = true)
    private void es$onMouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay != null && es$overlay.mouseDragged(event, dx, dy)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void es$onMouseScrolled(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay != null && es$overlay.mouseScrolled(x, y, scrollX, scrollY)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void es$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay != null && es$overlay.keyPressed(event)) cir.setReturnValue(true);
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void es$onHasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, CallbackInfoReturnable<Boolean> cir) {
        if (es$overlay == null) return;
        for (Rect rect : es$overlay.getBounds()) {
            if (rect.contains(mouseX, mouseY)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void es$onRemoved(CallbackInfo ci) {
        if (es$overlay != null) {
            es$overlay.saveState();
        }
        es$overlay = null;
    }
}
