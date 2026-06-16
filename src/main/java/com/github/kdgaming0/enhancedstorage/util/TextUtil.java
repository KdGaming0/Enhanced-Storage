package com.github.kdgaming0.enhancedstorage.util;

import java.util.regex.Pattern;

/**
 * Small text helpers shared across features.
 */
public final class TextUtil {

    // Section-sign formatting codes (§ followed by a color/format char). Hypixel sometimes
    // embeds these directly in the message content, which would break plain-text matching.
    private static final Pattern COLOR_CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private TextUtil() {
    }

    /** Removes legacy {@code §}-style color/formatting codes from a string. */
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) return text;
        return COLOR_CODE.matcher(text).replaceAll("");
    }
}
