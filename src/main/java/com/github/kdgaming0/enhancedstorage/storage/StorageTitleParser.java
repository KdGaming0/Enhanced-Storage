package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.util.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Hypixel SkyBlock container screen titles into typed storage metadata.
 */
public final class StorageTitleParser {

    private static final Pattern PATTERN_OVERVIEW = Pattern.compile("^[Ss]torage$");
    private static final Pattern PATTERN_RIFT = Pattern.compile(".*[Rr]ift [Ss]torage.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern PATTERN_STORAGE_PAGE = Pattern.compile(".*[Ss]torage.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern PATTERN_ENDER_CHEST = Pattern.compile(".*[Ee]nder [Cc]hest.*\\((\\d+)/(\\d+)\\)");
    private static final Pattern PATTERN_BACKPACK = Pattern.compile(".*[Bb]ackpack.*\\s*[(#]\\s*(\\d+)\\s*\\)?");
    private static final Pattern PATTERN_BACKPACK_UNNAMED = Pattern.compile(".*[Bb]ackpack.*");

    private StorageTitleParser() {
    }

    public static Optional<ParsedTitle> parse(String rawTitle) {
        String clean = stripFormatting(rawTitle);

        if (PATTERN_OVERVIEW.matcher(clean).matches()) {
            return Optional.of(new ParsedTitle(null, clean, true));
        }

        Matcher rift = PATTERN_RIFT.matcher(clean);
        if (rift.matches()) {
            try {
                int page = Integer.parseInt(rift.group(1));
                return Optional.of(new ParsedTitle(StoragePage.ofRift(page), clean, false));
            } catch (IllegalArgumentException ignored) {
            }
        }

        for (Pattern ecPattern : new Pattern[]{PATTERN_STORAGE_PAGE, PATTERN_ENDER_CHEST}) {
            Matcher m = ecPattern.matcher(clean);
            if (m.matches()) {
                try {
                    int page = Integer.parseInt(m.group(1));
                    return Optional.of(new ParsedTitle(StoragePage.ofEnderChest(page), clean, false));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        Matcher bp = PATTERN_BACKPACK.matcher(clean);
        if (bp.matches()) {
            try {
                int page = Integer.parseInt(bp.group(1));
                return Optional.of(new ParsedTitle(StoragePage.ofBackpack(page), clean, false));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (PATTERN_BACKPACK_UNNAMED.matcher(clean).matches()) {
            return Optional.of(new ParsedTitle(null, clean, false));
        }

        return Optional.empty();
    }

    public static String stripFormatting(String text) {
        return StringUtil.stripColorCodes(text);
    }

    public record ParsedTitle(@Nullable StoragePage page, String rawTitle, boolean isOverview) {
    }
}