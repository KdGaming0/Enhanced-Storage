package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.compat.CatharsisCompat;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Watches container screens. When a screen belongs to a storage page, it snapshots
 * the menu after the server sends or changes its contents and hands the result to
 * the {@link ProfileSession}.
 */
public final class StorageCaptureHandler {

    private StorageCaptureHandler() {
    }

    @SuppressWarnings("unused")
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

            Optional<StorageKey> keyOpt = StorageKey.fromTitle(containerScreen.getTitle());
            if (keyOpt.isEmpty()) return;

            StorageKey key = keyOpt.get();
            AbstractContainerMenu menu = containerScreen.getMenu();

            if (key.type() == StorageKey.Type.RIFT) {
                discoverRiftPages(containerScreen.getTitle());
            }

            IndexScanner scanner = key.type() == StorageKey.Type.STORAGE_INDEX ? new IndexScanner() : null;

            ScreenEvents.afterTick(screen).register(s -> onTick(key, menu, scanner));
            ScreenEvents.remove(screen).register(s -> onClose(key, menu));
        });
    }

    private static void onTick(StorageKey key, AbstractContainerMenu menu, IndexScanner scanner) {
        // Until the server has sent the contents the slots are all air, which looks like an empty page.
        if (!ContainerContentTracker.hasReceived(menu)) return;

        boolean changed = ContainerContentTracker.consumeChanged(menu);
        if (changed) {
            capture(key, menu);
        }

        if (scanner != null && scanner.tick(menu, changed)) {
            StorageEvents.INDEX_CHANGED.fire();
        }
    }

    private static void onClose(StorageKey key, AbstractContainerMenu menu) {
        if (ContainerContentTracker.hasReceived(menu)) {
            capture(key, menu);
        }
        ProfileSession.getInstance().requestSave();
    }

    private static void capture(StorageKey key, AbstractContainerMenu menu) {
        int containerSlots = MenuLayout.containerSlots(menu);
        if (containerSlots <= MenuLayout.HEADER_SLOTS) return;

        boolean isIndex = key.type() == StorageKey.Type.STORAGE_INDEX;
        List<ItemStack> items = new ArrayList<>(containerSlots);

        for (int i = MenuLayout.HEADER_SLOTS; i < containerSlots; i++) {
            if (isIndex && !MenuLayout.isIndexIconSlot(i)) continue;

            ItemStack stack = menu.slots.get(i).getItem().copy();
            if (isIndex) bakeCatharsisModel(stack, i);
            items.add(stack);
        }

        ProfileSession.getInstance().capture(key, items);
    }

    /**
     * The storage index icons carry no SkyBlock id, so a Catharsis pack can only texture them from
     * the slot they sit in, which is gone by the time we draw the cached copy. Copy that model onto
     * the stack while the index is open so the cached icon keeps its texture.
     */
    private static void bakeCatharsisModel(ItemStack stack, int slotIndex) {
        if (stack.isEmpty() || !CatharsisCompat.isLoaded()) return;

        Identifier model = CatharsisCompat.getSlotModel(slotIndex);
        if (model != null) {
            stack.set(DataComponents.ITEM_MODEL, model);
        }
    }

    /** Called by the screen too: a Rift title tells us how many Rift pages exist. */
    public static void discoverRiftPages(Component title) {
        StorageKey.riftTotalPages(title).ifPresent(total -> {
            Set<StorageKey> allRift = new HashSet<>();
            for (int p = 1; p <= total; p++) {
                allRift.add(new StorageKey(StorageKey.Type.RIFT, p));
            }
            PageCache.getInstance().updateIndex(StorageKey.Type.RIFT, allRift);
        });
    }
}