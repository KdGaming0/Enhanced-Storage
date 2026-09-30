package com.github.kdgaming0.enhancedstorage.gui;

import org.jspecify.annotations.Nullable;

/** Screen-space rectangle with half-open bounds, matching vanilla hover checks. */
public record Rect(int x, int y, int width, int height) {

    /** Null-safe hit test for optional areas, such as a hidden panel. */
    public static boolean contains(@Nullable Rect rect, double px, double py) {
        return rect != null && rect.contains(px, py);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }
}