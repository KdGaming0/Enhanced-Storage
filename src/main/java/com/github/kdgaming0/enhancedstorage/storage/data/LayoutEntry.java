package com.github.kdgaming0.enhancedstorage.storage.data;

/** One position in a saved layout. */
public sealed interface LayoutEntry {

    record Page(StorageKey key) implements LayoutEntry {
    }

    /** Empty slot used to push pages onto the next row. The id only needs to be unique within its context. */
    record Spacer(int id) implements LayoutEntry {
    }
}