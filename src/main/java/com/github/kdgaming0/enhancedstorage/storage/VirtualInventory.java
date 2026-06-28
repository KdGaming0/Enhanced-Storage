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

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
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

    private volatile byte @Nullable [] serializedSnapshot;

    public VirtualInventory(@NotNull List<ItemStack> stacks) {
        int size = Math.clamp(stacks.size(), MIN_SIZE, MAX_SIZE);
        int rows = (size + COLUMNS - 1) / COLUMNS;
        size = rows * COLUMNS;

        List<ItemStack> copy = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack s = i < stacks.size() ? stacks.get(i) : null;
            copy.add(s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        this.stacks = Collections.unmodifiableList(copy);
        this.rows = rows;
    }

    /**
     * Builds a snapshot from freshly captured container stacks and eagerly serializes it while the
     * given registry context is still valid, so the save path never has to re-encode against a
     * registry that may have changed (see {@link #serializedSnapshot}).
     */
    public static VirtualInventory capture(@NotNull List<ItemStack> stacks, @Nullable HolderLookup.Provider provider) {
        VirtualInventory inv = new VirtualInventory(stacks);
        inv.serializeToBytes(provider);
        return inv;
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
            var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
            List<ItemStack> stacks = new ArrayList<>(list.size());
            int failed = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompoundOrEmpty(i);
                if (tag.contains("Item")) {
                    try {
                        stacks.add(ItemStack.CODEC.parse(ops, tag.get("Item")).getOrThrow());
                    } catch (RuntimeException e) {
                        stacks.add(ItemStack.EMPTY);
                        failed++;
                    }
                } else {
                    stacks.add(ItemStack.EMPTY);
                }
            }
            if (failed > 0) {
                EnhancedStorage.LOGGER.warn("Skipped {} unreadable item(s) while loading a storage page", failed);
            }
            VirtualInventory inv = new VirtualInventory(stacks);
            inv.serializedSnapshot = data;
            return inv;
        } catch (Exception e) {
            EnhancedStorage.LOGGER.warn("Failed to deserialize VirtualInventory, returning empty", e);
            return empty(5);
        }
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

    /**
     * Content equality against another snapshot: same slot count and every slot the same item,
     * count, and components ({@link ItemStack#matches}). Lets callers detect whether a re-captured
     * page actually changed, so unchanged re-opens don't dirty the cache.
     */
    public boolean contentEquals(@Nullable VirtualInventory other) {
        if (other == null) return false;
        if (other.stacks.size() != stacks.size()) return false;
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemStack.matches(stacks.get(i), other.stacks.get(i))) return false;
        }
        return true;
    }

    /**
     * Returns the page's NBT, encoding it on first use and caching the result. Once cached (here or
     * at {@link #capture}/{@link #deserialize}) the bytes are reused verbatim and {@code provider}
     * is ignored — that is what keeps a later save safe from a changed registry.
     */
    public byte[] serializeToBytes(@Nullable HolderLookup.Provider provider) {
        byte[] cached = serializedSnapshot;
        if (cached != null) return cached;

        byte[] bytes = encode(provider);
        if (bytes.length > 0) serializedSnapshot = bytes;
        return bytes;
    }

    private byte[] encode(@Nullable HolderLookup.Provider provider) {
        try {
            var ops = provider != null
                    ? provider.createSerializationContext(NbtOps.INSTANCE)
                    : NbtOps.INSTANCE;
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            int failed = 0;
            for (ItemStack stack : stacks) {
                CompoundTag tag = new CompoundTag();
                if (!stack.isEmpty()) {
                    try {
                        Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        tag.put("Item", encoded);
                    } catch (RuntimeException e) {
                        failed++;
                    }
                }
                list.add(tag);
            }
            if (failed > 0) {
                EnhancedStorage.LOGGER.warn("Dropped {} unserializable item(s) while saving a storage page", failed);
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
}
