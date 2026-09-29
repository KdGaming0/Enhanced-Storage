package com.github.kdgaming0.enhancedstorage.util;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Util;

public final class SkyblockState {

    private static volatile boolean inSkyblock;

    private SkyblockState() {
    }

    public static void register() {
        // Subscribe once; HM API registers this with Hypixel after its handshake.
        HypixelNetworking.registerToEvents(Util.make(new Object2IntOpenHashMap<>(), map -> map.put(LocationUpdateS2CPacket.ID, 1)));

        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (!(packet instanceof LocationUpdateS2CPacket location)) return;

            inSkyblock = location.serverType().map("SKYBLOCK"::equals).orElse(false);
        });

        // Do not carry a SkyBlock result into a later connection.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> inSkyblock = false);
    }

    public static boolean isInSkyblock() {
        return inSkyblock;
    }
}
