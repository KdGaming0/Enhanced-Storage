package com.github.kdgaming0.enhancedstorage.storage;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single in-memory source-of-truth cache for all known storage pages.
 * Thread-safe and sorted.
 */
public final class StorageData {

    public static final StorageData INSTANCE = new StorageData();

    public record StorageInventory(String title, StoragePage page, @Nullable VirtualInventory inventory) {}

    private final ConcurrentSkipListMap<StoragePage, StorageInventory> inventories = new ConcurrentSkipListMap<>();
    private final List<Runnable> dirtyListeners = new CopyOnWriteArrayList<>();
    private volatile boolean dirty = false;

    private StorageData() {}

    public void updateInventory(StoragePage page, String title, @Nullable VirtualInventory inventory) {
        StorageInventory existing = inventories.get(page);
        String resolvedTitle = (title != null && !title.isBlank()) ? title
                : (existing != null && existing.title() != null) ? existing.title()
                : page.defaultName();
        VirtualInventory resolvedInventory = (inventory != null) ? inventory
                : (existing != null) ? existing.inventory()
                : null;
        inventories.put(page, new StorageInventory(resolvedTitle, page, resolvedInventory));
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

    public Map<StoragePage, StorageInventory> getInventories() {
        return inventories;
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
}
