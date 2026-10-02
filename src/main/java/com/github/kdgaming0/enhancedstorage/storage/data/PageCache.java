package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * The last-seen contents of every storage page, plus the list of pages that exist.
 */
public final class PageCache extends ProfileScopedStore {

    private static final PageCache INSTANCE = new PageCache();

    private static final String KNOWN_KEY = "known";
    private static final String PAGES_KEY = "pages";
    private static final String ITEMS_KEY = "items";
    private static final String CAPTURED_KEY = "captured";

    private final Map<StorageKey, CachedPage> pages = new ConcurrentHashMap<>();
    private final Set<StorageKey> knownPages = ConcurrentHashMap.newKeySet();

    // Saved form of each page, so a save only encodes the pages that changed.
    // The tags are shared with background writes and must never be modified.
    private final Map<StorageKey, EncodedPage> encoded = new ConcurrentHashMap<>();

    private volatile RegistryAccess opsSource;
    private volatile RegistryOps<Tag> ops;

    private PageCache() {
        super("storage_cache.dat", 1);
    }

    public static PageCache getInstance() {
        return INSTANCE;
    }

    public record CachedPage(List<ItemStack> items, long capturedAt) {
        public int emptySlots() {
            return (int) items.stream().filter(ItemStack::isEmpty).count();
        }
    }

    private record EncodedPage(CachedPage source, ListTag items) {
    }

    // Kept after the world unloads, so a save during disconnect can still encode items.
    private RegistryOps<Tag> ops() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.registryAccess() != opsSource) {
            opsSource = level.registryAccess();
            ops = opsSource.createSerializationContext(NbtOps.INSTANCE);
        }
        if (ops == null) throw new IllegalStateException("No registries available to encode items with");
        return ops;
    }

    private static boolean stacksMatch(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    public Optional<CachedPage> get(StorageKey key) {
        return Optional.ofNullable(pages.get(key));
    }

    public Map<StorageKey, CachedPage> all() {
        return Collections.unmodifiableMap(pages);
    }

    /**
     * Stores a snapshot of a page.
     * An identical snapshot keeps the old one (and its timestamp).
     */
    public void put(StorageKey key, List<ItemStack> items) {
        boolean changed = knownPages.add(key);

        CachedPage existing = pages.get(key);
        if (existing == null || !stacksMatch(existing.items(), items)) {
            pages.put(key, new CachedPage(List.copyOf(items), System.currentTimeMillis()));
            changed = true;
        }

        if (changed) markDirty();
    }

    @SuppressWarnings("unused")
    public void clear() {
        clearMemory();
        markDirty();
    }

    public Set<StorageKey> allKnown() {
        return Collections.unmodifiableSet(knownPages);
    }

    public boolean addKnown(Set<StorageKey> keys) {
        boolean changed = knownPages.addAll(keys);
        if (changed) markDirty();
        return changed;
    }

    /**
     * Makes {@code found} the complete list of pages for {@code type}: new ones are added,
     * missing ones are removed along with their cached contents.
     * {@code found} must only contain keys of that type. Returns true if anything changed.
     */
    public boolean updateIndex(StorageKey.Type type, Set<StorageKey> found) {
        boolean changed = knownPages.removeIf(k -> k.type() == type && !found.contains(k));
        changed |= knownPages.addAll(found);

        List<String> dropped = new ArrayList<>();
        pages.keySet().removeIf(k -> {
            if (k.type() == type && !found.contains(k)) {
                dropped.add(k.id());
                return true;
            }
            return false;
        });
        if (!dropped.isEmpty()) {
            changed = true;
            encoded.keySet().retainAll(pages.keySet());
            LOGGER.info("Storage index no longer lists {}; dropping cached contents", dropped);
        }

        if (changed) markDirty();
        return changed;
    }

    @Override
    protected void writeTo(CompoundTag root) {
        ListTag known = new ListTag();
        for (StorageKey key : knownPages) {
            known.add(StringTag.valueOf(key.id()));
        }
        root.put(KNOWN_KEY, known);

        CompoundTag pagesTag = new CompoundTag();
        pages.forEach((key, page) -> {
            CompoundTag pageTag = new CompoundTag();
            pageTag.put(ITEMS_KEY, encodedItems(key, page));
            pageTag.putLong(CAPTURED_KEY, page.capturedAt());
            pagesTag.put(key.id(), pageTag);
        });
        root.put(PAGES_KEY, pagesTag);
    }

    private ListTag encodedItems(StorageKey key, CachedPage page) {
        EncodedPage cached = encoded.get(key);
        if (cached != null && cached.source() == page) return cached.items();

        RegistryOps<Tag> ops = ops();
        ListTag list = new ListTag();
        for (ItemStack stack : page.items()) {
            // On failure, store an empty slot so the other items keep their positions.
            list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack)
                    .resultOrPartial(err -> LOGGER.warn("Failed to encode stack in {}: {}", key.id(), err))
                    .orElseGet(CompoundTag::new));
        }
        encoded.put(key, new EncodedPage(page, list));
        return list;
    }

    @Override
    protected void readFrom(CompoundTag root, int fileVersion) {
        RegistryOps<Tag> ops = ops();

        root.getList(KNOWN_KEY).ifPresent(list -> {
            for (Tag tag : list) {
                if (tag instanceof StringTag(String value)) {
                    StorageKey.fromId(value).ifPresent(knownPages::add);
                }
            }
        });

        if (fileVersion == 0) {
            // Old layout: the pages sit directly in the root, next to "known".
            for (String id : root.keySet()) {
                if (id.equals(KNOWN_KEY)) continue;
                root.getCompound(id).ifPresent(tag -> readPage(id, tag, ops));
            }
        } else {
            root.getCompound(PAGES_KEY).ifPresent(pagesTag -> {
                for (String id : pagesTag.keySet()) {
                    pagesTag.getCompound(id).ifPresent(tag -> readPage(id, tag, ops));
                }
            });
        }

        LOGGER.info("Loaded storage cache: {} pages (file version {})", pages.size(), fileVersion);
    }

    private void readPage(String id, CompoundTag pageTag, RegistryOps<Tag> ops) {
        Optional<StorageKey> key = StorageKey.fromId(id);
        if (key.isEmpty()) return;

        ListTag list = pageTag.getList(ITEMS_KEY).orElse(new ListTag());
        List<ItemStack> items = new ArrayList<>(list.size());
        for (Tag tag : list) {
            items.add(ItemStack.OPTIONAL_CODEC.parse(ops, tag)
                    .resultOrPartial(err -> LOGGER.warn("Failed to decode stack in {}: {}", id, err))
                    .orElse(ItemStack.EMPTY));
        }

        long captured = pageTag.getLong(CAPTURED_KEY).orElse(0L);
        CachedPage page = new CachedPage(List.copyOf(items), captured);
        pages.put(key.get(), page);
        encoded.put(key.get(), new EncodedPage(page, list));
        knownPages.add(key.get());
    }

    @Override
    protected void clearMemory() {
        pages.clear();
        knownPages.clear();
        encoded.clear();
    }
}