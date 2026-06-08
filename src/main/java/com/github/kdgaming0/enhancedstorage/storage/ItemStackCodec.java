package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * Bidirectional encoding/decoding between {@link ItemStack} and Base64-encoded compressed NBT.
 * Uses {@link ItemStack#CODEC} for full DataComponent-aware serialization.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {}

    public static @Nullable String encode(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
            Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            if (!(tag instanceof CompoundTag compound)) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(compound, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    public static ItemStack decode(@Nullable String base64, HolderLookup.Provider lookup) {
        if (base64 == null || base64.isBlank()) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            CompoundTag compound = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, compound).getOrThrow();
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static Optional<CompoundTag> decodeToTag(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) return Optional.empty();
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            return Optional.of(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
