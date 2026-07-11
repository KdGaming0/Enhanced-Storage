package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Watches container screens; when a screen belonging to a storage page is
 * open, it snapshots the container's contents every tick and writes the
 * final state into {@link StorageCache}.
 */
public final class StorageCaptureHandler {

    /** Player inventory (27) + hotbar (9) slots appended at the end of every chest menu. */
    private static final int PLAYER_SLOT_COUNT = 36;

    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("profile id:\\s*([0-9a-f-]{36})");

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
                StorageNames.getInstance().saveToDisk();
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> {
                    // Use last session's profile as the optimistic guess until we know for sure what skyblock profile just loaded in
                    StorageProfile.getInstance().adoptLastKnownProfile();
                    // When we then know the correct profile, we reload it if our guess is wrong
                    StorageCache.getInstance().reloadForCurrentProfile();
                    StorageNames.getInstance().reloadForCurrentProfile();
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StorageCache.getInstance().saveToDisk();
            StorageNames.getInstance().saveToDisk();
        });

        // Detect the real skyblock profile id from chat, which arrives a few seconds after JOIN.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            Matcher m = PROFILE_ID_PATTERN.matcher(TextUtils.stripText(message));
            if (!m.find()) return;

            String newProfileId = m.group(1);
            StorageProfile profile = StorageProfile.getInstance();

            // Did the profile not change? Do nothing.
            if (profile.current().filter(newProfileId::equals).isPresent()) {
                return;
            }

            // Different profile, save the current one
            StorageCache.getInstance().saveToDisk();
            StorageNames.getInstance().saveToDisk();

            // Change to the new profile
            profile.onProfileIdSeen(newProfileId);
        });
    }

    private static void capture(StorageKey key, AbstractContainerMenu menu) {
        int containerSlots = menu.slots.size() - PLAYER_SLOT_COUNT;
        if (containerSlots <= 9) return;

        List<ItemStack> items = new ArrayList<>(containerSlots);
        for (int i = 9; i < containerSlots; i++) {
            if (!shouldCaptureSlot(key, i)) continue;

            ItemStack stack = menu.slots.get(i).getItem();
            items.add(stack.copy());
        }

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

        Set<StorageKey> foundEnder = found.stream()
                .filter(k -> k.type() == StorageKey.Type.ENDER_CHEST)
                .collect(Collectors.toSet());
        Set<StorageKey> foundBackpack = found.stream()
                .filter(k -> k.type() == StorageKey.Type.BACKPACK)
                .collect(Collectors.toSet());

        StorageCache cache = StorageCache.getInstance();
        cache.replaceKnown(StorageKey.Type.ENDER_CHEST, foundEnder);
        cache.replaceKnown(StorageKey.Type.BACKPACK, foundBackpack);

        cache.retainOnly(StorageKey.Type.ENDER_CHEST, foundEnder);
        cache.retainOnly(StorageKey.Type.BACKPACK, foundBackpack);
    }
}