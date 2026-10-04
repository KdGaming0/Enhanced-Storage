package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;

import java.util.Set;

public final class SearchAutofocus {

    private static final Set<Integer> TYPING_KEYS = Set.of(
            InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_D,
            InputConstants.KEY_E, InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H,
            InputConstants.KEY_I, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L,
            InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O, InputConstants.KEY_P,
            InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S, InputConstants.KEY_T,
            InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W, InputConstants.KEY_X,
            InputConstants.KEY_Y, InputConstants.KEY_Z,
            InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7,
            InputConstants.KEY_8, InputConstants.KEY_9,
            InputConstants.KEY_APOSTROPHE, InputConstants.KEY_BACKSLASH, InputConstants.KEY_COMMA,
            InputConstants.KEY_EQUALS, InputConstants.KEY_GRAVE, InputConstants.KEY_LBRACKET,
            InputConstants.KEY_MINUS, InputConstants.KEY_PERIOD, InputConstants.KEY_RBRACKET,
            InputConstants.KEY_SEMICOLON, InputConstants.KEY_SLASH,
            InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
            InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5,
            InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8,
            InputConstants.KEY_NUMPAD9, InputConstants.KEY_ADD, InputConstants.KEY_MULTIPLY,
            InputConstants.KEY_NUMPADCOMMA, InputConstants.KEY_NUMPADEQUALS);

    private SearchAutofocus() {
    }

    public static boolean shouldFocus(KeyEvent event, boolean overItem) {
        if (!EnhancedStorageConfig.searchBarAutofocus || overItem) return false;
        if (event.hasControlDown() || event.hasAltDown()) return false;
        return TYPING_KEYS.contains(event.key());
    }

    public static boolean shouldRelease(KeyEvent event, EditBox box) {
        if (!EnhancedStorageConfig.searchBarAutofocus || !box.getValue().isEmpty()) return false;
        int key = event.key();
        return key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE;
    }
}
