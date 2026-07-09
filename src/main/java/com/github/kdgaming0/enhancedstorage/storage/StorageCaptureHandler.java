package com.github.kdgaming0.enhancedstorage.storage;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Watches container screens; when a screen belonging to a storage page is
 * open, it snapshots the container's contents every tick and writes the
 * final state into {@link StorageCache}.
 */
public final class StorageCaptureHandler {

    /** Player inventory (27) + hotbar (9) slots appended at the end of every chest menu. */
    private static final int PLAYER_SLOT_COUNT = 36;

    private StorageCaptureHandler() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            Optional<StorageKey> keyOpt = StorageKey.fromTitle(containerScreen.getTitle());
            if (keyOpt.isEmpty()) return;
            StorageKey key = keyOpt.get();

            ScreenEvents.afterTick(screen).register(s -> {
                capture(key, containerScreen.getMenu());
                if (key.type() == StorageKey.Type.STORAGE_INDEX) {
                    captureIndex(containerScreen.getMenu());
                }
            });

            ScreenEvents.remove(screen).register(s -> {
                capture(key, containerScreen.getMenu());
                StorageCache.getInstance().saveToDisk();
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> StorageCache.getInstance().loadFromDisk()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                StorageCache.getInstance().saveToDisk());
    }

    private static void capture(StorageKey key, AbstractContainerMenu menu) {
        int containerSlots = menu.slots.size() - PLAYER_SLOT_COUNT;
        if (containerSlots <= 9) return;

        List<ItemStack> items = new ArrayList<>(containerSlots);
        boolean anyItem = false;
        for (int i = 9; i < containerSlots; i++) {
            if (!shouldCaptureSlot(key, i)) continue;

            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty()) anyItem = true;
            items.add(stack.copy());
        }

        if (!anyItem) return;
        StorageCache.getInstance().put(key, items);
    }

    private static boolean shouldCaptureSlot(StorageKey key, int slotIndex) {
        if (key.type() != StorageKey.Type.STORAGE_INDEX) return true;
        int row = slotIndex / 9;
        return row == 1 || row == 3 || row == 4;
    }

    private static void captureIndex(AbstractContainerMenu menu) {
        int containerSlots = menu.slots.size() - PLAYER_SLOT_COUNT;
        Set<StorageKey> found = new HashSet<>();

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            StorageKey.fromIndexItem(stack.getHoverName()).ifPresent(found::add);
        }

        if (found.isEmpty()) return;

        StorageCache.getInstance().replaceKnown(StorageKey.Type.ENDER_CHEST,
                found.stream().filter(k -> k.type() == StorageKey.Type.ENDER_CHEST).collect(Collectors.toSet()));
        StorageCache.getInstance().replaceKnown(StorageKey.Type.BACKPACK,
                found.stream().filter(k -> k.type() == StorageKey.Type.BACKPACK).collect(Collectors.toSet()));
    }
}