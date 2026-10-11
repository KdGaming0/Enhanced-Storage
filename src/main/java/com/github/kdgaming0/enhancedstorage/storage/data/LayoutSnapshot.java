package com.github.kdgaming0.enhancedstorage.storage.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One context's whole layout: the arrangement plus the per-page widths and names.
 */
public record LayoutSnapshot(List<LayoutEntry> entries, Map<StorageKey, Integer> widths,
                             Map<StorageKey, String> names) {

    public LayoutSnapshot {
        entries = List.copyOf(entries);
        widths = Map.copyOf(widths);
        names = Map.copyOf(names);
    }

    public boolean sameIgnoringCollapse(LayoutSnapshot other) {
        return widths.equals(other.widths) && names.equals(other.names)
                && expanded(entries).equals(expanded(other.entries));
    }

    public static List<LayoutEntry> keepCollapse(List<LayoutEntry> target, List<LayoutEntry> current) {
        Map<Integer, Boolean> collapsed = new HashMap<>();
        for (LayoutEntry entry : current) {
            if (entry instanceof LayoutEntry.Section section) collapsed.put(section.id(), section.collapsed());
        }
        return target.stream()
                .map(entry -> entry instanceof LayoutEntry.Section section && collapsed.containsKey(section.id())
                        ? section.withCollapsed(collapsed.get(section.id()))
                        : entry)
                .toList();
    }

    public int pageCount() {
        return entries.stream().mapToInt(entry -> LayoutOps.keysOf(entry).size()).sum();
    }

    private static List<LayoutEntry> expanded(List<LayoutEntry> entries) {
        return entries.stream()
                .map(entry -> entry instanceof LayoutEntry.Section section ? section.withCollapsed(false) : entry)
                .toList();
    }
}
