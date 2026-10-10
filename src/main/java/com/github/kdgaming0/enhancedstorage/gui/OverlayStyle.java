package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig.BackgroundType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

public final class OverlayStyle {

    private static final int LIGHT_STRONG = 0xFF000000;
    private static final int LIGHT_NORMAL = 0xFF404040;
    private static final int LIGHT_MUTED = 0xFF555555;
    private static final int LIGHT_FAINT = 0xFF8B8B8B;
    private static final int LIGHT_ACCENT = 0xFFC8930A;
    private static final int LIGHT_DANGER = 0xFFAA0000;
    private static final int LIGHT_LINE = 0xFF555555;
    private static final int LIGHT_HOVER_FILL = 0x18000000;

    private static final int DARK_STRONG = 0xFFFFFFFF;
    private static final int DARK_NORMAL = 0xFFAAAAAA;
    private static final int DARK_MUTED = 0xFF808080;
    private static final int DARK_FAINT = 0xFF6A6A6A;
    private static final int DARK_ACCENT = 0xFFF5C842;
    private static final int DARK_DANGER = 0xFFFF5555;
    private static final int DARK_LINE = 0xFF434343;
    private static final int DARK_HOVER_FILL = 0x18FFFFFF;

    private static final int TRANSPARENT_LINE = 0xCC434343;
    private static final int ICON_SHADOW_RGB = 0x2A2A2A;

    private OverlayStyle() {
    }

    public static String displayName() {
        return switch (current()) {
            case TRANSPARENT -> "Transparent";
            case DARK -> "Dark";
            case LIGHT -> "Light";
        };
    }

    private static int strong() {
        return byTheme(LIGHT_STRONG, DARK_STRONG);
    }

    private static int normal() {
        return byTheme(LIGHT_NORMAL, DARK_NORMAL);
    }

    private static int muted() {
        return byTheme(LIGHT_MUTED, DARK_MUTED);
    }

    private static int faint() {
        return byTheme(LIGHT_FAINT, DARK_FAINT);
    }

    public static int accentColor() {
        return byTheme(LIGHT_ACCENT, DARK_ACCENT);
    }

    public static int accentColor(int alpha) {
        return ARGB.color(alpha, accentColor());
    }

    public static int dangerColor() {
        return byTheme(LIGHT_DANGER, DARK_DANGER);
    }

    public static int navLineColor() {
        return switch (current()) {
            case TRANSPARENT -> TRANSPARENT_LINE;
            case DARK -> DARK_LINE;
            case LIGHT -> LIGHT_LINE;
        };
    }

    public static int titleColor() {
        return normal();
    }

    public static int secondaryTextColor() {
        return muted();
    }

    public static int fullTextColor() {
        return dangerColor();
    }

    public static int navPageColor() {
        return normal();
    }

    public static int navUncachedColor() {
        return faint();
    }

    public static int navEmphasisColor() {
        return strong();
    }

    public static int navActiveTitleColor() {
        return isLight() ? strong() : accentColor();
    }

    public static int navActiveRowColor() {
        return accentColor();
    }

    public static int iconColor() {
        return normal();
    }

    public static int iconColor(float alpha) {
        return ARGB.color(alpha, iconColor());
    }

    public static int iconHoverColor() {
        return strong();
    }

    public static boolean titleShadow() {
        return !isLight();
    }

    public static int titleHoverFill() {
        return byTheme(LIGHT_HOVER_FILL, DARK_HOVER_FILL);
    }

    public static void blitIcon(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height, int color) {
        if (!isLight()) {
            int shadow = (color & 0xFF000000) | ICON_SHADOW_RGB;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + 1, y + 1, width, height, shadow);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
    }

    /**
     * Advances to the next theme and persists the choice.
     */
    public static void cycle() {
        EnhancedStorageConfig.backgroundType = switch (current()) {
            case TRANSPARENT -> BackgroundType.DARK;
            case DARK -> BackgroundType.LIGHT;
            case LIGHT -> BackgroundType.TRANSPARENT;
        };
        EnhancedStorageConfig.write(MOD_ID);
    }

    private static int byTheme(int light, int dark) {
        return isLight() ? light : dark;
    }

    private static boolean isLight() {
        return current() == BackgroundType.LIGHT;
    }

    private static BackgroundType current() {
        return EnhancedStorageConfig.backgroundType;
    }

    public enum Sprite {
        PAGE_CARD_IDLE(themed("page/page_card_idle")),
        PAGE_CARD_ACTIVE(themed("page/page_card_active")),
        PAGE_CARD_HOVER(themed("page/page_card_hover")),
        PAGE_SPACER(themed("page/page_spacer")),
        SECTION_FRAME(themed("section/section_frame")),
        SECTION_HEADER(themed("section/section_header")),
        SECTION_EXPAND_ARROW(fixed("common/section_expand_arrow")),
        SECTION_COLLAPSE_ARROW(fixed("common/section_collapse_arrow")),
        SECTION_TRASH(fixed("common/section_trash")),
        SECTION_GRIP(fixed("common/section_grip")),
        STORAGE_SLOT(themed("page/storage_slot")),
        STORAGE_SLOT_SMALL(themed("page/storage_slot_small")),
        MAIN_PANEL(themed("panel/main_panel")),
        STORAGE_INVENTORY(themed("panel/storage_inventory")),
        STORAGE_INVENTORY_FILLED(themed("panel/storage_inventory_filled")),
        STORAGE_OVERVIEW(themed("panel/storage_overview")),
        STORAGE_INVENTORY_TOP(themed("panel/storage_inventory_top")),
        STORAGE_INVENTORY_FILLED_TOP(themed("panel/storage_inventory_filled_top")),
        STORAGE_OVERVIEW_TOP(themed("panel/storage_overview_top")),
        NAV_PAGE(fixed("common/nav_page")),
        NAV_SECTION_EXPANDED(fixed("common/nav_section_expanded")),
        NAV_SECTION_COLLAPSED(fixed("common/nav_section_collapsed")),
        NAV_ROW_HOVER(fixed("common/nav_row_hover")),
        NAV_ROW_ACTIVE(fixed("common/nav_row_active")),
        SLIDER_BUTTON(themed("button/slider_button")),
        SLOT_HIGHLIGHT_BACK(fixed("common/storage_slot_highlight_back")),
        SLOT_HIGHLIGHT_FRONT(fixed("common/storage_slot_highlight_front")),
        SETTINGS_ICON(fixed("icons/gear_icon")),
        EDIT_ICON(fixed("icons/edit_icon")),
        RESET_ICON(fixed("icons/reset_icon")),
        THEME_ICON(variants("icons/icon_light", "icons/icon_dark", "icons/icon_transparent"));

        private final Identifier light;
        private final Identifier dark;
        private final Identifier transparent;

        Sprite(Identifier[] variants) {
            this.light = variants[0];
            this.dark = variants[1];
            this.transparent = variants[2];
        }

        private static Identifier[] themed(String name) {
            return variants("light/" + name, "dark/" + name + "_dark", "transparent/" + name + "_trans");
        }

        private static Identifier[] fixed(String path) {
            return variants(path, path, path);
        }

        private static Identifier[] variants(String light, String dark, String transparent) {
            return new Identifier[]{
                    Identifier.fromNamespaceAndPath(MOD_ID, light),
                    Identifier.fromNamespaceAndPath(MOD_ID, dark),
                    Identifier.fromNamespaceAndPath(MOD_ID, transparent)};
        }

        public Identifier id() {
            return switch (current()) {
                case LIGHT -> light;
                case DARK -> dark;
                case TRANSPARENT -> transparent;
            };
        }
    }
}