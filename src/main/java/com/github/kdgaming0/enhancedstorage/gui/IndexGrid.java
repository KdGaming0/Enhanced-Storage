package com.github.kdgaming0.enhancedstorage.gui;

import static com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout.SLOT_SIZE;

/**
 * Slot geometry of the Storage Overview card, in panel-local coordinates.
 * Row 0 holds ender chest pages; rows 1 and 2 hold backpacks.
 */
public final class IndexGrid {

    public static final int COLUMNS = 9;
    private static final int START_X = 7;
    private static final int[] ROW_Y = {14, 42, 60};

    private IndexGrid() {
    }

    public static int cellX(int col) {
        return START_X + col * SLOT_SIZE;
    }

    /** Rows past the defined ones continue downward at slot pitch. */
    public static int rowY(int row) {
        if (row < ROW_Y.length) return ROW_Y[row];
        return ROW_Y[ROW_Y.length - 1] + (row - ROW_Y.length + 1) * SLOT_SIZE;
    }

    /** Index of the cell under a panel-local point, or -1 if none. */
    public static int cellAt(int localX, int localY) {
        int gridX = localX - START_X;
        if (gridX < 0 || gridX >= COLUMNS * SLOT_SIZE) return -1;

        for (int row = 0; row < ROW_Y.length; row++) {
            if (localY >= ROW_Y[row] && localY < ROW_Y[row] + SLOT_SIZE) {
                return row * COLUMNS + gridX / SLOT_SIZE;
            }
        }
        return -1;
    }

    /** Visible row for a row of the /storage menu, or -1 for navigation and filler rows. */
    public static int visibleRowOfMenuRow(int menuRow) {
        return switch (menuRow) {
            case 1 -> 0;
            case 3 -> 1;
            case 4 -> 2;
            default -> -1;
        };
    }
}