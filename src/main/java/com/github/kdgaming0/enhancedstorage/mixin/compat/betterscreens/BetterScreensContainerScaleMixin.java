package com.github.kdgaming0.enhancedstorage.mixin.compat.betterscreens;

import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Better Screens' container scale apply to Edit Mode too. It only scales container screens, and Edit Mode is a
 * plain screen, so without this it would jump to the game's scale when entered from the live overlay.
 * <p>
 * {@code require = 0}: if a Better Screens update reshapes the check, Edit Mode just stays at our own scale.
 */
@Mixin(targets = "dev.microcontrollers.betterscreens.hook.ContainerScaleHook", remap = false)
public abstract class BetterScreensContainerScaleMixin {

    @Definition(id = "AbstractContainerScreen", type = AbstractContainerScreen.class)
    @Expression("? instanceof AbstractContainerScreen")
    @ModifyExpressionValue(method = "shouldScale", at = @At("MIXINEXTRAS:EXPRESSION"), require = 0)
    private static boolean enhancedstorage$scaleEditMode(boolean isContainerScreen) {
        //? if <26.2 {
        Screen screen = Minecraft.getInstance().screen;
        //?} else {
        /*Screen screen = Minecraft.getInstance().gui.screen();
         *///?}
        return isContainerScreen || screen instanceof StorageOverlay;
    }
}
