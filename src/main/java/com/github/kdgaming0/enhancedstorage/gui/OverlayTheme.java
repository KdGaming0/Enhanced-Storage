package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig.BackgroundType;
import net.minecraft.resources.Identifier;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

public final class OverlayTheme {

    public static final String PAGE_CARD_IDLE = "page_card_idle";
    public static final String PAGE_CARD_ACTIVE = "page_card_active";
    public static final String PAGE_CARD_HOVER = "page_card_hover";
    public static final String PAGE_SPACER = "page_spacer";
    public static final String SECTION_FRAME = "section_frame";
    public static final String SECTION_HEADER = "section_header";
    public static final String SECTION_EXPAND_ARROW = "section_expand_arrow";
    public static final String SECTION_COLLAPSE_ARROW = "section_collapse_arrow";
    public static final String SECTION_TRASH = "section_trash";
    public static final String SECTION_TRASH_HOVER = "section_trash_hover";
    public static final String SECTION_GRIP = "section_grip";
    public static final String SECTION_GRIP_HOVER = "section_grip_hover";
    public static final String STORAGE_SLOT = "storage_slot";
    public static final String MAIN_PANEL = "main_panel";
    public static final String STORAGE_INVENTORY = "storage_inventory";
    public static final String STORAGE_OVERVIEW = "storage_overview";

    private static final int LIGHT_TITLE_COLOR = 0xFF000000;
    private static final int DEFAULT_TITLE_COLOR = 0xFFAAAAAA;

    private OverlayTheme() {
    }

    public static Identifier sprite(String name) {
        String path = switch (current()) {
            case TRANSPARENT -> "transparent/" + name + "_trans";
            case DARK -> "dark/" + name + "_dark";
            case LIGHT -> "light/" + name;
        };
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Identifier icon() {
        String path = switch (current()) {
            case TRANSPARENT -> "icons/icon_transparent";
            case DARK -> "icons/icon_dark";
            case LIGHT -> "icons/icon_light";
        };
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String displayName() {
        return switch (current()) {
            case TRANSPARENT -> "Transparent";
            case DARK -> "Dark";
            case LIGHT -> "Light";
        };
    }

    public static int titleColor() {
        return current() == BackgroundType.LIGHT ? LIGHT_TITLE_COLOR : DEFAULT_TITLE_COLOR;
    }

    public static boolean titleShadow() {
        return current() != BackgroundType.LIGHT;
    }

    public static int titleHoverFill() {
        return current() == BackgroundType.LIGHT ? 0x18000000 : 0x18FFFFFF;
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

    private static BackgroundType current() {
        return EnhancedStorageConfig.backgroundType;
    }
}