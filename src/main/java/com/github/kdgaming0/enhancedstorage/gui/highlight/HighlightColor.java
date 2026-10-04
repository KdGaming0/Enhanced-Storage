package com.github.kdgaming0.enhancedstorage.gui.highlight;

final class HighlightColor {

    private final int fallbackRgb;
    private String lastHex;
    private int lastOpacity = -1;
    private int argb;

    HighlightColor(int fallbackRgb) {
        this.fallbackRgb = fallbackRgb;
    }

    int get(String hex, int opacityPercent) {
        if (opacityPercent != lastOpacity || !hex.equals(lastHex)) {
            lastHex = hex;
            lastOpacity = opacityPercent;
            argb = toArgb(hex, opacityPercent, fallbackRgb);
        }
        return argb;
    }

    static int toArgb(String hex, int opacityPercent, int fallbackRgb) {
        int rgb = fallbackRgb;
        String digits = hex == null ? "" : hex.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() == 6) {
            try {
                rgb = Integer.parseInt(digits, 16);
            } catch (NumberFormatException ignored) {
            }
        }
        int alpha = Math.round(Math.clamp(opacityPercent, 0, 100) * 255 / 100f);
        return alpha << 24 | (rgb & 0xFFFFFF);
    }
}
