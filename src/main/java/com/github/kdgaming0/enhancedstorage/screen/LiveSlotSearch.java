package com.github.kdgaming0.enhancedstorage.screen;

import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Search results for live container slots. A slot is only re-evaluated when its stack or the
 * query changes, since slots are checked every frame.
 */
final class LiveSlotSearch {

    private final Map<Integer, Entry> entries = new HashMap<>();
    private String query = "";

    boolean matches(Slot slot, String currentQuery) {
        if (!currentQuery.equals(query)) {
            entries.clear();
            query = currentQuery;
        }

        ItemStack stack = slot.getItem();
        Entry entry = entries.get(slot.index);
        if (entry == null || !ItemStack.matches(entry.stack(), stack)) {
            entry = new Entry(stack.copy(), ItemSearch.matches(stack, currentQuery));
            entries.put(slot.index, entry);
        }
        return entry.match();
    }

    private record Entry(ItemStack stack, boolean match) {
    }
}