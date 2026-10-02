package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.Consumer;

/**
 * What the overlay's cards and buttons do. Only the card click differs between screens,
 * so {@link #create} fills in the rest.
 */
public record OverlayActions(
        Consumer<StorageKey> onCardClick,
        Consumer<String> sendCommand,
        Runnable openSettings,
        Runnable toggleEditMode,
        Runnable rebuild) {

    public static OverlayActions create(Screen parent, StorageOverlayState state, Consumer<StorageKey> onCardClick,
                                        Runnable toggleEditMode, Runnable rebuild) {
        return new OverlayActions(
                onCardClick,
                command -> sendCommand(state, command),
                () -> Minecraft.getInstance().setScreen(MidnightConfig.getScreen(parent, EnhancedStorage.MOD_ID)),
                toggleEditMode,
                rebuild);
    }

    public static void sendCommand(StorageOverlayState state, String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        state.beginNavigation();
        player.connection.sendCommand(command);
    }
}