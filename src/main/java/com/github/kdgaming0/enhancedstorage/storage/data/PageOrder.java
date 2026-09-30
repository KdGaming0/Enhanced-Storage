package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

public final class PageOrder extends ProfileScopedStore {

    private static final PageOrder INSTANCE = new PageOrder();

    private final Map<StorageKey, Integer> positions = new ConcurrentHashMap<>();

    private PageOrder() {
        super("storage_order.dat", 1);
    }

    public static PageOrder getInstance() {
        return INSTANCE;
    }

    public Optional<Integer> get(StorageKey key) {
        return Optional.ofNullable(positions.get(key));
    }

    public boolean has(StorageKey key) {
        return positions.containsKey(key);
    }

    public void set(StorageKey key, Integer position) {
        if (position == null) {
            clear(key);
            return;
        }
        Integer previous = positions.put(key, position);
        if (!position.equals(previous)) markDirty();
    }

    public void clear(StorageKey key) {
        if (positions.remove(key) != null) markDirty();
    }

    public Map<StorageKey, Integer> all() {
        return Collections.unmodifiableMap(positions);
    }

    @Override
    protected void writeTo(CompoundTag root) {
        positions.forEach((key, pos) -> root.putInt(key.id(), pos));
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        for (String id : root.keySet()) {
            StorageKey.fromId(id).ifPresent(key ->
                    root.getInt(id).ifPresent(pos -> positions.put(key, pos)));
        }
        LOGGER.info("Loaded storage order: {} entries (file version {})", positions.size(), fileVersion);
    }

    @Override
    protected void clearMemory() {
        positions.clear();
    }
}