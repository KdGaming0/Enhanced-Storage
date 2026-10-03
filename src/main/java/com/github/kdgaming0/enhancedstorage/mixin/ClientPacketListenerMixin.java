package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.github.kdgaming0.enhancedstorage.storage.capture.ContainerContentTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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

    // Hypixel may close the current page before opening the next one; if those packets land on different frames, one frame
    // renders with no screen (a flash of the world). Keep the overlay up while a page switch is in flight.
    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    private void enhancedstorage$onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        // HEAD also runs on the network thread before vanilla hands the packet to the main thread; only act on the latter.
        if (!minecraft.isSameThread()) return;
        //? if <26.2 {
        Screen current = minecraft.screen;
        //?} else {
        /*Screen current = minecraft.gui.screen();
         *///?}
        if (current instanceof StorageContainerScreen screen
                && screen.deferCloseForNavigation(packet.getContainerId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void enhancedstorage$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        ContainerContentTracker.markReceived(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void enhancedstorage$onContainerSlotChanged(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        ContainerContentTracker.markChanged(packet.getContainerId());
    }
}
