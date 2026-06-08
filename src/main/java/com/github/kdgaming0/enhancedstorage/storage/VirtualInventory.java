package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a storage page's item grid.
 */
public final class VirtualInventory {

    public static final int MIN_SIZE = 9;
    public static final int MAX_SIZE = 54;
    public static final int COLUMNS = 9;

    private final List<ItemStack> stacks;
    private final int rows;

    public VirtualInventory(@NotNull List<ItemStack> stacks) {
        int size = stacks.size();
        if (size < MIN_SIZE) size = MIN_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        // Round up to nearest row
        int rows = (size + COLUMNS - 1) / COLUMNS;
        size = rows * COLUMNS;

        List<ItemStack> copy = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (i < stacks.size()) {
                ItemStack s = stacks.get(i);
                copy.add(s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy());
            } else {
                copy.add(ItemStack.EMPTY);
            }
        }
        this.stacks = Collections.unmodifiableList(copy);
        this.rows = size / COLUMNS;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return stacks.size();
    }

    public ItemStack get(int index) {
        return stacks.get(index);
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    public byte[] serializeToBytes(@Nullable HolderLookup.Provider provider) {
        try {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            var ops = provider != null
                    ? provider.createSerializationContext(NbtOps.INSTANCE)
                    : NbtOps.INSTANCE;
            for (ItemStack stack : stacks) {
                CompoundTag tag = new CompoundTag();
                if (!stack.isEmpty()) {
                    Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                    tag.put("Item", encoded);
                }
                list.add(tag);
            }
            root.put("Inventory", list);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return out.toByteArray();
        } catch (Exception e) {
            EnhancedStorage.LOGGER.error("Failed to serialize VirtualInventory", e);
            return new byte[0];
        }
    }

    public static VirtualInventory empty(int rows) {
        int size = Math.clamp(rows, 1, 6) * COLUMNS;
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(ItemStack.EMPTY);
        return new VirtualInventory(list);
    }

    public static VirtualInventory deserialize(byte[] data, HolderLookup.Provider lookup) {
        if (data == null || data.length == 0) return empty(5);
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag list = root.getListOrEmpty("Inventory");
            List<ItemStack> stacks = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompoundOrEmpty(i);
                if (tag.contains("Item")) {
                    var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
                    ItemStack stack = ItemStack.CODEC.parse(ops, tag.get("Item")).getOrThrow();
                    stacks.add(stack);
                } else {
                    stacks.add(ItemStack.EMPTY);
                }
            }
            return new VirtualInventory(stacks);
        } catch (Exception e) {
            return empty(5);
        }
    }
}
