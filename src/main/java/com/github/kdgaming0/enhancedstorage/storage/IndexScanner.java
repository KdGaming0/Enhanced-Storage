package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the storage index menu and keeps {@link PageCache}'s list of pages up to date.
 */
final class IndexScanner {

    // The index fills in over several packets; pruning before it settles would drop pages that haven't arrived yet.
    private static final int STABLE_TICKS_BEFORE_PRUNE = 10;

    private Set<StorageKey> lastFound = Set.of();
    private int stableTicks;
    private boolean pruned;

    /**
     * Call once per tick while the index is open.
     *
     * @param menuChanged true if the menu contents changed since the last tick
     * @return true if the cache's list of pages changed
     */
    boolean tick(AbstractContainerMenu menu, boolean menuChanged) {
        PageCache cache = PageCache.getInstance();
        boolean changed = false;

        if (menuChanged) {
            Set<StorageKey> found = scan(menu);
            if (!found.isEmpty() && !found.equals(lastFound)) {
                lastFound = found;
                stableTicks = 0;
                pruned = false;
                changed = cache.addKnown(found);
            }
        }

        if (!pruned && !lastFound.isEmpty() && ++stableTicks >= STABLE_TICKS_BEFORE_PRUNE) {
            pruned = true;
            changed |= prune(cache, lastFound);
        }
        return changed;
    }

    /** Makes the cache's ender chest and backpack lists match what the index shows. */
    private static boolean prune(PageCache cache, Set<StorageKey> found) {
        boolean changed = cache.updateIndex(StorageKey.Type.ENDER_CHEST, ofType(found, StorageKey.Type.ENDER_CHEST));
        changed |= cache.updateIndex(StorageKey.Type.BACKPACK, ofType(found, StorageKey.Type.BACKPACK));
        return changed;
    }

    private static Set<StorageKey> ofType(Set<StorageKey> keys, StorageKey.Type type) {
        return keys.stream().filter(k -> k.type() == type).collect(Collectors.toSet());
    }

    private static Set<StorageKey> scan(AbstractContainerMenu menu) {
        Set<StorageKey> found = new HashSet<>();
        int containerSlots = MenuLayout.containerSlots(menu);

        for (int i = 0; i < containerSlots; i++) {
            if (!MenuLayout.isIndexIconSlot(i)) continue;

            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            StorageKey.fromIndexItem(stack.getHoverName()).ifPresent(found::add);
        }
        return found;
    }
}