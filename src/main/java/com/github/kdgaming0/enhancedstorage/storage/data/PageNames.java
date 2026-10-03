package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * User-assigned custom names for storage pages.
 */
public final class PageNames extends ProfileScopedStore {

    // Cap maximum name length to avoid overflowing names in the UI.
    public static final int MAX_NAME_LENGTH = 48;

    private static final PageNames INSTANCE = new PageNames();

    private final Map<StorageKey, String> names = new ConcurrentHashMap<>();

    private PageNames() {
        super("storage_names.dat", 1);
    }

    public static PageNames getInstance() {
        return INSTANCE;
    }

    public Optional<String> get(StorageKey key) {
        return Optional.ofNullable(names.get(key));
    }

    /**
     * Sets the custom name. A null or blank name removes it (back to the default).
     * The value is trimmed and capped at {@link #MAX_NAME_LENGTH}.
     */
    public void set(StorageKey key, String name) {
        if (name == null || name.isBlank()) {
            clear(key);
            return;
        }

        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            int end = MAX_NAME_LENGTH;
            // Don't cut an emoji (surrogate pair) in half.
            if (Character.isHighSurrogate(trimmed.charAt(end - 1))) end--;
            trimmed = trimmed.substring(0, end).strip();
        }

        String previous = names.put(key, trimmed);
        if (!trimmed.equals(previous)) markDirty();
    }

    public void clear(StorageKey key) {
        if (names.remove(key) != null) markDirty();
    }

    public Map<StorageKey, String> all() {
        return Collections.unmodifiableMap(names);
    }

    @Override
    protected void writeTo(CompoundTag root) {
        names.forEach((key, name) -> root.putString(key.id(), name));
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        for (String id : root.keySet()) {
            StorageKey.fromId(id).ifPresent(key ->
                    root.getString(id).ifPresent(name -> {
                        if (!name.isBlank()) names.put(key, name);
                    }));
        }
        LOGGER.info("Loaded storage names: {} entries (file version {})", names.size(), fileVersion);
    }

    @Override
    protected void clearMemory() {
        names.clear();
    }
}