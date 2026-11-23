package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.api.screen.IScreenAccessor;
import com.daqem.uilib.gui.AbstractContainerScreen;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.compat.SkyOceanCompat;
import com.github.kdgaming0.enhancedstorage.gui.*;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.highlight.ItemTotals;
import com.github.kdgaming0.enhancedstorage.gui.highlight.SearchHighlight;
import com.github.kdgaming0.enhancedstorage.gui.layout.IndexGrid;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.storage.capture.ContainerContentTracker;
import com.github.kdgaming0.enhancedstorage.storage.capture.MenuLayout;
import com.github.kdgaming0.enhancedstorage.storage.capture.StorageCaptureHandler;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.mojang.blaze3d.platform.InputConstants;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StorageContainerScreen extends AbstractContainerScreen<ChestMenu> implements IHighlightClipProvider {

    private static final long DEFERRED_CLOSE_MS = 2000;
    private static final int SLOT_ITEM_SIZE = 16;
    private static final int SLOTS_PER_STRATUM = 9;
    private final StorageKey openKey;
    private final StorageOverlayState state = StorageOverlayState.session();
    private final OverlayActions actions = OverlayActions.create(this, state, this::onPageCardClicked, this::enterEditMode, this::rebuildWidgets);
    private final StorageOverlayLayout layout = new StorageOverlayLayout();
    private final LiveSlotPlacer slotPlacer;
    private final LiveSlotSearch liveSearch = new LiveSlotSearch();
    private final List<SunkRrvRenderable> sunkRrvWidgets = new ArrayList<>();
    private boolean refocusRrvSearchBar;
    private int backgroundMouseX;
    private int backgroundMouseY;
    private float backgroundPartialTick;
    private boolean rrvDrawnInBackground;
    private boolean bottomTabSelectedThisFrame;
    private boolean autoScrolledToOpenCard = false;
    private long deferredCloseAt = 0;
    private int slotsInStratum;
    // SkyOcean's craft helper panel: which side it's on, how much of that side the overlay keeps free for it, and the
    // recipe it was built for. Set on the first layout, then kept current by containerTick.
    private boolean craftHelperTracked;
    private SkyOceanCompat.Side craftHelperSide = SkyOceanCompat.Side.NONE;
    private int craftHelperReserve;
    private @Nullable Object craftHelperRecipe;
    private boolean repositioning;

    public StorageContainerScreen(ChestMenu menu, Inventory inventory, Component title, StorageKey openKey) {
        super(menu, inventory, title);
        this.openKey = openKey;
        this.state.setOpenKey(openKey);
        this.slotPlacer = new LiveSlotPlacer(menu, layout, openKey);
    }

    private static boolean isRrvWidget(Object o) {
        if (o instanceof SunkRrvRenderable) {
            return true; // treat wrappers as RRV widgets so we can re-process them
        }
        return o.getClass().getName().startsWith("cc.cassian.rrv");
    }

    /**
     * Switches the window to the overlay's own GUI scale before anything lays out. {@code width} and
     * {@code height} were assigned by our caller before the scale changed, so they have to be re-read.
     * While Better Screens scales this screen it sets the scale and size itself, so both are left alone.
     * <p>
     * Every rebuild funnels through here, so resizes, fullscreen toggles and live config edits need no
     * code of their own — including window resizes, where vanilla resets the scale to the player's
     * setting on the way in and {@link OverlayScale#sync} puts ours back.
     */
    @Override
    protected void init() {
        OverlayScale.syncScreen(this);

        super.init();

        if (openKey.type() == StorageKey.Type.RIFT) {
            StorageCaptureHandler.discoverRiftPages(this.getTitle());
        }

        if (!craftHelperTracked) {
            craftHelperTracked = true;
            craftHelperSide = SkyOceanCompat.panelSide();
            craftHelperReserve = craftHelperSide == SkyOceanCompat.Side.NONE ? 0 : SkyOceanCompat.expectedExtent();
            craftHelperRecipe = SkyOceanCompat.recipe();
        }
        // Whatever panel SkyOcean adds after this layout is the one measured next tick.
        SkyOceanCompat.forgetPanel();
        layout.setReservedSides(craftHelperSide == SkyOceanCompat.Side.LEFT ? craftHelperReserve : 0,
                craftHelperSide == SkyOceanCompat.Side.RIGHT ? craftHelperReserve : 0);
        layout.build(this, this.font, this.width, this.height, state, openKey, this.menu.getRowCount(), actions);

        // Repeated for every layout until the first frame: Better Screens resizes the screen once more before it.
        if (!autoScrolledToOpenCard) {
            layout.scrollLiveCardIntoView();
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
        int panelsBottom = (overview != null) ? Math.max(overview.getTotalY() + overview.getHeight(), invBottom) : invBottom;
        int boxBottom = Math.max(panelsBottom, boxTop + layout.getMainBackgroundHeight());

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
    public @NonNull List<? extends GuiEventListener> children() {
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
        autoScrolledToOpenCard = true;
        CopyHighlight.setTarget(hoveredStack(mouseX, mouseY));
        ItemTotals.update(layout.context(), openKey, () -> liveStacks().iterator());
        layout.updateCopyFilter();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty()) ItemTotals.extractBox(graphics, this.font, mouseX, mouseY);
        layout.setQuickNavBottomTabSelected(bottomTabSelectedThisFrame);
        if (refocusRrvSearchBar) {
            refocusRrvSearchBar = false;
            RRVCompat.focusSearchBar(this);
        }
    }

    private List<ItemStack> liveStacks() {
        int containerSlots = MenuLayout.containerSlots(this.menu);
        boolean countContainer = openKey.type() != StorageKey.Type.STORAGE_INDEX;
        List<ItemStack> stacks = new ArrayList<>(this.menu.slots.size());
        for (Slot slot : this.menu.slots) {
            if (countContainer || slot.index >= containerSlots) stacks.add(slot.getItem());
        }
        return stacks;
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!LiveSlotPlacer.isParked(slot) && this.isHovering(slot.x, slot.y, SLOT_ITEM_SIZE, SLOT_ITEM_SIZE, mouseX, mouseY)
                    && slot.hasItem()) {
                return slot.getItem();
            }
        }
        return layout.hoveredCachedStack(mouseX, mouseY);
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        slotPlacer.sync(this.leftPos, this.topPos);
        super.extractContents(guiGraphics, mouseX, mouseY, a);
        // A live slot holding an item shows its own tooltip, even if the cached copy still has it empty.
        if (this.menu.getCarried().isEmpty() && (this.hoveredSlot == null || !this.hoveredSlot.hasItem())) {
            layout.extractOverlayTooltip(guiGraphics, mouseX, mouseY);
        }
        RRVCompat.renderHighlightAbove(this, guiGraphics, mouseX, mouseY, a);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (RRVCompat.isLoaded()) {
            sinkForeignRrvWidgets();
        }
        bottomTabSelectedThisFrame = false;
        backgroundMouseX = mouseX;
        backgroundMouseY = mouseY;
        backgroundPartialTick = partialTick;
        rrvDrawnInBackground = false;
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        // Rendering RRV's overlay early, in the background stratum, so everything this screen draws appears on top of it and not the other way around.
        if (RRVCompat.isLoaded() && !rrvDrawnInBackground) {
            drawRrvBelow(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Draws RRV right after the background dim rather than after the whole background pass, because Skyblocker draws
     * its unselected Quick Nav tabs straight after this dim: RRV's side panel would otherwise cover tabs that reach it.
     */
    @Override
    public void extractTransparentBackground(@NonNull GuiGraphicsExtractor guiGraphics) {
        super.extractTransparentBackground(guiGraphics);
        if (RRVCompat.isLoaded()) drawRrvBelow(guiGraphics, backgroundMouseX, backgroundMouseY, backgroundPartialTick);
    }

    /**
     * Makes the Item list buttons from RRV render behind the menu instead of on top / block the mouse hovered from reaching the button.
     * They stay in the renderables list (RRV adds and removes them there) but are wrapped so the widget pass skips them;
     * drawRrvBelow draws them in the background pass instead, under Skyblocker's Quick Nav tabs.
     */
    private void sinkForeignRrvWidgets() {
        sunkRrvWidgets.clear();
        if (!(this instanceof IScreenAccessor accessor)) return;

        List<Renderable> renderables = accessor.uilib$getRenderables();
        List<GuiEventListener> children = accessor.uilib$getChildren();

        renderables.removeIf(r -> {
            if (!isRrvWidget(r)) return false;

            Renderable original = (r instanceof SunkRrvRenderable sunk) ? sunk.delegate() : r;
            if (!(original instanceof GuiEventListener l) || children.contains(l)) {
                sunkRrvWidgets.add(new SunkRrvRenderable(this, original));
            }
            return true;
        });

        renderables.addAll(0, sunkRrvWidgets);
    }

    private void drawRrvBelow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        RRVCompat.renderOverlayBelow(guiGraphics, mouseX, mouseY, partialTick);
        for (SunkRrvRenderable widget : sunkRrvWidgets) {
            widget.extractInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        rrvDrawnInBackground = true;
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
        // Capture the page on the next tick, so its cached copy (hover, empty count, search) includes this change.
        ContainerContentTracker.markLocalChange(this.menu);
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
        if (craftHelperSpaceChanged() | layout.modWidgetSpaceChanged()) rebuildWidgets();
    }

    /**
     * Follows SkyOcean's craft helper panel: a recipe set or removed while the screen is open, a changed position
     * setting, or the panel growing or shrinking (expanding its tree, finishing ingredients). Returns true when the
     * screen has to be laid out again.
     */
    private boolean craftHelperSpaceChanged() {
        // A recipe set or removed while the screen is open. SkyOcean only rebuilds its panel when a screen initialises,
        // so lay out again (which has it build the panel for the new recipe, or none); the next tick measures it.
        Object recipe = SkyOceanCompat.recipe();
        if (recipe != craftHelperRecipe) {
            craftHelperRecipe = recipe;
            return true;
        }
        SkyOceanCompat.Side side = SkyOceanCompat.panelSide();
        int reserve = side == SkyOceanCompat.Side.NONE ? 0 : SkyOceanCompat.measuredExtent();
        if (side == craftHelperSide && reserve == craftHelperReserve) return false;
        craftHelperSide = side;
        craftHelperReserve = reserve;
        return true;
    }

    /**
     * A window resize rebuilds through here and then fires Fabric's after-init event, which has SkyOcean add its
     * panel itself; {@link #rebuildWidgets} must not add a second one.
     */
    @Override
    protected void repositionElements() {
        repositioning = true;
        try {
            super.repositionElements();
        } finally {
            repositioning = false;
        }
    }

    /**
     * A rebuild throws away every widget and clears focus. The search box instance itself survives
     * (the layout reuses it), so put focus back on it afterwards — otherwise a rebuild triggered
     * while the user is typing swallows the keystrokes that follow it.
     */
    @Override
    protected void rebuildWidgets() {
        boolean refocus = SearchAutofocus.isFocused(this, layout.getSearchBox());
        refocusRrvSearchBar |= RRVCompat.isSearchBarFocused(this);

        super.rebuildWidgets();
        // Our own relayouts don't fire Fabric's after-init event, so SkyOcean wouldn't put its panel back.
        if (!repositioning && craftHelperSide != SkyOceanCompat.Side.NONE) SkyOceanCompat.addPanel(this);

        var box = layout.getSearchBox();
        if (refocus && box != null) SearchAutofocus.focus(this, box);
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
        // Closing the menu resets the scroll as if storage was closed; keep it so edit mode opens where we were.
        // Edit mode has no copy filter, so use the scroll from before the filter if one is active.
        Double beforeFilter = state.getScrollBeforeCopyFilter();
        double scroll = beforeFilter != null ? beforeFilter : state.getScrollAmount();
        double navigationScroll = state.getNavigationScroll();
        this.minecraft.player.closeContainer();
        state.setScrollAmount(scroll);
        state.setNavigationScroll(navigationScroll);
        Minecraft.getInstance().setScreen(StorageOverlay.forEditMode(rift, true));
    }

    private boolean isOverOverviewPanel(double mx, double my) {
        return Rect.contains(layout.getOverviewBounds(), mx, my);
    }

    private Optional<StorageKey> indexItemAt(double mx, double my) {
        Rect bounds = layout.getOverviewBounds();
        if (bounds == null) return Optional.empty();

        int idx = IndexGrid.cellAt((int) (mx - bounds.x()), (int) (my - bounds.y() - layout.getPanelContentTop()));
        if (idx < 0) return Optional.empty();

        ItemStack stack = PageCache.getInstance().itemAt(StorageKey.INDEX, idx);
        return stack.isEmpty() ? Optional.empty() : StorageKey.fromIndexItem(stack.getHoverName());
    }

    private @Nullable Slot findHoveredContainerSlot(double mx, double my) {
        int containerSlots = MenuLayout.containerSlots(this.menu);
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = this.menu.slots.get(i);
            if (this.isHovering(slot.x, slot.y, SLOT_ITEM_SIZE, SLOT_ITEM_SIZE, mx, my)) {
                return slot;
            }
        }
        return null;
    }

    // Centers Skyblocker Quick Nav row on the inventory or main storage panel instead of the whole screen.
    public int quickNavTabX(int x, int y, boolean toggled) {
        boolean topRow = y < this.topPos;
        if (!topRow && toggled) bottomTabSelectedThisFrame = true;
        int skyblockerCenter = this.leftPos + this.imageWidth / 2;
        return x - skyblockerCenter + layout.quickNavRowCenterX();
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
        if (layout.scrollPanels(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        slotsInStratum = SLOTS_PER_STRATUM;
        super.extractSlots(graphics, mouseX, mouseY);
    }

    private void nextStratumPerRow(GuiGraphicsExtractor graphics) {
        if (slotsInStratum++ == SLOTS_PER_STRATUM) {
            graphics.nextStratum();
            slotsInStratum = 1;
        }
    }

    @Override
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        // Slots parked offscreen (nav row, hidden index rows) still cost full item extraction
        // plus every other mod's slot hooks — skip them entirely.
        if (LiveSlotPlacer.isParked(slot)) return;

        Rect viewport = slotPlacer.viewport();
        if (viewport == null || slot.index >= MenuLayout.containerSlots(this.menu)) {
            nextStratumPerRow(graphics);
            if (CopyHighlight.hasTarget() && CopyHighlight.isCopy(slot.getItem()))
                CopyHighlight.draw(graphics, slot.x, slot.y);
            super.extractSlot(graphics, slot, mouseX, mouseY);
            return;
        }

        int slotX = slot.x + this.leftPos;
        int slotY = slot.y + this.topPos;
        if (slotY + 2 * SLOT_ITEM_SIZE <= viewport.y() || slotY - SLOT_ITEM_SIZE >= viewport.bottom()
                || slotX + 2 * SLOT_ITEM_SIZE <= viewport.x() || slotX - SLOT_ITEM_SIZE >= viewport.right()) {
            return;
        }

        nextStratumPerRow(graphics);
        graphics.enableScissor(
                viewport.x() - this.leftPos, viewport.y() - this.topPos,
                viewport.right() - this.leftPos, viewport.bottom() - this.topPos);

        boolean searching = state.isSearching();
        // Read once: other mods hook every slot read.
        ItemStack stack = searching || CopyHighlight.hasTarget() ? slot.getItem() : ItemStack.EMPTY;
        boolean match = searching && liveSearch.matches(slot.index, stack, state.getSearchQuery());

        if (searching && match) {
            SearchHighlight.drawMatch(graphics, slot.x, slot.y);
        }
        if (CopyHighlight.isCopy(stack)) {
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
        SearchAutofocus.releaseOnOutsideClick(this, layout.getSearchBox(), event.x(), event.y());
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
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) this.setDragging(true);
                return true;
            }
        }
        return false;
    }

    /**
     * On the live index page, left-clicking a page item navigates to that page.
     */
    private boolean handleIndexSlotClick(MouseButtonEvent event) {
        if (openKey.type() != StorageKey.Type.STORAGE_INDEX || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;

        Slot slot = findHoveredContainerSlot(event.x(), event.y());
        if (slot == null || !slot.hasItem()) return false;

        Optional<StorageKey> key = StorageKey.fromIndexItem(slot.getItem().getHoverName());
        if (key.isEmpty()) return false;

        onPageCardClicked(key.get());
        return true;
    }

    /**
     * Off the index page, left-clicking the cached overview opens the clicked page, or /storage when no item is hit.
     */
    private boolean handleOverviewPanelClick(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
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
        if (SearchAutofocus.isFocused(this, box)) {
            // Enter or Esc leaves the search box; a second Esc closes the screen as usual.
            if (event.isEscape() || event.isConfirmation()) {
                SearchAutofocus.release(this, box);
                return true;
            }
            box.keyPressed(event);
            if (SearchAutofocus.shouldRelease(event, box)) SearchAutofocus.release(this, box);
            return true;
        }
        if (RRVCompat.isSearchBarFocused(this)) return super.keyPressed(event);
        if (CopyHighlight.isSearchKey(event)) {
            layout.searchFor(CopyHighlight.hovered());
            return true;
        }
        if (layout.handleNavigationKey(event)) return true;
        if (box != null && SearchAutofocus.shouldFocus(event, this.hoveredSlot != null || !CopyHighlight.hovered().isEmpty())) {
            SearchAutofocus.focus(this, box);
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    private record SunkRrvRenderable(StorageContainerScreen screen, Renderable delegate) implements Renderable {
        @Override
        public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        }

        void extractInBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            if (screen.isInsideOverlay(mouseX, mouseY)) {
                delegate.extractRenderState(graphics, -9999, -9999, a);  // Suppress hover when covered by this screen
            } else {
                delegate.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }
    }
}