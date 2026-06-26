package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Re-captures storage page data after the server sends a full container contents update.
 * Fixes the race condition where {@code init} fires before slot items have arrived.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void es$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!EnhancedStorageConfig.enableStorageOverlay && !EnhancedStorageConfig.enableRiftStorageOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMenu().containerId != packet.containerId()) return;

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) return;

        StoragePage page = parsed.get().page();
        // Each storage system is captured only when its own overlay is enabled, so the two
        // toggles stay independent (overview pages belong to the MAIN system).
        boolean rift = page != null && page.isRift();
        if (rift ? !EnhancedStorageConfig.enableRiftStorageOverlay
                 : !EnhancedStorageConfig.enableStorageOverlay) return;

        if (parsed.get().isOverview()) {
            StorageLifecycle.onOverviewPacketReceived(screen);
            return;
        }

        if (page == null) return;

        List<ItemStack> stacks = StorageLifecycle.collectContainerStacks(screen);
        if (stacks.isEmpty()) return;

        StorageData.INSTANCE.updateInventory(page, rawTitle, new VirtualInventory(stacks));
    }
}