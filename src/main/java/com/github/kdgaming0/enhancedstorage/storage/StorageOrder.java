package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageOrder {

    private static final StorageOrder INSTANCE = new StorageOrder();
    private final Map<StorageKey, Integer> positions = new ConcurrentHashMap<>();
    private boolean dirty = false;

    private StorageOrder() {
    }

    public static StorageOrder getInstance() {
        return INSTANCE;
    }

    private static Path orderFile() {
        String profile = StorageProfile.getInstance().current().orElse("default");
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EnhancedStorage.MOD_ID)
                .resolve("profiles")
                .resolve(profile)
                .resolve("storage_order.dat");
    }

    public Optional<Integer> get(StorageKey key) {
        return Optional.ofNullable(positions.get(key));
    }

    public boolean has(StorageKey key) {
        return positions.containsKey(key);
    }

    /**
     * null clears the custom position (revert to default flow).
     */
    public void set(StorageKey key, Integer position) {
        if (position == null) {
            clear(key);
            return;
        }
        Integer previous = positions.put(key, position);
        if (!position.equals(previous)) dirty = true;
    }

    public void clear(StorageKey key) {
        if (positions.remove(key) != null) dirty = true;
    }

    public Map<StorageKey, Integer> all() {
        return java.util.Collections.unmodifiableMap(positions);
    }

    public void saveToDisk() {
        if (!dirty) return;
        // Don't write until the skyblock profile id has been captured
        if (!StorageProfile.getInstance().isConfirmed()) {
            EnhancedStorage.LOGGER.debug("Holding back storage order save; profile not confirmed yet");
            return;
        }
        CompoundTag root = new CompoundTag();
        positions.forEach((key, pos) -> root.putInt(key.id(), pos));
        try {
            Path file = orderFile();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
            EnhancedStorage.LOGGER.debug("Saved storage order ({} entries)", positions.size());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to save storage order", e);
        }
    }

    public void loadFromDisk() {
        Path file = orderFile();
        if (!Files.exists(file)) return;
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to read storage order file", e);
            return;
        }
        positions.clear();
        for (String id : root.keySet()) {
            StorageKey.fromId(id).ifPresent(key ->
                    root.getInt(id).ifPresent(pos -> positions.put(key, pos)));
        }
        dirty = false;
        EnhancedStorage.LOGGER.info("Loaded storage order: {} entries", positions.size());
    }

    public void reloadForCurrentProfile() {
        positions.clear();
        dirty = false;
        loadFromDisk();
    }
}
