package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean mouseGrabbed;
    @Shadow @Final private Minecraft minecraft;

    @Unique private double enhancedstorage$savedX;
    @Unique private double enhancedstorage$savedY;
    @Unique private long enhancedstorage$grabbedAt = Long.MIN_VALUE;

    // Menu is closing: remember where the cursor was, and when.
    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void enhancedstorage$saveCursorPos(CallbackInfo ci) {
        if (!EnhancedStorageConfig.saveCursorPosition) return;

        if (this.minecraft.isWindowActive() && !this.mouseGrabbed) {
            this.enhancedstorage$savedX = this.xpos;
            this.enhancedstorage$savedY = this.ypos;
            this.enhancedstorage$grabbedAt = Util.getMillis();
        }
    }

    // Menu is opening: restore the saved position if it's been < 0.5s (default value).
    @WrapOperation(
            method = "releaseMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"
            )
    )
    private void enhancedstorage$restoreCursorPos(Window window, int cursorMode, double xpos, double ypos, Operation<Void> original) {
        boolean recent = this.enhancedstorage$grabbedAt != Long.MIN_VALUE && Util.getMillis() - this.enhancedstorage$grabbedAt
                <= (long) (EnhancedStorageConfig.saveCursorPositionWindow * 1000);
        if (EnhancedStorageConfig.saveCursorPosition && recent) {
            this.xpos = this.enhancedstorage$savedX;
            this.ypos = this.enhancedstorage$savedY;
            original.call(window, cursorMode, this.enhancedstorage$savedX, this.enhancedstorage$savedY);
            GLFW.glfwSetCursorPos(window.handle(), this.enhancedstorage$savedX, this.enhancedstorage$savedY);
        } else {
            original.call(window, cursorMode, xpos, ypos);
        }
    }
}