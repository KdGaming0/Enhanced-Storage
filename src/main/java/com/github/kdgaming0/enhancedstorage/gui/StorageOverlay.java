package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.component.*;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Browse-only overlay showing cached pages without an open server menu.
 * Used for testing outside Hypixel and as the host for edit mode.
 */
public class StorageOverlay extends AbstractScreen {

    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout;
    private final OverlayActions actions = OverlayActions.create(this, state, this::onPageCardClicked, this::toggleEditMode, this::rebuildWidgets);
    static final int DRAG_THRESHOLD = 4;
    private final boolean editMode;
    // Sent on exit to reopen the live menu edit mode was entered from; null means just close.
    private final @Nullable String returnCommand;
    private boolean returnRequested;

    private final CardDragController drag;
    private @Nullable GridCellComponent pressedCard;
    private boolean pressedOnTitle;
    private double pressX;
    private double pressY;
    private @Nullable PageCardComponent renamingCard;
    private @Nullable EditBoxWidget renameBox;
    private @Nullable PopupComponent popup;
    private int realMouseX;
    private int realMouseY;

    public StorageOverlay() {
        this(new StorageOverlayLayout(), false, null);
    }

    private StorageOverlay(StorageOverlayLayout layout, boolean editMode, @Nullable String returnCommand) {
        super(Component.literal("Storage Overlay"));
        this.layout = layout;
        this.editMode = editMode;
        this.returnCommand = returnCommand;
        this.drag = new CardDragController(layout, this::applyOrder);
    }

    /**
     * @param rift            the context to edit; rift and normal storage keep separate layouts
     * @param fromLiveStorage true when entered from the live menu, so exiting reopens it
     */
    public static StorageOverlay forEditMode(boolean rift, boolean fromLiveStorage) {
        String returnCommand = fromLiveStorage && !rift ? "storage" : null;
        return new StorageOverlay(StorageOverlayLayout.forEditMode(rift), true, returnCommand);
    }

    @Override
    protected void init() {
        // Match the live screen's GUI scale so entering edit mode doesn't resize the overlay.
        Minecraft minecraft = Minecraft.getInstance();
        OverlayScale.sync(minecraft);
        this.width = minecraft.getWindow().getGuiScaledWidth();
        this.height = minecraft.getWindow().getGuiScaledHeight();

        pressedCard = null;
        drag.reset();
        renamingCard = null;
        renameBox = null;
        popup = null;

        layout.build(this, this.font, this.width, this.height, state, null, 0, actions);

        if (editMode) {
            PaintLayerComponent dragLayer = new PaintLayerComponent(this.width, this.height, this::paintEditLayer);
            dragLayer.updateParentPosition(0, 0, this.width, this.height);
            addComponent(dragLayer);
        }
        state.onStorageScreenOpened();
    }

    @Override
    public void tick() {
        super.tick();
        layout.tick();
    }

    private void onPageCardClicked(StorageKey key) {
        if (Objects.equals(key, state.getOpenKey())) return;

        EnhancedStorage.LOGGER.info("Storage page card clicked: {} (type={}, cached={})",
                key.displayName(), key.type(), PageCache.getInstance().get(key).isPresent());
        state.setOpenKey(key);
    }

    private void toggleEditMode() {
        if (editMode) {
            onClose();
        } else {
            Minecraft.getInstance().setScreen(forEditMode(false, false));
        }
    }

    @Override
    public void onClose() {
        if (returnCommand != null && !returnRequested) {
            // Stay open until the server's menu replaces this screen, so the world doesn't flash in between.
            // A second close (for example if the command failed) closes for real.
            returnRequested = true;
            OverlayActions.sendCommand(state, returnCommand);
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
        OverlayScale.restoreGameScale(Minecraft.getInstance());
    }

    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) {
            for (IWidget widget : popup.getWidgets()) {
                if (widget.mouseClicked(event, doubleClick)) return true;
            }
            // Clicking outside the panel dismisses it.
            if (!popup.isOverPanel(event.x(), event.y())) popup = null;
            return true;
        }
        if (renameBox != null) {
            if (renameBox.isMouseOver(event.x(), event.y())) {
                renameBox.mouseClicked(event, doubleClick);
                setDragging(true);  // lets drag-selecting text reach the box
                return true;
            }
            // Clicking outside saves, then the click carries on against the rebuilt layout.
            endRename(true);
        }

