package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtils {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextUtils() {
    }

    /**
     * Converts a Text component into raw plain text, with formatting codes stripped.
     */
    public static String stripText(Component component) {
        if (component == null) return "";
        return stripString(component.getString());
    }

    /**
     * Strips formatting codes, lowercases, and collapses whitespace.
     */
    public static String stripString(String raw) {
        if (raw == null) return "";
        String stripped = ChatFormatting.stripFormatting(raw);
        return normalizeWhitespace(stripped.toLowerCase(Locale.ROOT));
    }

    /**
     * Collapses repeated whitespace and trims leading/trailing spaces.
     */
    public static String normalizeWhitespace(String input) {
        if (input == null) return "";
        return WHITESPACE.matcher(input).replaceAll(" ").trim();
    }
}