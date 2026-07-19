package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class ItemSearch {

    private static final Map<ItemStack, String> SEARCH_TEXT_CACHE = new WeakHashMap<>();

    private ItemSearch() {
    }

    public static boolean matches(ItemStack stack, String query) {
        if (query.isBlank()) return false;
        if (stack == null || stack.isEmpty()) return false;

        return searchText(stack).contains(TextUtils.stripString(query));
    }

    public static boolean anyMatch(List<ItemStack> stacks, String query) {
        if (query.isBlank()) return false;

        String cleanQuery = TextUtils.stripString(query);
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (searchText(stack).contains(cleanQuery)) return true;
        }
        return false;
    }

    /**
     * Precomputes search text for pending stacks within roughly {@code timeBudgetMillis},
     * so the first search execution doesn't have to generate every tooltip at once.
     * Always processes a small minimum per call to guarantee the queue drains.
     */
    public static void warmUp(Deque<ItemStack> pending, long timeBudgetMillis) {
        long start = Util.getMillis();
        int processed = 0;
        while (!pending.isEmpty()) {
            ItemStack stack = pending.poll();
            if (stack != null && !stack.isEmpty()) {
                searchText(stack);
            }
            processed++;
            if (processed >= 10 && Util.getMillis() - start >= timeBudgetMillis) break;
        }
    }

    private static String searchText(ItemStack stack) {
        return SEARCH_TEXT_CACHE.computeIfAbsent(stack, s -> {
            StringBuilder text = new StringBuilder();
            text.append(TextUtils.stripText(s.getHoverName())).append('\n');
            ItemLore lore = s.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    text.append(TextUtils.stripText(line)).append('\n');
                }
            }
            return text.toString();
        });
    }
}
