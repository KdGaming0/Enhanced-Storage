package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.PaintLayerComponent;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
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
    private @Nullable PageCardComponent pressedCard;
    private boolean pressedOnTitle;
    private double pressX;
    private double pressY;
    private @Nullable PageCardComponent renamingCard;
    private @Nullable EditBoxWidget renameBox;

    public StorageOverlay() {
        this(new StorageOverlayLayout(), false, null);
    }

    private StorageOverlay(StorageOverlayLayout layout, boolean editMode, @Nullable String returnCommand) {
        super(Component.literal("Storage Overlay"));
        this.layout = layout;
        this.editMode = editMode;
        this.returnCommand = returnCommand;
        this.drag = new CardDragController(layout, this::onDrop);
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

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (renameBox != null) {
            if (renameBox.isMouseOver(event.x(), event.y())) {
                renameBox.mouseClicked(event, doubleClick);
                setDragging(true);  // lets drag-selecting text reach the box
                return true;
            }
            // Clicking outside saves, then the click carries on against the rebuilt layout.
            endRename(true);
        }

        if (layout.handleButtonClick(event)) return true;

        if (editMode && event.button() == 0) {
            PageCardComponent card = cardAt(event.x(), event.y());
            if (card != null) {
                pressedCard = card;
                pressedOnTitle = card.isOverTitleRow(event.x(), event.y());
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
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0 && drag.isActive()) {
            drag.drop();
            return true;
        }
        if (event.button() == 0 && pressedCard != null) {
            PageCardComponent card = pressedCard;
            pressedCard = null;
            if (pressedOnTitle && !movedPastThreshold(event.x(), event.y())
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

    private void onDrop(List<StorageKey> order) {
        layout.saveOrder(order);
        rebuildWidgets();
    }


    private void paintEditLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (drag.isActive()) {
            drag.paint(graphics, mouseX, mouseY);
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
            return;
        }
        if (renameBox != null) return;

        PageCardComponent hovered = cardAt(mouseX, mouseY);
        Rect viewport = layout.getPageOverviewBounds();
        if (hovered == null || viewport == null) return;

        boolean overTitle = hovered.isOverTitleRow(mouseX, mouseY);
        Rect card = hovered.bounds();

        // Hover cues: the frame says "draggable", the title wash says "click to rename".
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_HOVER),
                card.x(), card.y(), card.right() - card.x(), card.bottom() - card.y());
        if (overTitle) {
            Rect title = hovered.titleRowBounds();
            graphics.fill(title.x() + 2, title.y(), title.right() - 2, title.bottom(), OverlayTheme.titleHoverFill());
        }
        graphics.disableScissor();

        // Replaces the disabled button's cursor.
        graphics.requestCursor(overTitle ? CursorTypes.IBEAM : CursorTypes.RESIZE_ALL);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
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

    private @Nullable PageCardComponent cardAt(double x, double y) {
        if (!Rect.contains(layout.getPageOverviewBounds(), x, y)) return null;
        for (PageCardComponent card : layout.getPageCards()) {
            if (card.bounds().contains(x, y)) return card;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null
                && Rect.contains(layout.getPageOverviewBounds(), mouseX, mouseY)
                && overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}