        if (editMode && event.button() == 0 && layout.isOverResetButton(event.x(), event.y())) {
            openResetDialog();
            return true;
        }
        if (layout.handleButtonClick(event)) return true;

        if (editMode && event.button() == 1 && openSpacerMenu(event.x(), event.y())) return true;

        if (editMode && event.button() == 0) {
            GridCellComponent cell = cellAt(event.x(), event.y());
            if (cell != null) {
                pressedCard = cell;
                pressedOnTitle = cell instanceof PageCardComponent card && card.isOverTitleRow(event.x(), event.y());
                pressX = event.x();
                pressY = event.y();
                return true;
            }
        }

        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null && overview.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        realMouseX = mouseX;
        realMouseY = mouseY;
        if (popup != null) {
            super.extractRenderState(graphics, -9999, -9999, partialTick);
        } else {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0 && drag.isActive()) {
            drag.drop();
            return true;
        }
        if (event.button() == 0 && pressedCard != null) {
            GridCellComponent cell = pressedCard;
            pressedCard = null;
            if (cell instanceof PageCardComponent card && pressedOnTitle
                    && !movedPastThreshold(event.x(), event.y())
                    && titleRowAt(event.x(), event.y()) == card) {
                beginRename(card);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && pressedCard != null && !drag.isActive()
                && movedPastThreshold(event.x(), event.y())) {
            drag.start(pressedCard, pressX, pressY, this.width, this.height);
            pressedCard = null;
        }
        if (drag.isActive()) {
            drag.update(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private boolean movedPastThreshold(double x, double y) {
        double dx = x - pressX;
        double dy = y - pressY;
        return dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD;
    }

    private void applyOrder(List<LayoutEntry> order) {
        layout.saveOrder(order);
        rebuildWidgets();
    }

    /** Right-click on a page or a spacer. Returns false if neither is under the cursor. */
    private boolean openSpacerMenu(double x, double y) {
        List<GridCellComponent> cells = layout.getCells();
        GridCellComponent cell = cellAt(x, y);
        List<PopupComponent.Option> options;
        if (cell instanceof SpacerCardComponent) {
            options = List.of(option("Remove Spacer", () -> removeSpacer(cell)));
        } else if (cell != null) {
            int index = cells.indexOf(cell);
            options = List.of(
                    option("Add Spacer Before", () -> insertSpacer(index)),
                    option("Add Spacer After", () -> insertSpacer(index + 1)));
        } else {
            return false;
        }
        popup = PopupComponent.menu(this.width, this.height, this.font, (int) x, (int) y, options);
        return true;
    }

    private void insertSpacer(int index) {
        List<LayoutEntry> entries = layout.currentEntries();
        entries.add(index, new LayoutEntry.Spacer(layout.nextSpacerId()));
        applyOrder(entries);
    }

    private void removeSpacer(GridCellComponent spacer) {
        List<LayoutEntry> entries = layout.currentEntries();
        entries.remove(spacer.entry());
        applyOrder(entries);
    }

    // Spacers draw nothing themselves, so they only become visible here, in edit mode.
    private void paintSpacers(GuiGraphicsExtractor graphics, Rect viewport) {
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (GridCellComponent cell : layout.getCells()) {
            if (cell instanceof SpacerCardComponent) {
                Rect r = cell.bounds();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_SPACER),
                        r.x(), r.y(), r.width(), r.height());
            }
        }
        graphics.disableScissor();
    }

    private void paintEditLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Rect viewport = layout.getPageOverviewBounds();
        if (viewport != null) paintSpacers(graphics, viewport);

        if (popup != null) {
            // Requested before the popup renders, so its buttons can still ask for the hand cursor.
            graphics.requestCursor(CursorTypes.ARROW);
            popup.extractRenderStateBase(graphics, realMouseX, realMouseY, 0, this.width, this.height);
            return;
        }
        if (drag.isActive()) {
            drag.paint(graphics, mouseX, mouseY);
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
            return;
        }
        if (renameBox != null) return;

        GridCellComponent hovered = cellAt(mouseX, mouseY);
        if (hovered == null || viewport == null) return;

        Rect title = hovered instanceof PageCardComponent card && card.isOverTitleRow(mouseX, mouseY)
                ? card.titleRowBounds() : null;
        Rect bounds = hovered.bounds();

        // Hover cues: the frame says "draggable", the title wash says "click to rename".
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_HOVER),
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (title != null) {
            graphics.fill(title.x() + 2, title.y(), title.right() - 2, title.bottom(), OverlayTheme.titleHoverFill());
        }
        graphics.disableScissor();

