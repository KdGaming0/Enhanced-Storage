package com.github.kdgaming0.enhancedstorage.mixin.compat.skyblocker;

import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import de.hysky.skyblocker.skyblock.quicknav.QuickNavButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skyblocker centres Quick Nav tabs on the screen bounds we report, which span the whole overlay (navigation panel,
 * pages and inventory). Those bounds can't shrink without moving everything else that anchors to them, so the tabs
 * are re-centred here instead. Skyblocker recomputes the position every frame, so this only shifts X afterwards.
 */
@Mixin(value = QuickNavButton.class, remap = false)
public abstract class SkyblockerQuickNavButtonMixin {

    @Inject(method = "updateCoordinates", at = @At("TAIL"))
    private void enhancedstorage$centreOnOverlay(CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof StorageContainerScreen screen)) return;
        AbstractWidget self = (AbstractWidget) (Object) this;
        self.setX(screen.quickNavTabX(self.getX(), self.getY(), ((QuickNavButton) (Object) this).toggled()));
    }
}
