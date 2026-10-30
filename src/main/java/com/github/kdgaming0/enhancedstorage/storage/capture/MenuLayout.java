package com.github.kdgaming0.enhancedstorage.storage.capture;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Where things sit inside a Hypixel storage menu.
 */
public final class MenuLayout {

    /**
     * Player inventory (27) + hotbar (9) slots appended at the end of every chest menu.
     */
    public static final int PLAYER_SLOTS = 36;

    /**
     * The first row of a storage page is a header (back button, title, ...), never items.
     */
    public static final int HEADER_SLOTS = 9;

    public static final int SLOTS_PER_ROW = 9;

    private MenuLayout() {
    }

    /**
     * Number of slots that belong to the container itself (not the player's inventory). They come first.
     */
    public static int containerSlots(AbstractContainerMenu menu) {
        return menu.slots.size() - PLAYER_SLOTS;
    }

    /**
     * In the storage index, rows 1, 3 and 4 hold the ender chest and backpack icons. Returns the row they are shown
     * in on the Storage Overview card, or -1 for navigation and filler rows.
     */
    public static int indexIconRow(int menuRow) {
        return switch (menuRow) {
            case 1 -> 0;
            case 3 -> 1;
            case 4 -> 2;
            default -> -1;
        };
    }

    static boolean isIndexIconSlot(int slotIndex) {
        return indexIconRow(slotIndex / SLOTS_PER_ROW) >= 0;
    }
}
