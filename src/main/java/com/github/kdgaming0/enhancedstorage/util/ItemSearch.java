package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class ItemSearch {

    private static final Map<ItemStack, String> SEARCH_TEXT_CACHE = new WeakHashMap<>();
    private static final Pattern PET_LEVEL_PREFIX = Pattern.compile("^\\[Lvl \\d+]\\s*");
    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]");
    private static final int[] ROMAN_VALUES = {10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"X", "IX", "V", "IV", "I"};

    private ItemSearch() {
    }

    public static boolean matches(ItemStack stack, String query) {
        if (query.isBlank()) return false;
        if (stack == null || stack.isEmpty()) return false;

        return searchText(stack).contains(TextUtils.stripString(query));
    }

    public static String searchTermFor(ItemStack stack) {
        String enchant = bookEnchantTerm(stack);
        if (enchant != null) return enchant;
        return PET_LEVEL_PREFIX.matcher(plainText(stack.getHoverName())).replaceFirst("");
    }

    private static @Nullable String bookEnchantTerm(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        CompoundTag source = tag.getCompound("ExtraAttributes").orElse(tag);
        if (!"ENCHANTED_BOOK".equals(source.getString("id").orElse(""))) return null;
        CompoundTag enchants = source.getCompound("enchantments").orElse(null);
        if (enchants == null || enchants.isEmpty()) return null;

        String rawKey = enchants.keySet().iterator().next();
        String key = rawKey.toLowerCase(Locale.ROOT);
        String level = toRoman(enchants.getInt(rawKey).orElse(0));
        String needle = lettersOnly(key.startsWith("ultimate_") ? key.substring("ultimate_".length()) : key);

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !needle.isEmpty()) {
            for (Component line : lore.lines()) {
                for (String part : plainText(line).split(",")) {
                    String text = part.trim();
                    if (lettersOnly(text).contains(needle) && text.endsWith(" " + level)) return text;
                }
            }
        }
        return (key.replace('_', ' ') + " " + level).trim();
    }

    private static String lettersOnly(String text) {
        return NON_LETTERS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String toRoman(int value) {
        if (value <= 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (value >= ROMAN_VALUES[i]) {
                out.append(ROMAN_SYMBOLS[i]);
                value -= ROMAN_VALUES[i];
            }
        }
        return out.toString();
    }

    private static String plainText(Component component) {
        return TextUtils.normalizeWhitespace(ChatFormatting.stripFormatting(component.getString()));
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
     * Always processes at least one stack per call, so the queue drains.
     */
    public static void warmUp(Deque<ItemStack> pending, long timeBudgetMillis) {
        long deadline = System.nanoTime() + timeBudgetMillis * 1_000_000L;
        while (!pending.isEmpty()) {
            ItemStack stack = pending.poll();
            if (stack != null && !stack.isEmpty()) {
                searchText(stack);
            }
            if (System.nanoTime() >= deadline) break;
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
