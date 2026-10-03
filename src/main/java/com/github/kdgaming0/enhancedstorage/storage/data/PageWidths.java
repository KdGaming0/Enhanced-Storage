package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * Per-page slot widths chosen in edit mode. Pages without an entry use the global Page Width setting.
 */
public final class PageWidths extends ProfileScopedStore {

    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 18;

    private static final PageWidths INSTANCE = new PageWidths();

    private final Map<StorageKey, Integer> widths = new ConcurrentHashMap<>();

    private PageWidths() {
        super("storage_widths.dat", 1);
    }

    public static PageWidths getInstance() {
        return INSTANCE;
    }

    public Optional<Integer> get(StorageKey key) {
        return Optional.ofNullable(widths.get(key));
    }

    public void set(StorageKey key, int width) {
        int clamped = Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
        Integer previous = widths.put(key, clamped);
        if (previous == null || previous != clamped) markDirty();
    }

    public void clear(StorageKey key) {
        if (widths.remove(key) != null) markDirty();
    }

    public Map<StorageKey, Integer> all() {
        return Collections.unmodifiableMap(widths);
    }

    @Override
    protected void writeTo(CompoundTag root) {
        widths.forEach((key, width) -> root.putInt(key.id(), width));
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        for (String id : root.keySet()) {
            StorageKey.fromId(id).ifPresent(key ->
                    root.getInt(id).ifPresent(width -> widths.put(key, Math.clamp(width, MIN_WIDTH, MAX_WIDTH))));
        }
        LOGGER.info("Loaded storage widths: {} entries (file version {})", widths.size(), fileVersion);
    }

    @Override
    protected void clearMemory() {
        widths.clear();
    }
}