        // Replaces the disabled button's cursor.
        graphics.requestCursor(title != null ? CursorTypes.IBEAM : CursorTypes.RESIZE_ALL);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (popup != null) {
            if (event.isEscape()) popup = null;
            return true;
        }
        if (drag.isActive()) {
            if (event.isEscape()) drag.cancel();
            return true;
        }
        if (renameBox != null) {
            if (event.isEscape()) {
                endRename(false);
            } else if (event.isConfirmation()) {
                endRename(true);
            } else {
                renameBox.keyPressed(event);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private @Nullable PageCardComponent titleRowAt(double x, double y) {
        // Cards scrolled out of the viewport still have positions, so check the viewport first.
        if (!Rect.contains(layout.getPageOverviewBounds(), x, y)) return null;
        for (PageCardComponent card : layout.getPageCards()) {
            if (card.isOverTitleRow(x, y)) return card;
        }
        return null;
    }

    private @Nullable GridCellComponent cellAt(double x, double y) {
        if (!Rect.contains(layout.getPageOverviewBounds(), x, y)) return null;
        for (GridCellComponent cell : layout.getCells()) {
            if (cell.bounds().contains(x, y)) return cell;
        }
        return null;
    }

    private void beginRename(PageCardComponent card) {
        renamingCard = card;
        renameBox = card.beginTitleEdit(this.font);
        // Typed characters reach the box through screen focus.
        setFocused(renameBox);
        renameBox.setFocused(true);
    }

    private void endRename(boolean save) {
        if (renameBox == null || renamingCard == null) return;
        if (save) {
            // PageNames treats a blank name as "back to default".
            PageNames.getInstance().set(renamingCard.getKey(), renameBox.getValue());
            ProfileSession.getInstance().saveAll();
        }
        renameBox = null;
        renamingCard = null;
        setFocused(null);
        rebuildWidgets();
    }

    private void openResetDialog() {
        String context = layout.isRiftContext() ? "Rift Storage" : "Storage";
        popup = PopupComponent.confirm(this.width, this.height, this.font,
                List.of(Component.literal("Reset " + context + " layout?"),
                        Component.literal("This can't be undone.").withStyle(ChatFormatting.RED)),
                List.of(option("Reset Order", () -> resetLayout(true, false)),
                        option("Reset Names", () -> resetLayout(false, true)),
                        option("Reset Both", () -> resetLayout(true, true)),
                        option("Cancel", () -> {
                        })));
    }

    private void resetLayout(boolean order, boolean names) {
        layout.resetLayout(order, names);
        rebuildWidgets();
    }

    private PopupComponent.Option option(String label, Runnable action) {
        return new PopupComponent.Option(label, () -> {
            popup = null;
            action.run();
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup != null) return true;
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null
                && Rect.contains(layout.getPageOverviewBounds(), mouseX, mouseY)
                && overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}