package com.github.kdgaming0.enhancedstorage.screen;

import com.daqem.uilib.api.screen.IScreenAccessor;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.AbstractContainerScreen;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.compat.RRVCompat;
import com.github.kdgaming0.enhancedstorage.gui.*;
import com.github.kdgaming0.enhancedstorage.gui.component.EditDialogComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.PageOrder;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.github.kdgaming0.enhancedstorage.storage.StorageCaptureHandler;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import eu.midnightdust.lib.config.MidnightConfig;
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
    private final StorageOverlayLayout layout = new StorageOverlayLayout();
    private final LiveSlotPlacer slotPlacer;
    private final LiveSlotSearch liveSearch = new LiveSlotSearch();
    private final boolean rrvLoaded = FabricLoader.getInstance().isModLoaded("rrv");
    private boolean searchDirty = false;
    private Deque<ItemStack> searchWarmupQueue;
    private boolean autoScrolledToOpenCard = false;
    private EditDialogComponent renameDialog;
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

        slotPlacer.sync(this.leftPos, this.topPos);

        // Queue every cached stack for search-text warmup; init() also runs on every rebuildWidgets, so only build the queue once per screen.
        if (searchWarmupQueue == null) {
            searchWarmupQueue = new ArrayDeque<>();
            PageCache.getInstance().all().values()
                    .forEach(page -> searchWarmupQueue.addAll(page.items()));
        }

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
        if (renameDialog != null) return false;
        return !isInsideOverlay(mx, my);
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {
        slotPlacer.sync(this.leftPos, this.topPos);
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
                || Rect.contains(layout.getOverviewBounds(), mx, my);
    }

    private void onSearchChanged() {
        searchDirty = true;
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
        if (searchWarmupQueue != null && !searchWarmupQueue.isEmpty()) {
            ItemSearch.warmUp(searchWarmupQueue, 3);
        }
        if (searchDirty) {
            searchDirty = false;
            layout.applySearch(state.getSearchQuery());
        }
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
        return PageOrder.getInstance().all().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(editingKey) && entry.getValue() == position)
                .map(entry -> {
                    StorageKey otherKey = entry.getKey();
                    return PageNames.getInstance().get(otherKey).orElse(otherKey.displayName());
                })
                .findFirst()
                .orElse(null);
    }

    private void onRenameSave(String name, String position) {
        StorageKey key = (renameDialog != null) ? renameDialog.getKey() : null;
        if (key != null) {
            PageNames.getInstance().set(key, name);
            ProfileSession.getInstance().saveAll();

            Integer pos = null;
            String trimmed = position == null ? "" : position.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("-")) {
                try {
                    pos = Integer.valueOf(trimmed);
                } catch (NumberFormatException ignored) {
                    // validation should prevent this
                }
            }
            PageOrder.getInstance().set(key, pos);
            ProfileSession.getInstance().saveAll();
        }
        closeRenameDialog();
        refreshAfterRename();
    }

    private void onRenameReset(StorageKey key) {
        PageNames.getInstance().clear(key);
        PageOrder.getInstance().clear(key);
        ProfileSession.getInstance().saveAll();
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
            if (card.bounds().contains(mx, my)) {
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
        if (LiveSlotPlacer.isParked(slot)) return;

        Rect viewport = slotPlacer.viewport();
        if (viewport == null || slot.index >= this.menu.getRowCount() * 9) {
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

        if ((event.button() == 0 || event.button() == 1)
                && Rect.contains(layout.getToolkitButtonBounds(), event.x(), event.y())) {
            runToolkitCommand(event.button() == 0 ? "farmingtoolkit" : "huntingtoolkit");
            return true;
        }

        if (event.button() == 0 && Rect.contains(layout.getSettingsButtonBounds(), event.x(), event.y())) {
            onSettingsClicked();
            return true;
        }

        if (event.button() == 0 && Rect.contains(layout.getThemeButtonBounds(), event.x(), event.y())) {
            OverlayTheme.cycle();
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
            box.keyPressed(event);
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