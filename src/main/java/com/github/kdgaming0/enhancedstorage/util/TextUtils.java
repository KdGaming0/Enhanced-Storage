package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtils {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

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
     * Turns "&c"-style colour and format codes in a user-typed page or section name into "§" codes for drawing.
     * Names are stored as typed, since the text box doesn't accept "§". An "&" not followed by a code stays as is.
     */
    public static String colorCodes(String name) {
        if (name == null || name.indexOf('&') < 0) return name;
        return AMPERSAND_CODE.matcher(name).replaceAll("§$1");
    }

    /**
     * Collapses repeated whitespace and trims leading/trailing spaces.
     */
    public static String normalizeWhitespace(String input) {
        if (input == null) return "";
        return WHITESPACE.matcher(input).replaceAll(" ").trim();
    }
}