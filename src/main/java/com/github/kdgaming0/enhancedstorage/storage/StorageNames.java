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

/**
 * Holds user-assigned custom names for storage pages and persists them to an
 * NBT file so they survive restarts. Kept deliberately separate from
 * {@link StorageCache} so that renaming never touches captured item data.
 *
 * <p>This is a purely cosmetic layer: the underlying {@link StorageKey} and its
 * {@link StorageKey#displayName() default name} are unchanged. Callers should
 * treat an absent entry as "use the default name".</p>
 */
public final class StorageNames {

    /**
     * Custom names are just short cosmetic strings; cap them to keep the UI sane.
     */
    public static final int MAX_NAME_LENGTH = 32;
    private static final StorageNames INSTANCE = new StorageNames();
    private final Map<StorageKey, String> names = new ConcurrentHashMap<>();
    private boolean dirty = false;
    private boolean loaded = false;

    private StorageNames() {
    }

    public static StorageNames getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // In-memory API
    // ------------------------------------------------------------------

    private static Path namesFile() {
        String profile = StorageProfile.getInstance().current().orElse("default");
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EnhancedStorage.MOD_ID)
                .resolve("profiles")
                .resolve(profile)
                .resolve("storage_names.dat");
    }

    /**
     * Returns the custom name for this key, if one has been set.
     */
    public Optional<String> get(StorageKey key) {
        return Optional.ofNullable(names.get(key));
    }

    /**
     * True if this key has a non-blank custom name.
     */
    public boolean has(StorageKey key) {
        String v = names.get(key);
        return v != null && !v.isBlank();
    }

    /**
     * Sets (or clears) the custom name for a key. Passing null or a blank
     * string removes the custom name, reverting the page to its default.
     * The value is trimmed and length-capped.
     */
    public void set(StorageKey key, String name) {
        if (name == null || name.isBlank()) {
            clear(key);
            return;
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        String previous = names.put(key, trimmed);
        if (!trimmed.equals(previous)) {
            dirty = true;
        }
    }

    /**
     * Removes the custom name for a key (revert to default).
     */
    public void clear(StorageKey key) {
        if (names.remove(key) != null) {
            dirty = true;
        }
    }

    // ------------------------------------------------------------------
    // Disk persistence
    // ------------------------------------------------------------------

    public Map<StorageKey, String> all() {
        return java.util.Collections.unmodifiableMap(names);
    }

    /**
     * Writes all custom names to disk. No-op if nothing changed.
     */
    public void saveToDisk() {
        if (!dirty) return;
        // Don't write until the skyblock profile id has been captured
        if (!StorageProfile.getInstance().isConfirmed()) return;

        CompoundTag root = new CompoundTag();
        names.forEach((key, name) -> root.putString(key.id(), name));

        try {
            Path file = namesFile();
            Files.createDirectories(file.getParent());

            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            dirty = false;
            EnhancedStorage.LOGGER.debug("Saved storage names ({} entries)", names.size());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to save storage names", e);
        }
    }

    /**
     * Loads custom names from disk. Safe to call more than once.
     */
    public void loadFromDisk() {
        Path file = namesFile();
        if (!Files.exists(file)) {
            loaded = true;
            return;
        }

        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to read storage names file", e);
            return;
        }

        names.clear();
        for (String id : root.keySet()) {
            StorageKey.fromId(id).ifPresent(key ->
                    root.getString(id).ifPresent(name -> {
                        if (!name.isBlank()) {
                            names.put(key, name);
                        }
                    }));
        }

        dirty = false;
        loaded = true;
        EnhancedStorage.LOGGER.info("Loaded storage names: {} entries", names.size());
    }

    public void reloadForCurrentProfile() {
        names.clear();
        dirty = false;
        loaded = false;
        loadFromDisk();
    }

    public boolean isLoaded() {
        return loaded;
    }
}