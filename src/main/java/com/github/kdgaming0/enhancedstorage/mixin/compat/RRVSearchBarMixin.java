package com.github.kdgaming0.enhancedstorage.mixin.compat;

import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;

@Mixin(value = SearchBar.class, remap = false)
public abstract class RRVSearchBarMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$requireMouseOver(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        // Only alter behavior inside Enhanced Storage's screen
        if (!(Minecraft.getInstance().screen instanceof StorageContainerScreen)) return;

        SearchBar self = (SearchBar) (Object) this;
        if (!self.isMouseOver(event.x(), event.y())) {
            cir.setReturnValue(false);
        }
    }
}