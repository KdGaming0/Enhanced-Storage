package com.github.kdgaming0.enhancedstorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Mutable
    @Accessor("imageWidth")
    void enhancedstorage$setImageWidth(int value);

    @Mutable
    @Accessor("imageHeight")
    void enhancedstorage$setImageHeight(int value);
}
