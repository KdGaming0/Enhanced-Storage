package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.util.ProfileIdTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards system chat to {@link ProfileIdTracker#handleIncomingChat(String)} so the tracker can
 * parse Hypixel's automatic {@code Profile ID: ...} messages.
 *
 * <p>Hypixel sends these messages automatically on every SkyBlock server change, so the mod does
 * not need to issue {@code /profileid} itself. The message is left visible in chat.
 */
@Mixin(ClientPacketListener.class)
public class ProfileIdResponseMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void es$handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        ProfileIdTracker.handleIncomingChat(packet.content().getString());
    }
}
