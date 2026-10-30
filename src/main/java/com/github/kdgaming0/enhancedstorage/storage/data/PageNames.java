package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

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

    /**
     * Cuts the name to {@link #MAX_NAME_LENGTH}, without splitting an emoji (surrogate pair) in half.
     */
    public static String capLength(String name) {
        if (name.length() <= MAX_NAME_LENGTH) return name;
        int end = MAX_NAME_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end);
    }

    public Optional<String> get(StorageKey key) {
        return Optional.ofNullable(names.get(key));
    }

    /**
     * The custom name, or the page's default name. Still holds color codes.
     */
    public String displayName(StorageKey key) {
        return get(key).orElse(key.displayName());
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

        String trimmed = capLength(name.strip()).strip();

        String previous = names.put(key, trimmed);
        if (!trimmed.equals(previous)) markDirty();
    }

    public void clear(StorageKey key) {
        if (names.remove(key) != null) markDirty();
    }

    public void clearIf(Predicate<StorageKey> filter) {
        if (names.keySet().removeIf(filter)) markDirty();
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