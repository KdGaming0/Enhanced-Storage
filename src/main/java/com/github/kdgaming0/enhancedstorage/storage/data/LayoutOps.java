package com.github.kdgaming0.enhancedstorage.storage.data;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure edits on a layout's top-level entries. Every drag, menu action and load goes through here, so a stack
 * can never end up half-built: it always holds at least two distinct pages.
 */
public final class LayoutOps {

    private LayoutOps() {
    }

    /**
     * A page for one key, a stack for more.
     */
    public static LayoutEntry stackOf(List<StorageKey> keys) {
        return keys.size() == 1 ? new LayoutEntry.Page(keys.getFirst()) : new LayoutEntry.Stack(keys);
    }

    public static List<StorageKey> keysOf(LayoutEntry entry) {
        return switch (entry) {
            case LayoutEntry.Page(StorageKey key) -> List.of(key);
            case LayoutEntry.Stack(List<StorageKey> keys) -> keys;
            default -> List.of();
        };
    }

    /**
     * Index of the entry holding the page, or -1.
     */
    public static int indexOfKey(List<LayoutEntry> entries, StorageKey key) {
        for (int i = 0; i < entries.size(); i++) {
            if (keysOf(entries.get(i)).contains(key)) return i;
        }
        return -1;
    }

    /**
     * Index of the entry the anchor names: the one holding a page, or the equal entry for anything else.
     */
    public static int indexOfAnchor(List<LayoutEntry> entries, LayoutEntry anchor) {
        if (anchor instanceof LayoutEntry.Page(StorageKey key)) return indexOfKey(entries, key);
        return entries.indexOf(anchor);
    }

    /**
     * Drops lone or empty stacks and repeated pages, keeping the first occurrence.
     */
    public static List<LayoutEntry> normalize(List<LayoutEntry> entries) {
        List<LayoutEntry> result = new ArrayList<>();
        Set<StorageKey> seen = new HashSet<>();
        for (LayoutEntry entry : entries) {
            switch (entry) {
                case LayoutEntry.Page(StorageKey key) -> {
                    if (seen.add(key)) result.add(entry);
                }
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    List<StorageKey> fresh = keys.stream().filter(seen::add).toList();
                    if (!fresh.isEmpty()) result.add(stackOf(fresh));
                }
                default -> result.add(entry);
            }
        }
        return result;
    }

    /**
     * Takes one page, or one non-page entry, out of the list.
     */
    public static List<LayoutEntry> remove(List<LayoutEntry> entries, LayoutEntry item) {
        List<LayoutEntry> result = new ArrayList<>(entries.size());
        for (LayoutEntry entry : entries) {
            if (item instanceof LayoutEntry.Page(StorageKey key) && keysOf(entry).contains(key)) {
                List<StorageKey> rest = keysOf(entry).stream().filter(k -> !k.equals(key)).toList();
                if (!rest.isEmpty()) result.add(stackOf(rest));
            } else if (!entry.equals(item)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Every stack becomes separate pages, in the same order.
     */
    public static List<LayoutEntry> unstackAll(List<LayoutEntry> entries) {
        List<LayoutEntry> result = new ArrayList<>(entries.size());
        for (LayoutEntry entry : entries) {
            if (entry instanceof LayoutEntry.Stack(List<StorageKey> keys)) {
                for (StorageKey key : keys) result.add(new LayoutEntry.Page(key));
            } else {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * The page leaves its stack and becomes its own column right after it. Unchanged if it isn't stacked.
     */
    public static List<LayoutEntry> unstack(List<LayoutEntry> entries, StorageKey key) {
        int at = indexOfKey(entries, key);
        if (at < 0 || !(entries.get(at) instanceof LayoutEntry.Stack)) return entries;
        List<LayoutEntry> result = remove(entries, new LayoutEntry.Page(key));
        result.add(Math.min(at + 1, result.size()), new LayoutEntry.Page(key));
        return result;
    }

    /**
     * Moves a page or a space. Returns null when the target no longer exists.
     */
    public static @Nullable List<LayoutEntry> move(List<LayoutEntry> entries, LayoutEntry item, Target target) {
        List<LayoutEntry> base = remove(entries, item);
        switch (target) {
            case Target.Before(LayoutEntry anchor) -> {
                int at = indexOfAnchor(base, anchor);
                if (at < 0) return null;
                base.add(at, item);
            }
            case Target.After(LayoutEntry anchor) -> {
                int at = indexOfAnchor(base, anchor);
                if (at < 0) return null;
                base.add(at + 1, item);
            }
            case Target.At(int index) -> base.add(Math.clamp(index, 0, base.size()), item);
            case Target.Stack(StorageKey page, boolean above) -> {
                int at = indexOfKey(base, page);
                if (at < 0) return null;
                if (!(item instanceof LayoutEntry.Page(StorageKey moved))) {
                    base.add(above ? at : at + 1, item);
                    return base;
                }
                List<StorageKey> keys = new ArrayList<>(keysOf(base.get(at)));
                keys.add(keys.indexOf(page) + (above ? 0 : 1), moved);
                base.set(at, stackOf(keys));
            }
        }
        return base;
    }

    /**
     * Where a dragged page or space lands. Resolved against the layout without the dragged entry, so the
     * same target always gives the same result however the layout looked during the drag.
     */
    public sealed interface Target {
        /**
         * Its own column before the column holding {@code anchor}.
         */
        record Before(LayoutEntry anchor) implements Target {
        }

        /**
         * Its own column after the column holding {@code anchor}.
         */
        record After(LayoutEntry anchor) implements Target {
        }

        /**
         * Inside the stack holding {@code page} (a lone page becomes a stack), above or below it.
         */
        record Stack(StorageKey page, boolean above) implements Target {
        }

        /**
         * Its own column at this index of the layout without the dragged entry.
         */
        record At(int index) implements Target {
        }
    }
}
