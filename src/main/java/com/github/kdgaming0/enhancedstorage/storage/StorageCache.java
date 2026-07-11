package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the last-seen contents of every storage page in memory,
 * and can persist/restore that state to an NBT file so the overlay
 * data stay between restarts.
 */
public final class StorageCache {

    private static final StorageCache INSTANCE = new StorageCache();

    public static StorageCache getInstance() {
        return INSTANCE;
    }

    public record CachedPage(List<ItemStack> items, long capturedAt) {}

    private final Map<StorageKey, CachedPage> pages = new ConcurrentHashMap<>();
    private final Set<StorageKey> knownPages = ConcurrentHashMap.newKeySet();
    private boolean dirty = false;

    private StorageCache() {}

    // ------------------------------------------------------------------
    // In-memory API
    // ------------------------------------------------------------------

    public void put(StorageKey key, List<ItemStack> items) {
        pages.put(key, new CachedPage(List.copyOf(items), System.currentTimeMillis()));
        knownPages.add(key);
        dirty = true;
    }

    public Optional<CachedPage> get(StorageKey key) {
        return Optional.ofNullable(pages.get(key));
    }

    public Map<StorageKey, CachedPage> all() {
        return Collections.unmodifiableMap(pages);
    }

    public void replaceKnown(StorageKey.Type type, Set<StorageKey> keys) {
        knownPages.removeIf(k -> k.type() == type);
        knownPages.addAll(keys);
        dirty = true;
    }

    public void retainOnly(StorageKey.Type type, Set<StorageKey> keep) {
        boolean removed = pages.keySet().removeIf(k -> k.type() == type && !keep.contains(k));
        if (removed) {
            dirty = true;
        }
    }

    public Set<StorageKey> allKnown() {
        return Collections.unmodifiableSet(knownPages);
    }

    public void clear() {
        pages.clear();
        knownPages.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Disk persistence
    // ------------------------------------------------------------------

    private static Path cacheFile() {
        String profile = StorageProfile.getInstance().current().orElse("default");
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EnhancedStorage.MOD_ID)
                .resolve("profiles")
                .resolve(profile)
                .resolve("storage_cache.dat");
    }

    private static Optional<RegistryOps<Tag>> registryOps() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return Optional.empty();
        HolderLookup.Provider registries = client.level.registryAccess();
        return Optional.of(registries.createSerializationContext(NbtOps.INSTANCE));
    }

    /** Writes the whole cache to disk. */
    public void saveToDisk() {
        if (!dirty) return;
        Optional<RegistryOps<Tag>> opsOpt = registryOps();
        if (opsOpt.isEmpty()) return;
        RegistryOps<Tag> ops = opsOpt.get();

        CompoundTag root = new CompoundTag();

        ListTag known = new ListTag();
        for (StorageKey key : knownPages) {
            known.add(net.minecraft.nbt.StringTag.valueOf(key.id()));
        }
        root.put("known", known);

        pages.forEach((key, page) -> {
            ListTag list = new ListTag();
            for (ItemStack stack : page.items()) {
                ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack)
                        .resultOrPartial(err ->
                                EnhancedStorage.LOGGER.warn("Failed to encode stack in {}: {}", key.id(), err))
                        .ifPresent(list::add);
            }
            CompoundTag pageTag = new CompoundTag();
            pageTag.put("items", list);
            pageTag.putLong("captured", page.capturedAt());
            root.put(key.id(), pageTag);
        });

        try {
            Path file = cacheFile();
            Files.createDirectories(file.getParent());

            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            dirty = false;
            EnhancedStorage.LOGGER.debug("Saved storage cache ({} pages)", pages.size());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to save storage cache", e);
        }
    }

    /** Loads the cache file. Must be called once a world is joined */
    public void loadFromDisk() {
        Path file = cacheFile();
        if (!Files.exists(file)) return;

        Optional<RegistryOps<Tag>> opsOpt = registryOps();
        if (opsOpt.isEmpty()) return;
        RegistryOps<Tag> ops = opsOpt.get();

        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to read storage cache file", e);
            return;
        }

        pages.clear();
        knownPages.clear();

        root.getList("known").ifPresent(list -> {
            for (Tag tag : list) {
                if (tag instanceof StringTag(String value)) {
                    StorageKey.fromId(value)
                            .ifPresent(knownPages::add);
                }
            }
        });

        int loaded = 0;
        for (String id : root.keySet()) {
            if (id.equals("known")) {
                continue;
            }

            Optional<StorageKey> keyOpt = StorageKey.fromId(id);
            if (keyOpt.isEmpty()) continue;

            Optional<CompoundTag> pageTagOpt = root.getCompound(id);
            if (pageTagOpt.isEmpty()) continue;
            CompoundTag pageTag = pageTagOpt.get();

            ListTag list = pageTag.getList("items").orElse(new ListTag());
            List<ItemStack> items = new ArrayList<>(list.size());
            for (Tag tag : list) {
                items.add(ItemStack.OPTIONAL_CODEC.parse(ops, tag)
                        .resultOrPartial(err ->
                                EnhancedStorage.LOGGER.warn("Failed to decode stack in {}: {}", id, err))
                        .orElse(ItemStack.EMPTY));
            }

            long captured = pageTag.getLong("captured").orElse(0L);
            pages.put(keyOpt.get(), new CachedPage(List.copyOf(items), captured));
            knownPages.add(keyOpt.get());
            loaded++;
        }

        dirty = false;
        EnhancedStorage.LOGGER.info("Loaded storage cache: {} pages", loaded);
    }

    public void reloadForCurrentProfile() {
        pages.clear();
        knownPages.clear();
        dirty = false;
        loadFromDisk();
    }
}