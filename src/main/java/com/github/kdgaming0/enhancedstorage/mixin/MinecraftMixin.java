package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, name = "screen")
    private Screen enhancedstorage$swapScreen(Screen screen) {
        if (!(screen instanceof ContainerScreen containerScreen)) return screen;

        return StorageKey.fromTitle(containerScreen.getTitle())
                .<Screen>map(key -> new StorageContainerScreen(
                        containerScreen.getMenu(),
                        Minecraft.getInstance().player.getInventory(),
                        containerScreen.getTitle(),
                        key))
                .orElse(screen);
    }
}
