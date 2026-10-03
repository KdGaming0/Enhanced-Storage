package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if >=26.2
//import net.minecraft.client.gui.Gui;

/**
 * Replaces the vanilla chest screen with the storage overlay when a storage page opens.
 */
//? if <26.2 {
@Mixin(Minecraft.class)
        //?} else {
/*@Mixin(Gui.class)
 *///?}
public abstract class ScreenSwapMixin {

    @Unique
    private static boolean isOverlayEnabled(StorageKey key) {
        return key.type() == StorageKey.Type.RIFT
                ? EnhancedStorageConfig.enableRiftOverlay
                : EnhancedStorageConfig.enableOverlay;
    }

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen enhancedstorage$swapScreen(Screen screen) {
        if (!(screen instanceof ContainerScreen chest)) return screen;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return screen;

        StorageKey key = StorageKey.fromTitle(chest.getTitle()).orElse(null);
        if (key == null || !isOverlayEnabled(key)) return screen;

        return new StorageContainerScreen(chest.getMenu(), player.getInventory(), chest.getTitle(), key);
    }
}