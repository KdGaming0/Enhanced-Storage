package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.gui.AbstractContainerScreen;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.storage.StorageCache;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StorageContainerScreen extends AbstractContainerScreen<ChestMenu> implements IHighlightClipProvider {

    private final StorageKey openKey;
    private final StorageOverlayState state = new StorageOverlayState();
    private final StorageOverlayLayout layout = new StorageOverlayLayout();

    private final Map<Long, int[]> topSlotClipRects = new HashMap<>();

    private static long cordKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public StorageContainerScreen(ChestMenu menu, Inventory inventory, Component title, StorageKey openKey) {
        super(menu, inventory, title);
        this.openKey = openKey;
        this.state.setOpenKey(openKey);
    }

    @Override
    protected void init() {
        super.init();

        layout.build(this, this.font, this.width, this.height, state, openKey, this.menu.getRowCount(), this::onPageCardClicked);

        SpriteComponent inventory = layout.getInventoryPanel();
        SpriteComponent overview = layout.getOverviewPanel();

        int storageLeft = layout.getMainBackgroundX();
        int boxTop = layout.getMainBackgroundY();
        int storageRight = storageLeft + layout.getMainBackgroundWidth();

        int bottomLeft = Math.min(overview.getTotalX(), inventory.getTotalX());
        int bottomRight = Math.max(overview.getTotalX() + overview.getWidth(), inventory.getTotalX() + inventory.getWidth());
        int boxBottom = Math.max(overview.getTotalY() + overview.getHeight(), inventory.getTotalY() + inventory.getHeight());

        int boxLeft = Math.min(storageLeft,  bottomLeft);
        int boxRight = Math.max(storageRight, bottomRight);

        var accessor = (AbstractContainerScreenAccessor) this;
        accessor.enhancedstorage$setImageWidth(boxRight - boxLeft);
        accessor.enhancedstorage$setImageHeight(boxBottom - boxTop);

        // Set the bounds of the overlay so other mods get the correct size to anchor around
        this.leftPos = boxLeft;
        this.topPos  = boxTop;

        syncSlotPositions();
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {}

    /**
     * Calculates what counts as "inside" and only return true if the click is outside the overlay visual parts.
     */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return !isInsideOverlay(mx, my);
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        syncSlotPositions();
        super.extractContents(guiGraphics, mouseX, mouseY, a);
    }

    private boolean isInsideOverlay(double mx, double my) {
        // Page overview panel
        ScrollContainerWidget pageOverview = layout.getPageOverview();
        if (pageOverview != null && inRect(mx, my,
                pageOverview.getX(), pageOverview.getY(),
                pageOverview.getWidth(), pageOverview.getHeight())) {
            return true;
        }

        // Player inventory panel
        SpriteComponent inventory = layout.getInventoryPanel();
        if (inventory != null && inRect(mx, my,
                inventory.getTotalX(), inventory.getTotalY(),
                inventory.getWidth(), inventory.getHeight())) {
            return true;
        }

        // Storage overview panel
        SpriteComponent overviewPanel = layout.getOverviewPanel();
        //noinspection RedundantIfStatement
        if (overviewPanel != null && inRect(mx, my,
                overviewPanel.getTotalX(), overviewPanel.getTotalY(),
                overviewPanel.getWidth(), overviewPanel.getHeight())) {
            return true;
        }

        return false;
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void syncSlotPositions() {
        topSlotClipRects.clear();

        if (openKey.type() == StorageKey.Type.STORAGE_INDEX) {
            syncIndexSlotPositions();
        } else {
            syncPageSlotPositions();
        }

        syncPlayerSlotPositions();
    }

    private void syncPageSlotPositions() {
        PageCardComponent openCard = layout.getOpenCard();
        if (openCard == null) return;

        ScrollContainerWidget viewport = layout.getPageOverview();
        if (viewport == null) return;

        // Offset values so slots render correctly. Vanilla AbstractContainerScreen translates the render matrix by
        // (leftPos, topPos) before drawing slots, so slot.x/slot.y must be stored RELATIVE to that offset.
        // We set leftPos/topPos to getMainBackgroundX/Y (instead of 0,0) so other mods — e.g. ones adding buttons
        // around the inventory — can read the standard contract to know this menu's bounds.
        final int offX = this.leftPos;
        final int offY = this.topPos;

        int rows = this.menu.getRowCount();
        int containerSlots = rows * 9;

        int cardX = openCard.getTotalX();
        int cardY = openCard.getTotalY();
        int originalX = cardX + StorageOverlayLayout.CARD_BORDER + 1;
        int originalY = cardY + font.lineHeight + 5;

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = this.menu.slots.get(i);

            // Place the first 9 slots (top row) offscreen so the navigation buttons are not visible in the page when you view it
            if (i < 9) {
                slot.x = -9999;
                slot.y = -9999;
                continue;
            }

            // Re-index so the first visible slot (i=9) lands at grid position 0.
            int visible = i - 9;

            // Absolute (screen-space) position where this slot should visually land.
            int absX = originalX + (visible % 9) * 18;
            int absY = originalY + (visible / 9) * 18;

            // Clip rect stays in SCREEN space — scissor is never translated.
            int clipLeft   = Math.max(absX, viewport.getX());
            int clipTop    = Math.max(absY, viewport.getY());
            int clipRight  = Math.min(absX + 16, viewport.getX() + viewport.getWidth());
            int clipBottom = Math.min(absY + 16, viewport.getY() + viewport.getHeight());

            // Slot fields are RELATIVE to (leftPos, topPos) now.
            int relX = absX - offX;
            int relY = absY - offY;
            slot.x = relX;
            slot.y = relY;

            // Key must match what getHighlightClip looks up: cordKey(slot.x, slot.y),
            // which is now relative. The stored rect stays absolute (scissor is screen-space).
            topSlotClipRects.put(cordKey(relX, relY), new int[]{clipLeft, clipTop, clipRight, clipBottom});
        }
    }

    private void syncPlayerSlotPositions() {
        SpriteComponent inventory = layout.getInventoryPanel();
        if (inventory == null) return;

        final int offX = this.leftPos;
        final int offY = this.topPos;

        int invX = inventory.getTotalX() + 8;
        int invMainY = inventory.getTotalY() + 14 + 1;
        int hotbarY = invMainY + 3 * 18 + 4;

        int base = this.menu.slots.size() - 36;
        for (int i = 0; i < 27; i++) {
            Slot slot = this.menu.slots.get(base + i);
            slot.x = (invX + (i % 9) * 18) - offX;
            slot.y = (invMainY + (i / 9) * 18) - offY;
        }
        for (int i = 0; i < 9; i++) {
            Slot s = this.menu.slots.get(base + 27 + i);
            s.x = (invX + i * 18) - offX;
            s.y = hotbarY - offY;
        }
    }

    private void syncIndexSlotPositions() {
        SpriteComponent overview = layout.getOverviewPanel();
        if (overview == null) return;

        final int offX = this.leftPos;
        final int offY = this.topPos;

        // Match the cached-item layout in StorageOverlayLayout:
        // startX = 7, rowYs = {14, 42, 60}, item drawn at (+1, +1)
        int startX = overview.getTotalX() + 7 + 1;
        int baseY  = overview.getTotalY();
        final int[] rowYs = {14, 42, 60};

        int containerSlots = this.menu.getRowCount() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = this.menu.slots.get(i);

            int menuRow = i / 9;
            int visRow = switch (menuRow) {
                case 1 -> 0;
                case 3 -> 1;
                case 4 -> 2;
                default -> -1;
            };

            if (visRow < 0) {
                // Nav/filler rows: placed offscreen
                slot.x = -9999;
                slot.y = -9999;
                continue;
            }

            slot.x = (startX + (i % 9) * 18) - offX;
            slot.y = (baseY + rowYs[visRow] + 1) - offY;
        }
    }

    private void onPageCardClicked(StorageKey key) {
        if (key.equals(openKey)) return;

        String command = switch (key.type()) {
            case ENDER_CHEST -> "ec " + key.page();
            case BACKPACK    -> "backpack " + key.page();
            case STORAGE_INDEX -> null;
        };
        if (command == null) return;

        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private boolean isOverOverviewPanel(double mx, double my) {
        SpriteComponent overview = layout.getOverviewPanel();
        return overview != null && inRect(mx, my,
                overview.getTotalX(), overview.getTotalY(),
                overview.getWidth(), overview.getHeight());
    }

    private Optional<StorageKey> indexItemAt(double mx, double my) {
        SpriteComponent overview = layout.getOverviewPanel();
        if (overview == null) return Optional.empty();

        // Same grid the layout draws cached index items on: startX=7, rowYs={14,42,60}, 18px cells
        int localX = (int) (mx - overview.getTotalX()) - 7;
        int localY = (int) (my - overview.getTotalY());
        if (localX < 0 || localX >= 9 * 18) return Optional.empty();

        int col = localX / 18;
        int row;
        if      (localY >= 14 && localY < 32) row = 0; // ender chest pages
        else if (localY >= 42 && localY < 60) row = 1; // backpacks 1-9
        else if (localY >= 60 && localY < 78) row = 2; // backpacks 10-18
        else return Optional.empty();

        int idx = row * 9 + col;

        return StorageCache.getInstance().get(new StorageKey(StorageKey.Type.STORAGE_INDEX, 0))
                .map(StorageCache.CachedPage::items)
                .filter(items -> idx < items.size())
                .map(items -> items.get(idx))
                .filter(stack -> !stack.isEmpty())
                .flatMap(stack -> StorageKey.fromIndexItem(stack.getHoverName()));
    }

    private Slot findHoveredContainerSlot(double mx, double my) {
        int containerSlots = this.menu.getRowCount() * 9;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = this.menu.slots.get(i);
            if (this.isHovering(slot.x, slot.y, 16, 16, mx, my)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null && overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        int containerSlots = this.menu.getRowCount() * 9;

        if (slot.index < containerSlots && openKey.type() != StorageKey.Type.STORAGE_INDEX) {
            ScrollContainerWidget viewport = layout.getPageOverview();
            graphics.enableScissor(
                    viewport.getX() - this.leftPos, viewport.getY() - this.topPos,
                    viewport.getX() + viewport.getWidth()  - this.leftPos,
                    viewport.getY() + viewport.getHeight() - this.topPos);
            super.extractSlot(graphics, slot, mouseX, mouseY);
            graphics.disableScissor();
        } else {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        int[] clip = topSlotClipRects.get(cordKey(left, top));
        if (clip != null) {
            if (clip[2] <= clip[0] || clip[3] <= clip[1]) return false;
            return xm >= clip[0] && xm < clip[2] && ym >= clip[1] && ym < clip[3];
        }
        return super.isHovering(left, top, w, h, xm, ym);
    }

    @Override
    public int[] enhancedstorage$getHighlightClip(Slot slot) {
        return topSlotClipRects.get(cordKey(slot.x, slot.y));
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // 1. Widgets (scrollbar, search box) get first shot for click events
        for (GuiEventListener child : this.children()) {
            if (child.isMouseOver(event.x(), event.y()) && child.mouseClicked(event, doubleClick)) {
                this.setFocused(child);
                if (event.button() == 0) this.setDragging(true);
                return true;
            }
        }

        // 2. Index/Storage Overview mode: use container slots for navigation
        if (openKey.type() == StorageKey.Type.STORAGE_INDEX) {
            Slot slot = findHoveredContainerSlot(event.x(), event.y());
            if (slot != null) {
                if (event.button() == 0) {
                    if (slot.hasItem()) {
                        StorageKey.fromIndexItem(slot.getItem().getHoverName())
                                .ifPresent(this::onPageCardClicked);
                    }
                } else if (event.button() == 1) {
                    this.slotClicked(slot, slot.index, 1, ContainerInput.PICKUP);
                }
                return true;
            }
        }

        // 3. Browse mode: clicks on the Storage Overview panel
        if (event.button() == 0
                && openKey.type() != StorageKey.Type.STORAGE_INDEX
                && this.menu.getCarried().isEmpty()
                && isOverOverviewPanel(event.x(), event.y())) {

            StorageKey clicked = indexItemAt(event.x(), event.y()).orElse(null);
            if (clicked != null) {
                onPageCardClicked(clicked); // jump straight to that page you clicked on
            } else {
                Minecraft.getInstance().player.connection.sendCommand("storage");
            }
            return true;
        }

        // 4. Everything else: real slot clicks (open page + player inventory)
        return super.mouseClicked(event, doubleClick);
    }
}
