package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemSearch {

    private ItemSearch() {
    }

    public static boolean matches(ItemStack stack, String query) {
        if (query.isBlank()) return false;
        if (stack == null || stack.isEmpty()) return false;

        String cleanQuery = TextUtils.stripString(query);

        for (Component line : Screen.getTooltipFromItem(Minecraft.getInstance(), stack)) {
            if (TextUtils.stripText(line).contains(cleanQuery)) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyMatch(List<ItemStack> stacks, String query) {
        if (query.isBlank()) return false;
        return stacks.stream().anyMatch(stack -> matches(stack, query));
    }
}
