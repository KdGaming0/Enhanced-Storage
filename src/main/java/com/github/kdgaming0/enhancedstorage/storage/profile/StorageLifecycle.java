package com.github.kdgaming0.enhancedstorage.storage.profile;

import com.github.kdgaming0.enhancedstorage.storage.ContainerContentTracker;
import com.github.kdgaming0.enhancedstorage.storage.ProfileIdRequester;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Connects connection, tick and shutdown events to the profile session. */
public final class StorageLifecycle {

    private StorageLifecycle() {
    }

    @SuppressWarnings("unused")
    public static void register() {
        ProfileSession session = ProfileSession.getInstance();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> {
                    ContainerContentTracker.reset();
                    ProfileIdRequester.reset();
                    session.onJoin();
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ContainerContentTracker.reset();
            ProfileIdRequester.reset();
            session.onDisconnect();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> session.tick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> session.onStopping());
    }
}