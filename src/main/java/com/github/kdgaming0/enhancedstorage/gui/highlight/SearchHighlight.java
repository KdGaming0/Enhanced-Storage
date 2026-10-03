package com.github.kdgaming0.enhancedstorage.gui.highlight;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Search tints for a 16x16 item area: match is drawn under the item, no-match over it.
 */
public final class SearchHighlight {

    private static final int ITEM_SIZE = 16;
    private static final int MATCH_COLOR = 0x8033CC33;
    private static final int NO_MATCH_COLOR = 0xB0101010;

    private SearchHighlight() {
    }

    public static void drawMatch(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, MATCH_COLOR);
    }

    public static void drawNoMatch(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.fill(itemX, itemY, itemX + ITEM_SIZE, itemY + ITEM_SIZE, NO_MATCH_COLOR);
    }
}