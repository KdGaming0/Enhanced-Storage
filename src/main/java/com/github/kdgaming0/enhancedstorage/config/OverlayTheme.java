package com.github.kdgaming0.enhancedstorage.config;

/**
 * Visual theme variants for the storage overlay sprites.
 * Each theme appends a suffix to the base sprite identifier.
 */
public enum OverlayTheme {
    LIGHT(""),
    DARK("_dark"),
    TRANSPARENT("_trans");

    private final String suffix;

    OverlayTheme(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
