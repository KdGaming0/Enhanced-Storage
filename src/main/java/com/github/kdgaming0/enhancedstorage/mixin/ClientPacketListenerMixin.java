package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.storage.ContainerContentTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    // TAIL so this only runs on the main-thread invocation, after the old
    // screen's remove handlers (which still need the previous container id).
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void enhancedstorage$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        ContainerContentTracker.reset();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void enhancedstorage$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        ContainerContentTracker.markReceived(packet.containerId());
    }
}
