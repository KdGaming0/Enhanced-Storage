package com.github.kdgaming0.enhancedstorage.storage.capture;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Where things sit inside a Hypixel storage menu.
 */
final class MenuLayout {

    /**
     * Player inventory (27) + hotbar (9) slots appended at the end of every chest menu.
     */
    static final int PLAYER_SLOTS = 36;

    /**
     * The first row of a storage page is a header (back button, title, ...), never items.
     */
    static final int HEADER_SLOTS = 9;

    private static final int SLOTS_PER_ROW = 9;

    /**
     * In the storage index, rows 1, 3 and 4 hold the ender chest and backpack icons.
     */
    private static final int[] INDEX_ICON_ROWS = {1, 3, 4};

    private MenuLayout() {
    }

    /**
     * Number of slots that belong to the container itself (not the player's inventory).
     */
    static int containerSlots(AbstractContainerMenu menu) {
        return menu.slots.size() - PLAYER_SLOTS;
    }

    static boolean isIndexIconSlot(int slotIndex) {
        int row = slotIndex / SLOTS_PER_ROW;
        for (int iconRow : INDEX_ICON_ROWS) {
            if (row == iconRow) return true;
        }
        return false;
    }
}