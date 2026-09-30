package com.github.kdgaming0.enhancedstorage.storage.data;

import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a single storage page.
 */
public record StorageKey(Type type, int page) {

    public enum Type {
        STORAGE_INDEX(1),
        ENDER_CHEST(9),
        BACKPACK(0),     // 0 = the number of pages varies
        RIFT(0);

        private final int maxPages;

        Type(int maxPages) {
            this.maxPages = maxPages;
        }

        public int maxPages() {
            return maxPages;
        }
    }

    public static final Comparator<StorageKey> DISPLAY_ORDER =
            Comparator.comparing(StorageKey::type).thenComparingInt(StorageKey::page);

    // Matched against TextUtils.stripString output, so they must be lowercase.
    private static final Pattern ENDER_CHEST_TITLE = Pattern.compile("ender chest \\((\\d+)/\\d+\\)");
    private static final Pattern BACKPACK_TITLE = Pattern.compile(".*backpack.*\\(slot #(\\d+)\\).*");
    private static final Pattern RIFT_TITLE = Pattern.compile("rift storage \\((\\d+)/(\\d+)\\)");
    private static final Pattern ENDER_CHEST_ITEM = Pattern.compile(".*ender chest page (\\d+).*");
    private static final Pattern BACKPACK_ITEM = Pattern.compile(".*backpack slot (\\d+).*");

    public static Optional<StorageKey> fromTitleText(String text) {
        String title = TextUtils.stripString(text);

        if (title.equals("storage")) {
            return Optional.of(new StorageKey(Type.STORAGE_INDEX, 0));
        }

        return match(ENDER_CHEST_TITLE, title, Type.ENDER_CHEST)
                .or(() -> match(BACKPACK_TITLE, title, Type.BACKPACK))
                .or(() -> match(RIFT_TITLE, title, Type.RIFT));
    }

    public static Optional<StorageKey> fromIndexItemText(String text) {
        String name = TextUtils.stripString(text);

        if (name.contains("locked") || name.contains("empty")) {
            return Optional.empty();
        }

        return match(ENDER_CHEST_ITEM, name, Type.ENDER_CHEST)
                .or(() -> match(BACKPACK_ITEM, name, Type.BACKPACK));
    }

    // Finds and returns the total number of pages in your rift storage
    public static OptionalInt riftTotalPages(String titleText) {
        Matcher matcher = RIFT_TITLE.matcher(TextUtils.stripString(titleText));
        return matcher.matches()
                ? OptionalInt.of(Integer.parseInt(matcher.group(2)))
                : OptionalInt.empty();
    }

    public static Optional<StorageKey> fromTitle(Component title) {
        return fromTitleText(title.getString());
    }

    public static Optional<StorageKey> fromIndexItem(Component name) {
        return fromIndexItemText(name.getString());
    }

    public static OptionalInt riftTotalPages(Component title) {
        return riftTotalPages(title.getString());
    }

    public static Optional<StorageKey> fromId(String id) {
        int sep = id.lastIndexOf('_');
        if (sep < 0) return Optional.empty();

        try {
            Type type = Type.valueOf(id.substring(0, sep).toUpperCase(Locale.ROOT));
            int page = Integer.parseInt(id.substring(sep + 1));
            return Optional.of(new StorageKey(type, page));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // The save id, like "ender_chest_3"
    public String id() {
        return type.name().toLowerCase(Locale.ROOT) + "_" + page;
    }

    public String displayName() {
        return switch (type) {
            case STORAGE_INDEX -> "Storage";
            case ENDER_CHEST -> "Ender Chest " + page + "/" + type.maxPages();
            case BACKPACK -> "Backpack #" + page;
            case RIFT -> "Rift Storage #" + page;
        };
    }

    private static Optional<StorageKey> match(Pattern pattern, String text, Type type) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(new StorageKey(type, Integer.parseInt(matcher.group(1))));
    }
}