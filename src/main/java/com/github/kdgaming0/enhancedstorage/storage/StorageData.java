/*
 * Based on code from Firmament:
 * https://github.com/FirmamentMC/Firmament
 *
 * This file contains substantial portions adapted from Firmament's
 * storage overlay implementation.
 *
 * Original code licensed under the GNU General Public License v3.0.
 *
 * Modifications:
 * - Translated from Kotlin to Java
 * - Modified for Enhanced Storage
 */

package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single in-memory source-of-truth cache for all known storage pages.
 * Thread-safe and sorted by page index.
 */
public final class StorageData {

    public static final StorageData INSTANCE = new StorageData();
    private final ConcurrentSkipListMap<StoragePage, StorageInventory> inventories = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<StoragePage, StorageInventory> riftInventories = new ConcurrentSkipListMap<>();
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

        StorageInventory updated = new StorageInventory(resolvedTitle, page, resolvedInv, resolvedIcon);
        if (existing != null && existing.contentEquals(updated)) {
            return;
        }
        map.put(page, updated);
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
    }

    /**
     * Monotonic counter bumped on every mutation. Lets consumers cache derived state
     * (e.g. search-match results) and recompute only when the underlying data changes.
     */
    public long getVersion() {
        return version.get();
    }

    /** Whether there are in-memory changes not yet written to disk. */
    public boolean isDirty() {
        return dirty;
    }

    /** Marks the in-memory state as matching the last successful save. */
    public void clearDirty() {
        dirty = false;
    }

    public record StorageInventory(
            String title,
            StoragePage page,
            @Nullable VirtualInventory inventory,
            @Nullable ItemStack icon) {

        /**
         * Content equality ignoring page identity: same title, same inventory contents, same icon.
         * The record's generated {@code equals} can't be used — it compares the inventory and icon
         * by reference, not by item content.
         */
        public boolean contentEquals(StorageInventory other) {
            if (other == null) return false;
            if (!Objects.equals(title, other.title)) return false;
            if ((inventory == null) != (other.inventory == null)) return false;
            if (inventory != null && !inventory.contentEquals(other.inventory)) return false;
            if ((icon == null) != (other.icon == null)) return false;
            return icon == null || ItemStack.matches(icon, other.icon);
        }
    }
}