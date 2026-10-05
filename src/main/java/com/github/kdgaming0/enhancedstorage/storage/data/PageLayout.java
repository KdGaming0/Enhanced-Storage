package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * The player's arranged page order.
 */
public final class PageLayout extends ProfileScopedStore {

    private static final PageLayout INSTANCE = new PageLayout();
    private static final String PAGE_KEY = "page";
    private static final String SPACER_KEY = "spacer";
    private static final String SECTION_KEY = "section";
    private static final String NAME_KEY = "name";
    private static final String COLLAPSED_KEY = "collapsed";
    private static final String SECTION_END_KEY = "sections_end";
    private static final String SLOTS_KEY = "slots";
    private static final String STACK_KEY = "stack";
    private final Map<Context, List<LayoutEntry>> orders = new ConcurrentHashMap<>();

    private PageLayout() {
        super("storage_layout.dat", 1);
    }

    public static PageLayout getInstance() {
        return INSTANCE;
    }

    private static int insertionIndex(List<LayoutEntry> result, List<StorageKey> defaultOrder, int defaultIndex) {
        for (int j = defaultIndex - 1; j >= 0; j--) {
            int at = LayoutOps.indexOfKey(result, defaultOrder.get(j));
            if (at >= 0) return at + 1;
        }
        for (int j = defaultIndex + 1; j < defaultOrder.size(); j++) {
            int at = LayoutOps.indexOfKey(result, defaultOrder.get(j));
            if (at >= 0) return at;
        }
        return result.size();
    }

    /**
     * Applies the saved order to the pages that exist right now. Saved pages that no longer exist are
     * skipped; pages missing from the saved order go next to their default-order neighbour. Spacers are kept.
     *
     * @return empty when this context has no saved order
     */
    public Optional<List<LayoutEntry>> resolve(Context context, List<StorageKey> defaultOrder) {
        List<LayoutEntry> saved = orders.get(context);
        if (saved == null) return Optional.empty();

        Set<StorageKey> present = new HashSet<>(defaultOrder);
        List<LayoutEntry> result = new ArrayList<>();
        for (LayoutEntry entry : saved) {
            switch (entry) {
                case LayoutEntry.Page(StorageKey key) -> {
                    if (present.contains(key)) result.add(entry);
                }
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    List<StorageKey> kept = keys.stream().filter(present::contains).toList();
                    if (!kept.isEmpty()) result.add(LayoutOps.stackOf(kept));
                }
                default -> result.add(entry);
            }
        }

        // Walk in default order so consecutive new pages chain (#4 lands after #3, then #5 after #4).
        for (int i = 0; i < defaultOrder.size(); i++) {
            StorageKey key = defaultOrder.get(i);
            if (LayoutOps.indexOfKey(result, key) < 0) {
                result.add(insertionIndex(result, defaultOrder, i), new LayoutEntry.Page(key));
            }
        }
        return Optional.of(result);
    }

    public void setOrder(Context context, List<LayoutEntry> order) {
        List<LayoutEntry> copy = List.copyOf(order);
        if (!copy.equals(orders.put(context, copy))) markDirty();
    }

    public void clear(Context context) {
        if (orders.remove(context) != null) markDirty();
    }

    @Override
    protected void writeTo(CompoundTag root) {
        orders.forEach((context, order) -> {
            ListTag list = new ListTag();
            for (LayoutEntry entry : order) {
                CompoundTag tag = new CompoundTag();
                switch (entry) {
                    case LayoutEntry.Page(StorageKey key) -> tag.putString(PAGE_KEY, key.id());
                    case LayoutEntry.Stack(List<StorageKey> keys) -> {
                        ListTag pages = new ListTag();
                        for (StorageKey key : keys) pages.add(StringTag.valueOf(key.id()));
                        tag.put(STACK_KEY, pages);
                    }
                    case LayoutEntry.Spacer(int id, int slots) -> {
                        tag.putInt(SPACER_KEY, id);
                        tag.putInt(SLOTS_KEY, slots);
                    }
                    case LayoutEntry.Section(int id, String name, boolean collapsed) -> {
                        tag.putInt(SECTION_KEY, id);
                        tag.putString(NAME_KEY, name);
                        if (collapsed) tag.putBoolean(COLLAPSED_KEY, true);
                    }
                    case LayoutEntry.SectionEnd() -> tag.putBoolean(SECTION_END_KEY, true);
                }
                list.add(tag);
            }
            root.put(context.id, list);
        });
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        for (Context context : Context.values()) {
            root.getList(context.id).ifPresent(list -> {
                List<LayoutEntry> entries = new ArrayList<>();
                Set<LayoutEntry> seen = new HashSet<>();
                for (Tag tag : list) {
                    if (!(tag instanceof CompoundTag entry)) continue;
                    entry.getString(PAGE_KEY).flatMap(StorageKey::fromId)
                            .<LayoutEntry>map(LayoutEntry.Page::new)
                            .or(() -> readStack(entry))
                            .or(() -> entry.getInt(SPACER_KEY).map(id ->
                                    new LayoutEntry.Spacer(id, entry.getInt(SLOTS_KEY).orElse(LayoutEntry.Spacer.FILL))))
                            .or(() -> entry.getInt(SECTION_KEY).map(id -> new LayoutEntry.Section(id,
                                    entry.getString(NAME_KEY).orElse(LayoutEntry.Section.DEFAULT_NAME),
                                    entry.getBoolean(COLLAPSED_KEY).orElse(false))))
                            .or(() -> entry.getBoolean(SECTION_END_KEY).map(present -> new LayoutEntry.SectionEnd()))
                            .filter(e -> e instanceof LayoutEntry.SectionEnd || seen.add(e))
                            .ifPresent(entries::add);
                }
                orders.put(context, List.copyOf(LayoutOps.normalize(entries)));
            });
        }
        LOGGER.info("Loaded storage layout: {} contexts (file version {})", orders.size(), fileVersion);
    }

    private static Optional<LayoutEntry> readStack(CompoundTag entry) {
        return entry.getList(STACK_KEY).map(list -> {
            List<StorageKey> keys = new ArrayList<>();
            for (Tag tag : list) {
                tag.asString().flatMap(StorageKey::fromId).ifPresent(keys::add);
            }
            return keys.isEmpty() ? null : LayoutOps.stackOf(keys);
        });
    }

    @Override
    protected void clearMemory() {
        orders.clear();
    }

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
}