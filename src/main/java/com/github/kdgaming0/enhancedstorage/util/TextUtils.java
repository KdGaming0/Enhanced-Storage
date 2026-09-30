package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
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

    /**
     * Returns a Matcher against the component's stripped text.
     */
    public static Matcher matcher(Component component, Pattern pattern) {
        return pattern.matcher(stripText(component));
    }

    /**
     * Checks if a Text's stripped content matches exactly.
     */
    public static boolean matches(Component component, String expected) {
        return stripText(component).equals(expected.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a Text's stripped content starts with a prefix.
     */
    public static boolean startsWith(Component component, String prefix) {
        return stripText(component).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a Text's stripped content contains a substring.
     */
    public static boolean contains(Component component, String substring) {
        return stripText(component).contains(substring.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a Text's stripped content matches a regex pattern.
     * The pattern should be written in lowercase since stripped text is lowercased.
     */
    public static boolean matchesRegex(Component component, Pattern pattern) {
        return pattern.matcher(stripText(component)).matches();
    }

    /**
     * Checks if a Text's stripped content contains a match anywhere within it.
     */
    public static boolean findsRegex(Component component, Pattern pattern) {
        return pattern.matcher(stripText(component)).find();
    }
}