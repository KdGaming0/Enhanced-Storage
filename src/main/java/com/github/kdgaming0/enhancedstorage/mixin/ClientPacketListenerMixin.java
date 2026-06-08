package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.StorageData;
import com.github.kdgaming0.enhancedstorage.storage.StorageLifecycle;
import com.github.kdgaming0.enhancedstorage.storage.StoragePage;
import com.github.kdgaming0.enhancedstorage.storage.StorageTitleParser;
import com.github.kdgaming0.enhancedstorage.storage.VirtualInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Re-captures storage page data when the server sends a full container contents update.
 * This fixes the race condition where {@code init} fires before slot items have arrived.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void es$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!EnhancedStorageConfig.enableStorageOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.player == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMenu().containerId != packet.containerId()) return;

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) return;

        if (parsed.get().isOverview()) {
            StorageLifecycle.onOverviewPacketReceived(screen);
            return;
        }

        StoragePage page = parsed.get().page();
        if (page == null) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (Slot s : screen.getMenu().slots) {
            if (s.container != mc.player.getInventory()) {
                while (stacks.size() <= s.index) stacks.add(ItemStack.EMPTY);
                stacks.set(s.index, s.getItem().copy());
            }
        }
        int rows = Math.clamp((stacks.size() + 8) / 9, 1, 6);
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);

        VirtualInventory vinv = new VirtualInventory(stacks);
        StorageData.INSTANCE.updateInventory(page, rawTitle, vinv);
        StorageData.INSTANCE.markDirty();
    }
}
