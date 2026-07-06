package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.feature.savecursorposition.CursorPositionManager;
import com.github.kdgaming0.enhancedstorage.feature.savecursorposition.CursorPositionManager.CursorPosition;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves the cursor position across inventory screens (see {@link CursorPositionManager}).
 *
 * <p>The injection points target the third {@code Minecraft.getWindow()} call (ordinal 2) in each
 * method — i.e. immediately before {@code InputConstants.grabOrReleaseMouse} — where {@code xpos}
 * and {@code ypos} have both been assigned the screen centre. This mirrors SkyBlock-Enhancements'
 * {@code SaveCursorPositionMouseHandlerMixin}; the two coexist because the manager defers to
 * SkyBlock-Enhancements at runtime when that mod is handling cursor saving.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    /** Captures the cursor position before vanilla centres it in {@code grabMouse()}. */
    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void es$onGrabMouseHead(CallbackInfo ci) {
        CursorPositionManager.saveCursorOriginal(this.xpos, this.ypos);
    }

    /** Captures the screen centre after vanilla centres the cursor in {@code grabMouse()}. */
    @Inject(method = "grabMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;",
            ordinal = 2))
    private void es$onGrabMouseAfterCenter(CallbackInfo ci) {
        CursorPositionManager.saveCursorMiddle(this.xpos, this.ypos);
    }

    /**
     * Replaces the centre with the saved cursor position before {@code releaseMouse()} asks GLFW to
     * move the cursor. Updating {@code xpos}/{@code ypos} keeps Minecraft's internal mouse state in
     * sync so the first rendered frame shows the correct hover; calling {@code grabOrReleaseMouse}
     * ourselves ensures the visible cursor lands on the restored position immediately.
     */
    @Inject(method = "releaseMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;",
            ordinal = 2))
    private void es$onReleaseMouse(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Screen newScreen = mc{$gui}.screen;
        CursorPosition position = CursorPositionManager.loadCursor(this.xpos, this.ypos, newScreen);
        if (position != null) {
            this.xpos = position.x();
            this.ypos = position.y();
            InputConstants.grabOrReleaseMouse(mc.getWindow(), InputConstants.CURSOR_NORMAL, position.x(), position.y());
        }
    }
}
