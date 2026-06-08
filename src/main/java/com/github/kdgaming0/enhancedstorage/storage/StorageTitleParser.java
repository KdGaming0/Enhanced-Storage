package com.github.kdgaming0.enhancedstorage.storage;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based parser that classifies container screen titles from Hypixel SkyBlock
 * into structured page metadata.
 */
public final class StorageTitleParser {

    private static final Pattern PATTERN_STORAGE_PAGE = Pattern.compile(".*[Ss]torage.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern PATTERN_ENDER_CHEST = Pattern.compile(".*[Ee]nder [Cc]hest.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern PATTERN_BACKPACK = Pattern.compile(".*[Bb]ackpack.*\\((\\d+)\\)");
    private static final Pattern PATTERN_BACKPACK_UNNAMED = Pattern.compile(".*[Bb]ackpack.*");
    private static final Pattern PATTERN_OVERVIEW = Pattern.compile("^[Ss]torage$");
    private static final Pattern FORMATTING_CODES = Pattern.compile("§.");

    private StorageTitleParser() {}

    public static Optional<ParsedTitle> parse(String rawTitle) {
        String clean = stripFormatting(rawTitle);

        Matcher overview = PATTERN_OVERVIEW.matcher(clean);
        if (overview.matches()) {
            return Optional.of(new ParsedTitle(null, clean, true));
        }

        Matcher storage = PATTERN_STORAGE_PAGE.matcher(clean);
        if (storage.matches()) {
            try {
                int page = Integer.parseInt(storage.group(1));
                return Optional.of(new ParsedTitle(StoragePage.ofEnderChest(page), clean, false));
            } catch (IllegalArgumentException ignored) {}
        }

        Matcher ec = PATTERN_ENDER_CHEST.matcher(clean);
        if (ec.matches()) {
            try {
                int page = Integer.parseInt(ec.group(1));
                return Optional.of(new ParsedTitle(StoragePage.ofEnderChest(page), clean, false));
            } catch (IllegalArgumentException ignored) {}
        }

        Matcher bp = PATTERN_BACKPACK.matcher(clean);
        if (bp.matches()) {
            try {
                int page = Integer.parseInt(bp.group(1));
                return Optional.of(new ParsedTitle(StoragePage.ofBackpack(page), clean, false));
            } catch (IllegalArgumentException ignored) {}
        }

        Matcher bpUnnamed = PATTERN_BACKPACK_UNNAMED.matcher(clean);
        if (bpUnnamed.matches()) {
            return Optional.of(new ParsedTitle(null, clean, false));
        }

        return Optional.empty();
    }

    public static String stripFormatting(String text) {
        return FORMATTING_CODES.matcher(text).replaceAll("");
    }

    public record ParsedTitle(@Nullable StoragePage page, String rawTitle, boolean isOverview) {}
}
