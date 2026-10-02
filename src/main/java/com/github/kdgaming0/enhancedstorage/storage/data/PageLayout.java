package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * The player's arranged page order.
 */
public final class PageLayout extends ProfileScopedStore {

    public enum Context {
        STORAGE("storage"),
        RIFT("rift");

        private final String id;

        Context(String id) {
            this.id = id;
        }

        public static Context of(boolean rift) {
            return rift ? RIFT : STORAGE;
        }
    }

    private static final PageLayout INSTANCE = new PageLayout();
    private static final String PAGE_KEY = "page";

    private final Map<Context, List<StorageKey>> orders = new ConcurrentHashMap<>();

    private PageLayout() {
        super("storage_layout.dat", 1);
    }

    public static PageLayout getInstance() {
        return INSTANCE;
    }

    public boolean hasOrder(Context context) {
        return orders.containsKey(context);
    }

    /**
     * Applies the saved order to the pages that exist right now. Saved pages that no longer exist are
     * skipped; pages missing from the saved order go next to their default-order neighbour.
     *
     * @return empty when this context has no saved order
     */
    public Optional<List<StorageKey>> resolve(Context context, List<StorageKey> defaultOrder) {
        List<StorageKey> saved = orders.get(context);
        if (saved == null) return Optional.empty();

        Set<StorageKey> present = new HashSet<>(defaultOrder);
        List<StorageKey> result = new ArrayList<>();
        for (StorageKey key : saved) {
            if (present.contains(key)) result.add(key);
        }

        // Walk in default order so consecutive new pages chain (#4 lands after #3, then #5 after #4).
        for (int i = 0; i < defaultOrder.size(); i++) {
            StorageKey key = defaultOrder.get(i);
            if (!result.contains(key)) {
                result.add(insertionIndex(result, defaultOrder, i), key);
            }
        }
        return Optional.of(result);
    }

    private static int insertionIndex(List<StorageKey> result, List<StorageKey> defaultOrder, int defaultIndex) {
        for (int j = defaultIndex - 1; j >= 0; j--) {
            int at = result.indexOf(defaultOrder.get(j));
            if (at >= 0) return at + 1;
        }
        for (int j = defaultIndex + 1; j < defaultOrder.size(); j++) {
            int at = result.indexOf(defaultOrder.get(j));
            if (at >= 0) return at;
        }
        return result.size();
    }

    public void setOrder(Context context, List<StorageKey> order) {
        List<StorageKey> copy = List.copyOf(order);
        if (!copy.equals(orders.put(context, copy))) markDirty();
    }

    public void clear(Context context) {
        if (orders.remove(context) != null) markDirty();
    }

    @Override
    protected void writeTo(CompoundTag root) {
        orders.forEach((context, order) -> {
            ListTag list = new ListTag();
            for (StorageKey key : order) {
                CompoundTag entry = new CompoundTag();
                entry.putString(PAGE_KEY, key.id());
                list.add(entry);
            }
            root.put(context.id, list);
        });
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        for (Context context : Context.values()) {
            root.getList(context.id).ifPresent(list -> {
                Set<StorageKey> keys = new LinkedHashSet<>();
                for (Tag tag : list) {
                    if (tag instanceof CompoundTag entry) {
                        entry.getString(PAGE_KEY).flatMap(StorageKey::fromId).ifPresent(keys::add);
                    }
                }
                orders.put(context, List.copyOf(keys));
            });
        }
        LOGGER.info("Loaded storage layout: {} contexts (file version {})", orders.size(), fileVersion);
    }

    @Override
    protected void clearMemory() {
        orders.clear();
    }
}