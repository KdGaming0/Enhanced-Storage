package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.api.screen.IScreenAccessor;
import com.daqem.uilib.gui.AbstractContainerScreen;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.gui.*;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.StorageCaptureHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class StorageContainerScreen extends AbstractContainerScreen<ChestMenu> implements IHighlightClipProvider {

    private final StorageKey openKey;
    private final StorageOverlayState state = StorageOverlayState.session();
    private final OverlayActions actions = OverlayActions.create(this, state, this::onPageCardClicked, this::enterEditMode, this::rebuildWidgets);
    private final StorageOverlayLayout layout = new StorageOverlayLayout();
    private final LiveSlotPlacer slotPlacer;
    private final LiveSlotSearch liveSearch = new LiveSlotSearch();
    private final boolean rrvLoaded = FabricLoader.getInstance().isModLoaded("rrv");
    private boolean autoScrolledToOpenCard = false;
    private static final long DEFERRED_CLOSE_MS = 2000;
    private long deferredCloseAt = 0;

    public StorageContainerScreen(ChestMenu menu, Inventory inventory, Component title, StorageKey openKey) {
        super(menu, inventory, title);
        this.openKey = openKey;
        this.state.setOpenKey(openKey);
        this.slotPlacer = new LiveSlotPlacer(menu, layout, openKey);
    }

    private static boolean isRrvWidget(Object o) {
        if (o instanceof HoverMaskedRenderable) {
            return true; // treat wrappers as RRV widgets so we can re-process them
        }
        return o.getClass().getName().startsWith("cc.cassian.rrv");
    }

    /**
     * Switches the window to the overlay's own GUI scale before anything lays out. {@code width} and
     * {@code height} were assigned by our caller before the scale changed, so they have to be re-read.
     * <p>
     * Every rebuild funnels through here, so resizes, fullscreen toggles and live config edits need no
     * code of their own — including window resizes, where vanilla resets the scale to the player's
     * setting on the way in and {@link OverlayScale#sync} puts ours back.
     */
    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        OverlayScale.sync(minecraft);
        this.width = minecraft.getWindow().getGuiScaledWidth();
        this.height = minecraft.getWindow().getGuiScaledHeight();

        super.init();

        if (openKey.type() == StorageKey.Type.RIFT) {
            StorageCaptureHandler.discoverRiftPages(this.getTitle());
        }

        layout.build(this, this.font, this.width, this.height, state, openKey, this.menu.getRowCount(), actions);

        if (!autoScrolledToOpenCard) {
            layout.scrollLiveCardIntoView();
            autoScrolledToOpenCard = true;
        }

        SpriteComponent inventory = layout.getInventoryPanel();
        SpriteComponent overview = layout.getOverviewPanel(); // may be null (Rift, or overview card disabled)

        int storageLeft = layout.getMainBackgroundX();
        int boxTop = layout.getMainBackgroundY();
        int storageRight = storageLeft + layout.getMainBackgroundWidth();
        Rect navigation = layout.getNavigationBounds();
        if (navigation != null) {
            storageLeft = Math.min(storageLeft, navigation.x());
            storageRight = Math.max(storageRight, navigation.right());
        }

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

        slotPlacer.sync(this.leftPos, this.topPos);

        state.onStorageScreenOpened();
    }

    /**
     * uilib's {@link com.daqem.uilib.gui.AbstractContainerScreen#children()} returns an immutable
     * {@code List.copyOf(...)}. REI's post-init handler calls {@code children().removeIf(...)} to strip the
     * recipe-book widget, which throws {@link UnsupportedOperationException} on an immutable list and crashes
     * the client. Returning a mutable copy lets external mutators (REI) operate on a throwaway list without
     * affecting our real widget state. super.children() already allocates a fresh list each call, so wrapping
     * it adds no meaningful overhead.
     */
    @Override
    public @NotNull List<? extends GuiEventListener> children() {
        return new ArrayList<>(super.children());
    }

    /**
     * {@code Minecraft.setScreen} calls this on the outgoing screen before initialising the incoming
     * one, so putting the player's scale back here means whatever comes next — the config screen, the
     * next storage page, the in-game HUD — lays out at the right size from its first frame.
     */
    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
        OverlayScale.restoreGameScale(Minecraft.getInstance());
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }

    /**
     * Calculates what counts as "inside" and only return true if the click is outside the overlay visual parts.
     */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return !isInsideOverlay(mx, my);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        CopyHighlight.setTarget(hoveredStack(mouseX, mouseY));
        layout.updateCopyFilter();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!LiveSlotPlacer.isParked(slot) && slot.hasItem()
                    && this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot.getItem();
            }
        }
        return layout.hoveredCachedStack(mouseX, mouseY);
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        slotPlacer.sync(this.leftPos, this.topPos);
        super.extractContents(guiGraphics, mouseX, mouseY, a);
        if (this.menu.getCarried().isEmpty()) layout.extractOverlayTooltip(guiGraphics, mouseX, mouseY);
        if (rrvLoaded) {
            RRVCompat.renderHighlightAbove(this, guiGraphics, mouseX, mouseY, a);
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
        List<GuiEventListener> children = accessor.uilib$getChildren();
        List<Renderable> rrvWidgets = new ArrayList<>();

        renderables.removeIf(r -> {
            if (!isRrvWidget(r)) return false;

            Renderable original = (r instanceof HoverMaskedRenderable m) ? m.delegate() : r;
            if (!(original instanceof GuiEventListener l) || children.contains(l)) {
                rrvWidgets.add(original);
            }
            return true;
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
        return Rect.contains(layout.getPageOverviewBounds(), mx, my)
                || Rect.contains(layout.getInventoryBounds(), mx, my)
                || Rect.contains(layout.getOverviewBounds(), mx, my)
                || Rect.contains(layout.getNavigationBounds(), mx, my);
    }

    /**
     * Called when the server closes this menu. During a page switch the next page's open packet is
     * normally right behind, so hold the overlay up instead of dropping to no screen for a frame.
     * If the next page hasn't opened within {@link #DEFERRED_CLOSE_MS}, containerTick closes it.
     *
     * @return true if the close was deferred and vanilla's handling should be skipped
     */
    public boolean deferCloseForNavigation(int containerId) {
        if (containerId != this.menu.containerId || !state.isNavigating()) return false;
        if (deferredCloseAt == 0) {
            EnhancedStorage.LOGGER.debug("Deferring container close during page switch");
            deferredCloseAt = Util.getMillis() + DEFERRED_CLOSE_MS;
        }
        return true;
    }

    // The server already closed this menu while a deferred close is pending, so clicks would target a dead container.
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, @NonNull ContainerInput containerInput) {
        if (deferredCloseAt != 0) return;
        super.slotClicked(slot, slotId, buttonNum, containerInput);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (deferredCloseAt != 0 && Util.getMillis() >= deferredCloseAt) {
            deferredCloseAt = 0;
            this.minecraft.player.clientSideCloseContainer();
            return;
        }
        layout.tick();
    }

    /**
     * A rebuild throws away every widget and clears focus. The search box instance itself survives
     * (the layout reuses it), so put focus back on it afterwards — otherwise a rebuild triggered
     * while the user is typing swallows the keystrokes that follow it.
     */
    @Override
    protected void rebuildWidgets() {
        var previous = layout.getSearchBox();
        boolean refocus = previous != null && (previous.isFocused() || this.getFocused() == previous);

        super.rebuildWidgets();

        var box = layout.getSearchBox();
        if (refocus && box != null) {
            this.setFocused(box);
            box.setFocused(true);
        }
    }

    // Opening a page with the arrow keys counts as keyboard input, after which vanilla focuses the first widget: the search box. This prevents that.
    @Override
    protected void setInitialFocus() {
    }

    private void onPageCardClicked(StorageKey key) {
        if (key.equals(openKey)) return;

        String command = switch (key.type()) {
            case ENDER_CHEST, RIFT -> "ec " + key.page();
            case BACKPACK -> "backpack " + key.page();
            case STORAGE_INDEX -> null;
        };
        if (command == null) return;

        OverlayActions.sendCommand(state, command);
    }

    private void enterEditMode() {
        // Prevent opening the edit screen while dragging an item.
        if (!this.menu.getCarried().isEmpty()) return;

        boolean rift = openKey.type() == StorageKey.Type.RIFT;
        this.minecraft.player.closeContainer();
        this.minecraft.setScreen(StorageOverlay.forEditMode(rift, true));
    }

    private boolean isOverOverviewPanel(double mx, double my) {
        return Rect.contains(layout.getOverviewBounds(), mx, my);
    }

    private Optional<StorageKey> indexItemAt(double mx, double my) {
        Rect bounds = layout.getOverviewBounds();
        if (bounds == null) return Optional.empty();

        int idx = IndexGrid.cellAt((int) (mx - bounds.x()), (int) (my - bounds.y()));
        if (idx < 0) return Optional.empty();

        return PageCache.getInstance().get(new StorageKey(StorageKey.Type.STORAGE_INDEX, 0))
                .map(PageCache.CachedPage::items)
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
        return Rect.contains(layout.getPageOverviewBounds(), mouseX, mouseY);
    }

    // Call to reset the Storage Overlay if the cache changes while it open
    public void rebuildForProfileChange() {
        autoScrolledToOpenCard = false;
        this.rebuildWidgets();
    }

    public void refreshCards() {
        this.rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollContainerWidget navigation = layout.getNavigationList();
        if (navigation != null && Rect.contains(layout.getNavigationBounds(), mouseX, mouseY)
                && navigation.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
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
        if (LiveSlotPlacer.isParked(slot)) return;

        Rect viewport = slotPlacer.viewport();
        if (viewport == null || slot.index >= this.menu.getRowCount() * 9) {
            if (CopyHighlight.isCopy(slot.getItem())) CopyHighlight.draw(graphics, slot.x, slot.y);
            super.extractSlot(graphics, slot, mouseX, mouseY);
            return;
        }

        graphics.enableScissor(
                viewport.x() - this.leftPos, viewport.y() - this.topPos,
                viewport.right() - this.leftPos, viewport.bottom() - this.topPos);

        boolean searching = state.isSearching();
        boolean match = searching && liveSearch.matches(slot, state.getSearchQuery());

        if (searching && match) {
            SearchHighlight.drawMatch(graphics, slot.x, slot.y);
        }
        if (CopyHighlight.isCopy(slot.getItem())) {
            CopyHighlight.draw(graphics, slot.x, slot.y);
        }

        super.extractSlot(graphics, slot, mouseX, mouseY);

        if (searching && !match) {
            SearchHighlight.drawNoMatch(graphics, slot.x, slot.y);
        }

        graphics.disableScissor();
    }

    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        Rect clip = slotPlacer.clipAt(left, top);
        if (clip != null) return clip.contains(xm, ym);
        return super.isHovering(left, top, w, h, xm, ym);
    }

    @Override
    public @Nullable Rect enhancedstorage$getHighlightClip(Slot slot) {
        return slotPlacer.clipAt(slot.x, slot.y);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        var box = layout.getSearchBox();
        if (box != null && (box.isFocused() || this.getFocused() == box) && !box.isMouseOver(event.x(), event.y())) {
            box.setFocused(false);
            this.setFocused(null);
        }
        if (layout.handleButtonClick(event)) return true;

        boolean coveredByGui = isInsideOverlay(event.x(), event.y());
        if (dispatchToWidgets(event, doubleClick, coveredByGui)) return true;
        if (handleIndexSlotClick(event)) return true;
        if (handleOverviewPanelClick(event)) return true;
        return dispatchToSlots(event, doubleClick, coveredByGui);
    }

    private boolean dispatchToWidgets(MouseButtonEvent event, boolean doubleClick, boolean coveredByGui) {
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
        return false;
    }

    /** On the live index page, left-clicking a page item navigates to that page. */
    private boolean handleIndexSlotClick(MouseButtonEvent event) {
        if (openKey.type() != StorageKey.Type.STORAGE_INDEX || event.button() != 0) return false;

        Slot slot = findHoveredContainerSlot(event.x(), event.y());
        if (slot == null || !slot.hasItem()) return false;

        Optional<StorageKey> key = StorageKey.fromIndexItem(slot.getItem().getHoverName());
        if (key.isEmpty()) return false;

        onPageCardClicked(key.get());
        return true;
    }

    /** Off the index page, left-clicking the cached overview opens the clicked page, or /storage when no item is hit. */
    private boolean handleOverviewPanelClick(MouseButtonEvent event) {
        if (event.button() != 0
                || openKey.type() == StorageKey.Type.STORAGE_INDEX
                || !this.menu.getCarried().isEmpty()
                || !isOverOverviewPanel(event.x(), event.y())) {
            return false;
        }
        indexItemAt(event.x(), event.y()).ifPresentOrElse(
                this::onPageCardClicked,
                () -> OverlayActions.sendCommand(state, "storage"));
        return true;
    }

    /**
     * Real slot clicks (live page and player inventory). The screen's own mouseClicked runs its child loop
     * again before slot logic, so RRV widgets hidden under the overlay are pulled out for the duration of the call.
     */
    private boolean dispatchToSlots(MouseButtonEvent event, boolean doubleClick, boolean coveredByGui) {
        if (!coveredByGui || !(this instanceof IScreenAccessor accessor)) {
            return super.mouseClicked(event, doubleClick);
        }

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

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {

        var box = layout.getSearchBox();
        // Stops E from closing the overlay when searching
        if (box != null && (box.isFocused() || this.getFocused() == box)) {
            // Enter or Esc leaves the search box; a second Esc closes the screen as usual.
            if (event.isEscape() || event.isConfirmation()) {
                box.setFocused(false);
                this.setFocused(null);
                return true;
            }
            box.keyPressed(event);
            return true;
        }
        if (layout.handleNavigationKey(event)) return true;
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