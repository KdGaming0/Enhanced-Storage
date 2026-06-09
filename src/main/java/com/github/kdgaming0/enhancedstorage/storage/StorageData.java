package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single in-memory source-of-truth cache for all known storage pages.
 * Thread-safe and sorted by page index.
 */
public final class StorageData {

    public static final StorageData INSTANCE = new StorageData();
    private final ConcurrentSkipListMap<StoragePage, StorageInventory> inventories = new ConcurrentSkipListMap<>();
    private final List<Runnable> dirtyListeners = new CopyOnWriteArrayList<>();
    private volatile boolean dirty = false;
    private StorageData() {
    }

    public void updateInventory(StoragePage page, String title, @Nullable VirtualInventory inventory) {
        updateInventory(page, title, inventory, null);
    }

    public void updateInventory(StoragePage page, String title,
                                @Nullable VirtualInventory inventory, @Nullable ItemStack icon) {
        StorageInventory existing = inventories.get(page);

        String resolvedTitle = (title != null && !title.isBlank()) ? title
                : (existing != null && existing.title() != null) ? existing.title()
                  : page.defaultName();
        VirtualInventory resolvedInv = inventory != null ? inventory
                : (existing != null ? existing.inventory() : null);
        ItemStack resolvedIcon = icon != null ? icon
                : (existing != null ? existing.icon() : null);

        inventories.put(page, new StorageInventory(resolvedTitle, page, resolvedInv, resolvedIcon));
        markDirty();
    }

    public boolean hasInventory(StoragePage page) {
        return inventories.containsKey(page);
    }

    public @Nullable StorageInventory getInventory(StoragePage page) {
        return inventories.get(page);
    }

    public void removeInventory(StoragePage page) {
        inventories.remove(page);
        markDirty();
    }

    public void clear() {
        inventories.clear();
        markDirty();
    }

    /**
     * Returns a read-only view of all cached inventories, sorted by page index.
     */
    public Map<StoragePage, StorageInventory> getInventories() {
        return Collections.unmodifiableMap(inventories);
    }

    public void markDirty() {
        dirty = true;
        for (Runnable listener : dirtyListeners) {
            listener.run();
        }
    }

    public void addDirtyListener(Runnable listener) {
        dirtyListeners.add(listener);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public record StorageInventory(
            String title,
            StoragePage page,
            @Nullable VirtualInventory inventory,
            @Nullable ItemStack icon) {
    }
}