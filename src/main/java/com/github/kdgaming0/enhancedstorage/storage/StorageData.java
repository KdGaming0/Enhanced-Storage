package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single in-memory source-of-truth cache for all known storage pages.
 * Thread-safe and sorted by page index.
 */
public final class StorageData {

    public static final StorageData INSTANCE = new StorageData();
    // MAIN (Ender Chest / Backpack) and RIFT pages live in separate maps so neither system's
    // pages ever leak into the other's overlay. The active map is chosen by the page's type.
    private final ConcurrentSkipListMap<StoragePage, StorageInventory> inventories = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<StoragePage, StorageInventory> riftInventories = new ConcurrentSkipListMap<>();
    private final List<Runnable> dirtyListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong version = new AtomicLong();
    private volatile boolean dirty = false;

    private StorageData() {
    }

    private ConcurrentSkipListMap<StoragePage, StorageInventory> mapFor(StoragePage page) {
        return page.type() == StorageType.RIFT ? riftInventories : inventories;
    }

    public void updateInventory(StoragePage page, String title, @Nullable VirtualInventory inventory) {
        updateInventory(page, title, inventory, null);
    }

    public void updateInventory(StoragePage page, String title,
                                @Nullable VirtualInventory inventory, @Nullable ItemStack icon) {
        ConcurrentSkipListMap<StoragePage, StorageInventory> map = mapFor(page);
        StorageInventory existing = map.get(page);

        String resolvedTitle = (title != null && !title.isBlank()) ? title
                : (existing != null && existing.title() != null) ? existing.title()
                  : page.defaultName();
        VirtualInventory resolvedInv = inventory != null ? inventory
                : (existing != null ? existing.inventory() : null);
        ItemStack resolvedIcon = icon != null ? icon
                : (existing != null ? existing.icon() : null);

        map.put(page, new StorageInventory(resolvedTitle, page, resolvedInv, resolvedIcon));
        markDirty();
    }

    public boolean hasInventory(StoragePage page) {
        return mapFor(page).containsKey(page);
    }

    public @Nullable StorageInventory getInventory(StoragePage page) {
        return mapFor(page).get(page);
    }

    public void clear() {
        inventories.clear();
        riftInventories.clear();
        markDirty();
    }

    /**
     * Returns a read-only view of the MAIN (Ender Chest / Backpack) inventories, sorted by page.
     * Rift pages are intentionally excluded so they never appear in the regular storage overlay.
     */
    public Map<StoragePage, StorageInventory> getInventories() {
        return Collections.unmodifiableMap(inventories);
    }

    /**
     * Returns a read-only view of the RIFT inventories, sorted by page.
     */
    public Map<StoragePage, StorageInventory> getRiftInventories() {
        return Collections.unmodifiableMap(riftInventories);
    }

    public void markDirty() {
        dirty = true;
        version.incrementAndGet();
        for (Runnable listener : dirtyListeners) {
            listener.run();
        }
    }

    /**
     * Monotonic counter bumped on every mutation. Lets consumers cache derived state
     * (e.g. search-match results) and recompute only when the underlying data changes.
     */
    public long getVersion() {
        return version.get();
    }

    public record StorageInventory(
            String title,
            StoragePage page,
            @Nullable VirtualInventory inventory,
            @Nullable ItemStack icon) {
    }
}