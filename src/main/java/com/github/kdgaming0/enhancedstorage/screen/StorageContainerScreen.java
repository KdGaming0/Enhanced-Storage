package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.api.screen.IScreenAccessor;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.AbstractContainerScreen;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.gui.component.EditDialogComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.storage.*;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class StorageContainerScreen extends AbstractContainerScreen<ChestMenu> implements IHighlightClipProvider {

    private final StorageKey openKey;
    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout = new StorageOverlayLayout();

    private final Map<Long, int[]> topSlotClipRects = new HashMap<>();

    private final boolean rrvLoaded = FabricLoader.getInstance().isModLoaded("rrv");
    private final Map<Integer, ItemStack> liveMatchStacks = new HashMap<>();
    private final Map<Integer, Boolean> liveMatchResults = new HashMap<>();
    private String liveMatchQuery = "";
    private static final long SEARCH_REBUILD_DELAY_MS = 150;
    private long searchRebuildDueAt = -1;
    private boolean autoScrolledToOpenCard = false;
    private EditDialogComponent renameDialog;

    public StorageContainerScreen(ChestMenu menu, Inventory inventory, Component title, StorageKey openKey) {
        super(menu, inventory, title);
        this.openKey = openKey;
        this.state.setOpenKey(openKey);
    }

    private static long cordKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static boolean isRrvWidget(Object o) {
        if (o instanceof HoverMaskedRenderable) {
            return true; // treat wrappers as RRV widgets so we can re-process them
        }
        return o.getClass().getName().startsWith("cc.cassian.rrv");
    }

    @Override
    protected void init() {
        super.init();

        if (openKey.type() == StorageKey.Type.RIFT) {
            StorageCaptureHandler.discoverRiftPages(this.getTitle());
        }

        layout.build(this, this.font, this.width, this.height, state, openKey, this.menu.getRowCount(),
                this::onPageCardClicked, this::onSearchChanged, this::runToolkitCommand);

        if (!autoScrolledToOpenCard) {
            layout.scrollLiveCardIntoView();
            autoScrolledToOpenCard = true;
        }

        SpriteComponent inventory = layout.getInventoryPanel();
        SpriteComponent overview = layout.getOverviewPanel(); // may be null (Rift, or overview card disabled)

        int storageLeft = layout.getMainBackgroundX();
        int boxTop = layout.getMainBackgroundY();
        int storageRight = storageLeft + layout.getMainBackgroundWidth();

        int invLeft = inventory.getTotalX();
        int invRight = inventory.getTotalX() + inventory.getWidth();
        int invBottom = inventory.getTotalY() + inventory.getHeight();

        int bottomLeft = (overview != null) ? Math.min(overview.getTotalX(), invLeft) : invLeft;
        int bottomRight = (overview != null) ? Math.max(overview.getTotalX() + overview.getWidth(), invRight) : invRight;
        int boxBottom = (overview != null) ? Math.max(overview.getTotalY() + overview.getHeight(), invBottom) : invBottom;

        int boxLeft = Math.min(storageLeft, bottomLeft);
        int boxRight = Math.max(storageRight, bottomRight);

        var accessor = (AbstractContainerScreenAccessor) this;
        accessor.enhancedstorage$setImageWidth(boxRight - boxLeft);
        accessor.enhancedstorage$setImageHeight(boxBottom - boxTop);

        // Set the bounds of the overlay so other mods get the correct size to anchor around
        this.leftPos = boxLeft;
        this.topPos = boxTop;

        syncSlotPositions();

        state.onStorageScreenOpened();
    }

    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }

    /**
     * Calculates what counts as "inside" and only return true if the click is outside the overlay visual parts.
     */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (renameDialog != null) return false;
        return !isInsideOverlay(mx, my);
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        syncSlotPositions();
        super.extractContents(guiGraphics, mouseX, mouseY, a);
        if (rrvLoaded) {
            RRVCompat.renderHighlightAbove(this, guiGraphics, mouseX, mouseY, a);
        }
        if (renameDialog != null) {
            renameDialog.extractRenderStateBase(guiGraphics, mouseX, mouseY, a, this.width, this.height);
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (rrvLoaded) {
            sinkForeignRrvWidgets(mouseX, mouseY);
        }
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        // Rendering RRV's overlay early, in the background stratum, so everything this screen draws appears on top of it and not the other way around.
        if (rrvLoaded) {
            RRVCompat.renderOverlayBelow(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // Makes the Item list buttons from RRV render behind the menu instead of on top / block the mouse hovered from reaching the button
    private void sinkForeignRrvWidgets(int mouseX, int mouseY) {
        if (!(this instanceof IScreenAccessor accessor)) return;

        List<Renderable> renderables = accessor.uilib$getRenderables();
        List<Renderable> rrvWidgets = new ArrayList<>();

        renderables.removeIf(r -> {
            if (isRrvWidget(r)) {
                Renderable original = (r instanceof HoverMaskedRenderable m) ? m.delegate() : r;
                // RRV removed it from children => it's stale, let it die
                if (original instanceof GuiEventListener l && !this.children().contains(l)) {
                    return true; // remove and don't re-add
                }
                rrvWidgets.add(original);
                return true;
            }
            return false;
        });

        if (rrvWidgets.isEmpty()) return;

        boolean covered = isInsideOverlay(mouseX, mouseY);
        List<Renderable> toInsert = new ArrayList<>(rrvWidgets.size());
        for (Renderable r : rrvWidgets) {
            toInsert.add(covered ? new HoverMaskedRenderable(this, r) : r);
        }
        renderables.addAll(0, toInsert);
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

            // Clip rect stays in SCREEN space: isHovering compares it against absolute mouse coords, and scissor
            // consumers subtract (leftPos, topPos) themselves because enableScissor transforms its rect by the current pose.
            int clipLeft = Math.max(absX, viewport.getX());
            int clipTop = Math.max(absY, viewport.getY());
            int clipRight = Math.min(absX + 16, viewport.getX() + viewport.getWidth());
            int clipBottom = Math.min(absY + 16, viewport.getY() + viewport.getHeight());

            // Slot fields are RELATIVE to (leftPos, topPos) now.
            int relX = absX - offX;
            int relY = absY - offY;
            slot.x = relX;
            slot.y = relY;

            // Key must match what getHighlightClip looks up: cordKey(slot.x, slot.y),
            // which is now relative. The stored rect stays absolute (screen space).
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
        if (overview == null) {
            // When the Storage Overview card is disabled, we push every container slot offscreen so it doesn't render.
            int slots = this.menu.getRowCount() * 9;
            for (int i = 0; i < slots; i++) {
                Slot slot = this.menu.slots.get(i);
                slot.x = -9999;
                slot.y = -9999;
            }
            return;
        }

        final int offX = this.leftPos;
        final int offY = this.topPos;

        // Match the cached-item layout in StorageOverlayLayout:
        // startX = 7, rowYs = {14, 42, 60}, item drawn at (+1, +1)
        int startX = overview.getTotalX() + 7 + 1;
        int baseY = overview.getTotalY();
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

    private void onSearchChanged() {
        // Debounce: a full rebuild per keystroke is expensive, so wait for a short pause in typing.
        searchRebuildDueAt = Util.getMillis() + SEARCH_REBUILD_DELAY_MS;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchRebuildDueAt >= 0 && Util.getMillis() >= searchRebuildDueAt) {
            searchRebuildDueAt = -1;
            this.rebuildWidgets();

            var box = layout.getSearchBox();
            if (box != null) {
                this.setFocused(box);
                box.setFocused(true);
            }
        }
    }

    private boolean liveSlotMatches(Slot slot) {
        String query = state.getSearchQuery();
        if (!query.equals(liveMatchQuery)) {
            liveMatchStacks.clear();
            liveMatchResults.clear();
            liveMatchQuery = query;
        }
        ItemStack prev = liveMatchStacks.get(slot.index);
        if (prev == null || !ItemStack.matches(prev, slot.getItem())) {
            liveMatchStacks.put(slot.index, slot.getItem().copy());
            liveMatchResults.put(slot.index, ItemSearch.matches(slot.getItem(), query));
        }
        return liveMatchResults.get(slot.index);
    }

    private PageCardComponent cardTitleAt(double mx, double my) {
        if (!isOverPageOverview(mx, my)) return null;
        for (PageCardComponent card : layout.getPageCards()) {
            if (card.isOverTitle(mx, my)) return card;
        }
        return null;
    }

    private void openRenameDialog(StorageKey key) {
        this.renameDialog = new EditDialogComponent(
                this.width, this.height, this.font, key,
                layout.defaultPositionOf(key),
                pos -> nameOfCardAtPosition(key, pos),
                this::onRenameSave,
                this::closeRenameDialog,
                () -> onRenameReset(key));

        var box = this.renameDialog.getNameBox();
        this.setFocused(box);
        box.setFocused(true);
    }

    private @Nullable String nameOfCardAtPosition(StorageKey editingKey, int position) {
        return StorageOrder.getInstance().all().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(editingKey) && entry.getValue() == position)
                .map(entry -> {
                    StorageKey otherKey = entry.getKey();
                    return StorageNames.getInstance().get(otherKey).orElse(otherKey.displayName());
                })
                .findFirst()
                .orElse(null);
    }

    private void onRenameSave(String name, String position) {
        StorageKey key = (renameDialog != null) ? renameDialog.getKey() : null;
        if (key != null) {
            StorageNames.getInstance().set(key, name);
            StorageNames.getInstance().saveToDisk();

            Integer pos = null;
            String trimmed = position == null ? "" : position.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("-")) {
                try {
                    pos = Integer.valueOf(trimmed);
                } catch (NumberFormatException ignored) {
                    // validation should prevent this
                }
            }
            StorageOrder.getInstance().set(key, pos);
            StorageOrder.getInstance().saveToDisk();
        }
        closeRenameDialog();
        refreshAfterRename();
    }

    private void onRenameReset(StorageKey key) {
        StorageNames.getInstance().clear(key);
        StorageNames.getInstance().saveToDisk();
        StorageOrder.getInstance().clear(key);
        StorageOrder.getInstance().saveToDisk();
        closeRenameDialog();
        refreshAfterRename();
    }

    private void closeRenameDialog() {
        this.renameDialog = null;
        this.setFocused(null);
    }

    private void refreshAfterRename() {
        Minecraft.getInstance().schedule(this::rebuildWidgets);
    }

    private void onPageCardClicked(StorageKey key) {
        if (key.equals(openKey)) return;

        String command = switch (key.type()) {
            case ENDER_CHEST, RIFT -> "ec " + key.page();
            case BACKPACK -> "backpack " + key.page();
            case STORAGE_INDEX -> null;
        };
        if (command == null) return;

        state.beginNavigation();
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private void onSettingsClicked() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(MidnightConfig.getScreen(this, EnhancedStorage.MOD_ID));
    }

    private void runToolkitCommand(String command) {
        state.beginNavigation();
        assert Minecraft.getInstance().player != null;
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
        if (localY >= 14 && localY < 32) row = 0; // ender chest pages
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

    private boolean isOverPageOverview(double mouseX, double mouseY) {
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview == null) return false;
        return mouseX >= overview.getX() && mouseX < overview.getX() + overview.getWidth()
                && mouseY >= overview.getY() && mouseY < overview.getY() + overview.getHeight();
    }

    // Call to reset the Storage Overlay if the cache changes while it open
    public void rebuildForProfileChange() {
        closeRenameDialog();
        autoScrolledToOpenCard = false;
        this.rebuildWidgets();
    }

    private PageCardComponent cachedCardAt(double mx, double my) {
        // Don't click cards that are scrolled outside the viewport
        if (!isOverPageOverview(mx, my)) return null;
        for (PageCardComponent card : layout.getPageCards()) {
            if (card == layout.getOpenCard()) continue;  // live card: title only
            if (!card.isCached()) continue;              // "Click To Open" cards: title only
            if (inRect(mx, my, card.getTotalX(), card.getTotalY(),
                    card.getWidth(), card.getHeight())) {
                return card;
            }
        }
        return null;
    }

    public void refreshCards() {
        this.rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (renameDialog != null) return true;
        if (isOverPageOverview(mouseX, mouseY)) {
            ScrollContainerWidget overview = layout.getPageOverview();
            if (overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        // Slots parked offscreen (nav row, hidden index rows) still cost full item extraction
        // plus every other mod's slot hooks — skip them entirely.
        if (slot.y < -9000) return;

        int containerSlots = this.menu.getRowCount() * 9;

        if (slot.index < containerSlots && openKey.type() != StorageKey.Type.STORAGE_INDEX) {
            ScrollContainerWidget viewport = layout.getPageOverview();
            graphics.enableScissor(
                    viewport.getX() - this.leftPos, viewport.getY() - this.topPos,
                    viewport.getX() + viewport.getWidth() - this.leftPos,
                    viewport.getY() + viewport.getHeight() - this.topPos);
            boolean searching = state.isSearching();
            boolean match = searching && liveSlotMatches(slot);

            if (searching && match) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8033CC33);
            }

            super.extractSlot(graphics, slot, mouseX, mouseY);

            if (searching && !match) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xB0101010);
            }

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
        // If the Rename Dialog is open, give it all the mouse clicks
        if (renameDialog != null) {
            for (IWidget widget : renameDialog.getWidgets()) {
                if (widget.mouseClicked(event, doubleClick)) {
                    renameDialog.getNameBox().setFocused(false);
                    renameDialog.getPositionBox().setFocused(false);
                    //noinspection ConstantValue
                    if (widget instanceof GuiEventListener l) {
                        this.setFocused(l);
                        if (!l.isFocused()) l.setFocused(true);
                    }
                    return true;
                }
            }
            if (event.button() == 0 && !renameDialog.isOverPanel(event.x(), event.y())) {
                closeRenameDialog();
            }
            return true;
        }

        int[] tb = layout.getToolkitButtonBounds();
        if (tb != null && (event.button() == 0 || event.button() == 1)
                && inRect(event.x(), event.y(), tb[0], tb[1], tb[2], tb[3])) {
            runToolkitCommand(event.button() == 0 ? "farmingtoolkit" : "huntingtoolkit");
            return true;
        }

        int[] sb = layout.getSettingsButtonBounds();
        if (sb != null && event.button() == 0
                && inRect(event.x(), event.y(), sb[0], sb[1], sb[2], sb[3])) {
            onSettingsClicked();
            return true;
        }

        int[] thb = layout.getThemeButtonBounds();
        if (thb != null && event.button() == 0
                && inRect(event.x(), event.y(), thb[0], thb[1], thb[2], thb[3])) {
            StorageOverlayLayout.cycleTheme();
            this.rebuildWidgets();
            return true;
        }

        // Makes Right-click opens the edit dialog: on the title of any card, or anywhere on a cached (non-live) card.
        if (event.button() == 1) {
            PageCardComponent target = cardTitleAt(event.x(), event.y());
            if (target == null) target = cachedCardAt(event.x(), event.y());
            if (target != null) {
                openRenameDialog(target.getKey());
                return true;
            }
        }

        boolean coveredByGui = isInsideOverlay(event.x(), event.y());

        // 1. Widgets get first shot
        for (GuiEventListener child : this.children()) {
            if (coveredByGui && isRrvWidget(child)) continue;
            if (child.mouseClicked(event, doubleClick)) {
                this.setFocused(child);
                if (!child.isFocused()) {
                    child.setFocused(true);   // screen ref was stale; force the widget flag
                }
                if (event.button() == 0) this.setDragging(true);
                return true;
            }
        }

        // 2. Index mode: left-click on an item in the storage overview navigates to that page
        if (openKey.type() == StorageKey.Type.STORAGE_INDEX && event.button() == 0) {
            Slot slot = findHoveredContainerSlot(event.x(), event.y());
            if (slot != null && slot.hasItem()) {
                var key = StorageKey.fromIndexItem(slot.getItem().getHoverName());
                if (key.isPresent()) {
                    onPageCardClicked(key.get());
                    return true;
                }
            }
        }

        // 3. Browse mode: left-clicking on the storage overview panel area runs /storage to open the storage page
        if (event.button() == 0
                && openKey.type() != StorageKey.Type.STORAGE_INDEX
                && this.menu.getCarried().isEmpty()
                && isOverOverviewPanel(event.x(), event.y())) {
            indexItemAt(event.x(), event.y()).ifPresentOrElse(
                    this::onPageCardClicked,
                    () -> {
                        state.beginNavigation();
                        assert Minecraft.getInstance().player != null;
                        Minecraft.getInstance().player.connection.sendCommand("storage");
                    });
            return true;
        }

        // 4. Screen's own mouseClicked runs its child loop again before slot logic, which would give the hidden RRV widgets a second chance at the click.
        // Pull them out of the children list for the duration of the call, and restore them afterward.
        if (coveredByGui && this instanceof IScreenAccessor accessor) {
            List<GuiEventListener> children = accessor.uilib$getChildren();
            List<GuiEventListener> pulled = new ArrayList<>();
            children.removeIf(c -> {
                if (isRrvWidget(c)) {
                    pulled.add(c);
                    return true;
                }
                return false;
            });
            try {
                return super.mouseClicked(event, doubleClick);
            } finally {
                children.addAll(pulled);
            }
        }

        // 5. Everything else: real slot clicks (open page + player inventory)
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (renameDialog != null) {
            if (event.isEscape()) {
                closeRenameDialog();
                return true;
            }
            if (event.isConfirmation()) {
                if (renameDialog.getPositionBox()
                        .validateInput(renameDialog.getPositionBox().getValue()).isEmpty()) {
                    onRenameSave(renameDialog.getNameBox().getValue(),
                            renameDialog.getPositionBox().getValue());
                }
                return true;
            }
            renameDialog.getFocusedBox().keyPressed(event);
            return true;
        }

        var box = layout.getSearchBox();
        // Stops E from closing the overlay when searching
        if (box != null && box.isFocused() && !event.isEscape()) {
            if (box.keyPressed(event)) return true;
            return true;
        }
        return super.keyPressed(event);
    }

    private record HoverMaskedRenderable(StorageContainerScreen screen, Renderable delegate) implements Renderable {
        @Override
        public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            if (screen.isInsideOverlay(mouseX, mouseY)) {
                delegate.extractRenderState(graphics, -9999, -9999, a);  // Suppress when covered by this screen
            } else {
                delegate.extractRenderState(graphics, mouseX, mouseY, a); // Uncovered, do nothing
            }
        }
    }
}