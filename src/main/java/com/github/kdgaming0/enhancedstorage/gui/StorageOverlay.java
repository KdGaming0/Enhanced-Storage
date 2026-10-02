package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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

    private @Nullable PageCardComponent pressedTitle;
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

        pressedTitle = null;
        renamingCard = null;
        renameBox = null;

        layout.build(this, this.font, this.width, this.height, state, null, 0, actions);
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
            PageCardComponent card = titleRowAt(event.x(), event.y());
            if (card != null) {
                pressedTitle = card;
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
        if (pressedTitle != null && event.button() == 0) {
            PageCardComponent card = pressedTitle;
            pressedTitle = null;
            double dx = event.x() - pressX;
            double dy = event.y() - pressY;
            boolean moved = dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD;
            if (!moved && titleRowAt(event.x(), event.y()) == card) {
                beginRename(card);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
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