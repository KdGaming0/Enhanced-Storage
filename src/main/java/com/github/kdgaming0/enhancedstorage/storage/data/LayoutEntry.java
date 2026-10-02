package com.github.kdgaming0.enhancedstorage.storage.data;

/** One position in a saved layout. */
public sealed interface LayoutEntry {

    record Page(StorageKey key) implements LayoutEntry {
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

    /** Empty slot used to push pages onto the next row. The id only needs to be unique within its context. */
    record Spacer(int id) implements LayoutEntry {
    }
}