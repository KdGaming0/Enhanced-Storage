package com.github.kdgaming0.enhancedstorage.storage.data;

import java.util.List;

/**
 * One position in a saved layout.
 */
public sealed interface LayoutEntry {

    record Page(StorageKey key) implements LayoutEntry {
    }

    /**
     * Two or more pages drawn one under the other as a single column. They share one width.
     * Use {@link LayoutOps#stackOf} so a stack never holds fewer than two pages.
     */
    record Stack(List<StorageKey> keys) implements LayoutEntry {
        public Stack {
            keys = List.copyOf(keys);
        }
    }

    record Section(int id, String name, boolean collapsed) implements LayoutEntry {
        public static final String DEFAULT_NAME = "New section";

        public Section withName(String name) {
            return new Section(id, name, collapsed);
        }

        public Section withCollapsed(boolean collapsed) {
            return new Section(id, name, collapsed);
        }
    }

    record SectionEnd() implements LayoutEntry {
    }

    /**
     * Empty space between pages. {@code slots} is its width in slots, or {@link #FILL} to take the rest of the
     * row, which makes the next page start a new row. The id only needs to be unique within its context.
     */
    record Spacer(int id, int slots) implements LayoutEntry {
        public static final int FILL = 0;

        public boolean fill() {
            return slots == FILL;
        }

        public Spacer withSlots(int slots) {
            return new Spacer(id, slots);
        }
    }
}
