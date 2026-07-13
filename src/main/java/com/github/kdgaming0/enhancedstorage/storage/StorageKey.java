package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a single storage page
 */
public record StorageKey(Type type, int page) {

    public static final Pattern RIFT_PATTERN = Pattern.compile("rift storage \\((\\d+)/(\\d+)\\)");
    public static final Comparator<StorageKey> DISPLAY_ORDER = Comparator.comparing(StorageKey::type).thenComparingInt(StorageKey::page);
    private static final Pattern ENDER_CHEST_PATTERN = Pattern.compile("ender chest \\((\\d+)/\\d+\\)");
    private static final Pattern BACKPACK_PATTERN = Pattern.compile("backpack \\(slot #(\\d+)\\)");
    private static final Pattern ENDER_CHEST_ITEM_PATTERN = Pattern.compile("ender chest page (\\d+)");
    private static final Pattern BACKPACK_ITEM_PATTERN = Pattern.compile("backpack slot (\\d+)");

    /**
     * Tries to identify a storage page from a screen title.
     * Returns empty for any screen we don't care about.
     */
    public static Optional<StorageKey> fromTitle(Component title) {
        Matcher m = TextUtils.matcher(title, ENDER_CHEST_PATTERN);

        if (m.matches()) {
            return Optional.of(new StorageKey(Type.ENDER_CHEST, Integer.parseInt(m.group(1))));
        }

        m = TextUtils.matcher(title, BACKPACK_PATTERN);
        if (m.find()) {
            return Optional.of(new StorageKey(Type.BACKPACK, Integer.parseInt(m.group(1))));
        }

        m = TextUtils.matcher(title, RIFT_PATTERN);
        if (m.matches()) {
            return Optional.of(new StorageKey(Type.RIFT, Integer.parseInt(m.group(1))));
        }

        if (TextUtils.matches(title, "storage")) {
            return Optional.of(new StorageKey(Type.STORAGE_INDEX, 0));
        }

        return Optional.empty();
    }

    /**
     * Identifies a storage page from an item in the storage index menu.
     */
    public static Optional<StorageKey> fromIndexItem(Component name) {
        if (TextUtils.stripText(name).contains("locked") || TextUtils.stripText(name).contains("empty")) {
            return Optional.empty();
        }

        Matcher m = TextUtils.matcher(name, ENDER_CHEST_ITEM_PATTERN);

        if (m.find()) {
            return Optional.of(new StorageKey(Type.ENDER_CHEST, Integer.parseInt(m.group(1))));
        }

        m = TextUtils.matcher(name, BACKPACK_ITEM_PATTERN);
        if (m.find()) {
            return Optional.of(new StorageKey(Type.BACKPACK, Integer.parseInt(m.group(1))));
        }

        return Optional.empty();
    }

    public static Optional<StorageKey> fromId(String id) {
        int sep = id.lastIndexOf('_');
        if (sep < 0) return Optional.empty();
        try {
            Type type = Type.valueOf(id.substring(0, sep).toUpperCase());
            int page = Integer.parseInt(id.substring(sep + 1));
            return Optional.of(new StorageKey(type, page));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String id() {
        return type.name().toLowerCase() + "_" + page;
    }

    public String displayName() {
        return switch (type) {
            case ENDER_CHEST -> "Ender Chest " + page + "/9";
            case BACKPACK -> "Backpack #" + page;
            case STORAGE_INDEX -> "Storage";
            case RIFT -> "Rift Storage #" + page;
        };
    }

    public enum Type {
        STORAGE_INDEX,
        ENDER_CHEST,
        BACKPACK,
        RIFT
    }
}