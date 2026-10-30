package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.Consumer;

/**
 * What the overlay's cards and buttons do. Only the card click and edit mode's own buttons differ between screens,
 * so {@link #create} fills in the rest.
 */
public record OverlayActions(
        Consumer<StorageKey> onCardClick,
        Consumer<String> sendCommand,
        Runnable openSettings,
        Runnable toggleEditMode,
        Runnable rebuild,
        EditActions edit) {

    public static OverlayActions create(Screen parent, StorageOverlayState state, Consumer<StorageKey> onCardClick,
                                        Runnable toggleEditMode, Runnable rebuild) {
        return create(parent, state, onCardClick, toggleEditMode, rebuild, EditActions.NONE);
    }

    public static OverlayActions create(Screen parent, StorageOverlayState state, Consumer<StorageKey> onCardClick,
                                        Runnable toggleEditMode, Runnable rebuild, EditActions edit) {
        return new OverlayActions(
                onCardClick,
                command -> sendCommand(state, command),
                () -> Minecraft.getInstance().setScreen(MidnightConfig.getScreen(parent, EnhancedStorage.MOD_ID)),
                toggleEditMode,
                rebuild,
                edit);
    }

    public static void sendCommand(StorageOverlayState state, String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        state.beginNavigation();
        player.connection.sendCommand(command);
    }

    /**
     * Edit mode's Reset and Share buttons and the Undo and Dismiss buttons of its notice. Only edit mode shows them.
     */
    public record EditActions(Runnable reset, Consumer<MouseButtonEvent> share, Runnable undo, Runnable dismissNotice) {
        static final EditActions NONE = new EditActions(() -> {
        }, event -> {
        }, () -> {
        }, () -> {
        });
    }
}