package com.github.kdgaming0.enhancedstorage.gui.highlight;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ItemTotals {

    private static ItemStack countedTarget = ItemStack.EMPTY;
    private static @Nullable StorageKey countedLiveKey;
    private static boolean countedRift;
    private static int cachedCount;
    private static int total;
    private static @Nullable Screen totalScreen;
    private static boolean shownInTooltip;

    private ItemTotals() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (total <= 0 || stack != CopyHighlight.target() || Minecraft.getInstance().screen != totalScreen) return;
            lines.add(Component.literal("Total: " + total + "×").withStyle(ChatFormatting.GRAY));
            shownInTooltip = true;
        });
    }

    public static void update(boolean rift, @Nullable StorageKey liveKey, Iterable<ItemStack> liveStacks) {
        shownInTooltip = false;
        ItemStack target = CopyHighlight.target();
        if (!EnhancedStorageConfig.showItemTotals || target.isEmpty()) {
            clear();
            return;
        }
        if (target != countedTarget || rift != countedRift || !Objects.equals(liveKey, countedLiveKey)) {
            countedTarget = target;
            countedRift = rift;
            countedLiveKey = liveKey;
            cachedCount = countCached(target, rift, liveKey);
        }
        int count = cachedCount;
        for (ItemStack stack : liveStacks) {
            if (!stack.isEmpty() && CopyHighlight.sameItem(target, stack)) count += stack.getCount();
        }
        total = count;
        totalScreen = Minecraft.getInstance().screen;
    }

    public static void clear() {
        total = 0;
        countedTarget = ItemStack.EMPTY;
        totalScreen = null;
    }

    public static void extractBox(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (total <= 0 || shownInTooltip) return;
        graphics.setTooltipForNextFrame(font, Component.literal(total + "×"), mouseX, mouseY);
    }

    private static int countCached(ItemStack target, boolean rift, @Nullable StorageKey liveKey) {
        int count = 0;
        for (Map.Entry<StorageKey, PageCache.CachedPage> entry : PageCache.getInstance().all().entrySet()) {
            StorageKey key = entry.getKey();
            if (key.type() == StorageKey.Type.STORAGE_INDEX || rift != (key.type() == StorageKey.Type.RIFT)
                    || key.equals(liveKey)) {
                continue;
            }
            List<ItemStack> items = entry.getValue().items();
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && CopyHighlight.sameItem(target, stack)) count += stack.getCount();
            }
        }
        return count;
    }
}
