package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Highlights every other stack of the hovered item, across cached pages, the live page and the inventory.
 */
public final class CopyHighlight {

    private static final int ITEM_SIZE = 16;
    private static final int COLOR = 0x8033CCFF;
    private static final Identity NONE = new Identity("", null);
    private static final Map<ItemStack, Identity> IDENTITIES = new WeakHashMap<>();

    private static KeyMapping key;
    private static ItemStack target = ItemStack.EMPTY;
    private static boolean locked;
    private static boolean tooltipsHidden;

    private CopyHighlight() {
    }

    private record Identity(String id, @Nullable Object variant) {
    }

    public static void register() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.enhanced_storage.highlight_copies",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, KeyMapping.Category.INVENTORY));
    }

    public static void setTarget(ItemStack hovered) {
        boolean holdKey = EnhancedStorageConfig.highlightCopies == EnhancedStorageConfig.HighlightCopiesMode.HOLD_KEY;
        boolean active = switch (EnhancedStorageConfig.highlightCopies) {
            case OFF -> false;
            case ALWAYS -> true;
            case HOLD_KEY -> isKeyHeld();
        };
        tooltipsHidden = holdKey && active;
        if (!active) {
            target = ItemStack.EMPTY;
            locked = false;
            return;
        }
        if (locked) return;
        target = hovered;
        locked = holdKey && EnhancedStorageConfig.hidePagesWithoutCopies && !hovered.isEmpty();
    }

    public static ItemStack filterTarget() {
        return locked ? target : ItemStack.EMPTY;
    }

    public static boolean hidesTooltips() {
        return tooltipsHidden;
    }

    public static boolean isCopy(ItemStack stack) {
        return !target.isEmpty() && !stack.isEmpty() && stack != target && sameItem(target, stack);
    }

    public static void draw(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, COLOR);
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        Identity identityA = identity(a);
        Identity identityB = identity(b);
        if (identityA != NONE || identityB != NONE) return identityA.equals(identityB);
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean isKeyHeld() {
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue());
    }

    private static Identity identity(ItemStack stack) {
        return IDENTITIES.computeIfAbsent(stack, s -> {
            CustomData data = s.get(DataComponents.CUSTOM_DATA);
            if (data == null) return NONE;
            CompoundTag tag = data.copyTag();
            String id = tag.getString("id").orElse("");
            return id.isEmpty() ? NONE : new Identity(id, variant(id, tag));
        });
    }

    private static @Nullable Object variant(String id, CompoundTag tag) {
        return switch (id) {
            case "PET" -> tag.getString("petInfo").map(CopyHighlight::petVariant).orElse(null);
            case "ENCHANTED_BOOK" -> tag.getCompound("enchantments").orElse(null);
            case "RUNE" -> tag.getCompound("runes").orElse(null);
            default -> null;
        };
    }

    private static String petVariant(String petInfo) {
        try {
            JsonObject json = JsonParser.parseString(petInfo).getAsJsonObject();
            return json.get("type").getAsString() + ":" + json.get("tier").getAsString();
        } catch (RuntimeException e) {
            return petInfo;
        }
    }
}