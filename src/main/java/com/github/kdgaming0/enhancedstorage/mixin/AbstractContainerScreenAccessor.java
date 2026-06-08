package com.github.kdgaming0.enhancedstorage.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int es$getLeftPos();

    @Accessor("topPos")
    int es$getTopPos();

    @Accessor("imageWidth")
    int es$getImageWidth();

    @Accessor("imageHeight")
    int es$getImageHeight();
}
