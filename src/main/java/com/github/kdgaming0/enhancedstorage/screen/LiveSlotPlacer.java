package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.IndexGrid;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout.SLOT_SIZE;
import static com.github.kdgaming0.enhancedstorage.gui.component.page.StorageSlotComponent.ITEM_INSET;

/**
 * Moves the real container and inventory slots onto the overlay and records, per page slot,
 * the part that is visible inside the page viewport.
 *
 * <p>Slot coordinates are relative to (leftPos, topPos), because vanilla translates by that before
 * drawing slots. Clip rects are in screen space, because hover checks compare them to raw mouse
 * coordinates.</p>
 */
final class LiveSlotPlacer {

    // Hidden container slots (navigation rows, unused index rows) are moved here.
    private static final int PARKED = -9999;
    private static final int ITEM_SIZE = 16;

    private final ChestMenu menu;
    private final StorageOverlayLayout layout;
    private final boolean indexPage;
    private final Map<Long, Rect> clipRects = new HashMap<>();

    private @Nullable Rect viewport;

    LiveSlotPlacer(ChestMenu menu, StorageOverlayLayout layout, StorageKey openKey) {
        this.menu = menu;
        this.layout = layout;
        this.indexPage = openKey.type() == StorageKey.Type.STORAGE_INDEX;
    }

    static boolean isParked(Slot slot) {
        return slot.y == PARKED;
    }

    private static void park(Slot slot) {
        slot.x = PARKED;
        slot.y = PARKED;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Visible part of the slot at these relative coordinates, or null if it isn't clipped.
     */
    @Nullable
    Rect clipAt(int slotX, int slotY) {
        return clipRects.get(key(slotX, slotY));
    }

    /**
     * Screen-space page viewport from the last sync, or null on the storage index page.
     */
    @Nullable
    Rect viewport() {
        return viewport;
    }

    void sync(int offX, int offY) {
        clipRects.clear();
        viewport = null;
        if (indexPage) {
            placeIndexSlots(offX, offY);
        } else {
            placePageSlots(offX, offY);
        }
        placePlayerSlots(offX, offY);
    }

    private void placePageSlots(int offX, int offY) {
        ScrollContainerWidget widget = layout.getPageOverview();
        if (widget == null) return;

        Rect view = new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        this.viewport = view;

        PageCardComponent openCard = layout.getOpenCard();
        if (openCard == null) {
            int containerSlots = menu.getRowCount() * 9;
            for (int i = 0; i < containerSlots; i++) {
                park(menu.slots.get(i));
            }
            return;
        }

        int originX = openCard.slotGridScreenX() + ITEM_INSET;
        int originY = openCard.slotGridScreenY() + ITEM_INSET;
        int pageWidth = openCard.slotsAcross();

        int containerSlots = menu.getRowCount() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);

            // The top row holds the server's navigation buttons.
            if (i < 9) {
                park(slot);
                continue;
            }

            int visible = i - 9;
            int absX = originX + (visible % pageWidth) * SLOT_SIZE;
            int absY = originY + (visible / pageWidth) * SLOT_SIZE;
            slot.x = absX - offX;
            slot.y = absY - offY;

            int left = Math.max(absX, view.x());
            int top = Math.max(absY, view.y());
            int right = Math.min(absX + ITEM_SIZE, view.right());
            int bottom = Math.min(absY + ITEM_SIZE, view.bottom());
            clipRects.put(key(slot.x, slot.y),
                    new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
        }
    }

    private void placeIndexSlots(int offX, int offY) {
        int containerSlots = menu.getRowCount() * 9;
        SpriteComponent overview = layout.getOverviewPanel();

        if (overview == null) {
            // With the Storage Overview card disabled there is nowhere to show the index slots.
            for (int i = 0; i < containerSlots; i++) {
                park(menu.slots.get(i));
            }
            return;
        }

        int panelX = overview.getTotalX();
        int panelY = overview.getTotalY();
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);

            int visRow = IndexGrid.visibleRowOfMenuRow(i / 9);
            if (visRow < 0) {
                park(slot);
                continue;
            }

            slot.x = panelX + IndexGrid.cellX(i % 9) + ITEM_INSET - offX;
            slot.y = panelY + IndexGrid.rowY(visRow) + ITEM_INSET - offY;
        }
    }

    private void placePlayerSlots(int offX, int offY) {
        SpriteComponent inventory = layout.getInventoryPanel();
        if (inventory == null) return;

        // Offsets follow the vanilla inventory texture: 3 main rows, then a gap, then the hotbar.
        int invX = inventory.getTotalX() + 8;
        int mainY = inventory.getTotalY() + 15;
        int hotbarY = mainY + 3 * SLOT_SIZE + 4;

        int base = menu.slots.size() - 36;
        for (int i = 0; i < 36; i++) {
            Slot slot = menu.slots.get(base + i);
            int y = i < 27 ? mainY + (i / 9) * SLOT_SIZE : hotbarY;
            slot.x = invX + (i % 9) * SLOT_SIZE - offX;
            slot.y = y - offY;
        }
    }
}