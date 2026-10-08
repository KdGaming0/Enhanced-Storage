package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout.ResetPart;
import com.github.kdgaming0.enhancedstorage.gui.component.common.PaintLayerComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.common.PopupComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageWidthSlider;
import com.github.kdgaming0.enhancedstorage.gui.component.page.SpacerCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionEndComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionHeaderComponent;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.highlight.ItemTotals;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.*;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Browse-only overlay showing cached pages without an open server menu.
 * Used for testing outside Hypixel and as the host for edit mode.
 */
public class StorageOverlay extends AbstractScreen {

    static final int DRAG_THRESHOLD = 4;
    private static final int SECTION_LABEL_BACKGROUND = 0xC0000000;
    private static final String ADD_SECTION_TEXT = "+ Add section here";
    private static final String FILL_SPACER_TEXT = "Fills the row";
    private static final float FILL_SPACER_ALPHA = 0.5f;
    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout;
    private final OverlayActions actions = OverlayActions.create(this, state, this::onPageCardClicked, this::toggleEditMode, this::rebuildWidgets);
    private final boolean editMode;
    // Sent on exit to reopen the live menu edit mode was entered from; null means just close.
    private final @Nullable String returnCommand;
    private final CardDragController drag;
    private boolean returnRequested;
    private @Nullable GridCellComponent pressedCard;
    private boolean pressedOnTitle;
    private boolean pressedOnGrip;
    private double pressX;
    private double pressY;
    private @Nullable Consumer<String> renameCommit;
    private @Nullable TextComponent sectionLabel;
    private @Nullable EditBoxWidget renameBox;
    private @Nullable PopupComponent popup;
    private @Nullable IWidget popupPressed;
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

    private static boolean isOverRenameTarget(GridCellComponent cell, double x, double y) {
        return switch (cell) {
            case PageCardComponent card -> card.isOverTitleRow(x, y);
            case SectionHeaderComponent header -> header.isOverName(x, y);
            default -> false;
        };
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
        renameCommit = null;
        renameBox = null;
        popup = null;
        popupPressed = null;

        layout.build(this, this.font, this.width, this.height, state, null, 0, actions);

        if (editMode) {
            sectionLabel = new TextComponent(0, 0, Component.literal(ADD_SECTION_TEXT), OverlayStyle.accentColor());
            sectionLabel.setDrawShadow(true);
            sectionLabel.updateParentPosition(0, 0, this.width, this.height);

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
                if (widget.mouseClicked(event, doubleClick)) {
                    // Drags and the release go to this widget only, so a slider keeps tracking outside its bounds.
                    if (popup != null && popup.getWidgets().contains(widget)) popupPressed = widget;
                    return true;
                }
            }
            // Clicking outside the panel dismisses it.
            if (!popup.isOverPanel(event.x(), event.y())) popup = null;
            return true;
        }
        EditBoxWidget search = layout.getSearchBox();
        if (search != null && (search.isFocused() || getFocused() == search) && !search.isMouseOver(event.x(), event.y())) {
            search.setFocused(false);
            setFocused(null);
        }
        boolean endedRename = false;
        if (renameBox != null) {
            if (renameBox.isMouseOver(event.x(), event.y())) {
                renameBox.mouseClicked(event, doubleClick);
                setDragging(true);  // lets drag-selecting text reach the box
                return true;
            }
            // Clicking outside saves, then the click carries on against the rebuilt layout.
            endRename(true);
            endedRename = true;
        }

        if (editMode && event.button() == 0 && layout.isOverResetButton(event.x(), event.y())) {
            openResetDialog(EnumSet.noneOf(ResetPart.class));
            return true;
        }
        if (layout.handleButtonClick(event)) return true;

        if (editMode && event.button() == 1 && openSpacerMenu(event.x(), event.y())) return true;

        if (editMode && event.button() == 0) {
            StorageOverlayLayout.SectionSlot slot = endedRename ? null : layout.sectionSlotAt(event.x(), event.y());
            if (slot != null) {
                insertSection(slot.index());
                return true;
            }
            GridCellComponent cell = cellAt(event.x(), event.y());
            if (cell != null) {
                pressedCard = cell;
                pressedOnTitle = isOverRenameTarget(cell, event.x(), event.y());
                pressedOnGrip = cell instanceof SectionHeaderComponent header && header.isOverGrip(event.x(), event.y());
                pressX = event.x();
                pressY = event.y();
                return true;
            }
        }

        ScrollContainerWidget navigation = layout.getNavigationList();
        if (navigation != null && navigation.mouseClicked(event, doubleClick)) {
            setFocused(navigation);
            if (event.button() == 0) setDragging(true);
            return true;
        }

        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null && overview.mouseClicked(event, doubleClick)) {
            setFocused(overview);
            if (event.button() == 0) setDragging(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        realMouseX = mouseX;
        realMouseY = mouseY;
        boolean blocked = popup != null || drag.isActive();
        CopyHighlight.setTarget(blocked ? ItemStack.EMPTY : layout.hoveredCachedStack(mouseX, mouseY));
        var player = Minecraft.getInstance().player;
        if (editMode || blocked || player == null) {
            ItemTotals.clear();
        } else {
            ItemTotals.update(layout.isRiftContext(), null, player.getInventory().getNonEquipmentItems());
        }
        layout.updateCopyFilter();
        if (popup != null) {
            super.extractRenderState(graphics, -9999, -9999, partialTick);
        } else {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            if (!drag.isActive() && renameBox == null) layout.extractOverlayTooltip(graphics, mouseX, mouseY);
            ItemTotals.extractBox(graphics, this.font, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (popupPressed != null) {
            IWidget pressed = popupPressed;
            popupPressed = null;
            pressed.mouseReleased(event);
            return true;
        }
        if (event.button() == 0 && drag.isActive()) {
            drag.drop();
            return true;
        }
        if (event.button() == 0 && pressedCard != null) {
            GridCellComponent cell = pressedCard;
            pressedCard = null;
            if (pressedOnTitle && !movedPastThreshold(event.x(), event.y())
                    && cellAt(event.x(), event.y()) == cell && isOverRenameTarget(cell, event.x(), event.y())) {
                if (cell instanceof PageCardComponent card) beginRename(card);
                else if (cell instanceof SectionHeaderComponent header) beginSectionRename(header);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (popup != null) {
            if (popupPressed != null) popupPressed.mouseDragged(event, dragX, dragY);
            return true;
        }
        if (event.button() == 0 && pressedCard != null && !drag.isActive()
                && (pressedOnGrip || !(pressedCard instanceof SectionHeaderComponent))
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

    /**
     * Right-click on a page or a spacer. Returns false if neither is under the cursor.
     */
    private boolean openSpacerMenu(double x, double y) {
        List<GridCellComponent> cells = layout.getCells();
        GridCellComponent cell = cellAt(x, y);
        List<PopupComponent.Option> options;
        if (cell instanceof SpacerCardComponent spacer) {
            LayoutEntry.Spacer entry = (LayoutEntry.Spacer) spacer.entry();
            options = List.of(
                    option("Space Width...", () -> openSpacerWidthMenu(entry, (int) x, (int) y)),
                    entry.fill()
                            ? option("Fixed Width", () -> replaceSpacer(entry, entry.withSlots(StorageOverlayLayout.pageWidth())))
                            : option("Fill Row", () -> replaceSpacer(entry, entry.withSlots(LayoutEntry.Spacer.FILL))),
                    option("Remove Space", () -> removeSpacer(cell)));
        } else if (cell instanceof PageCardComponent card) {
            int index = cells.indexOf(cell);
            // A stack is one column, so spaces go beside the whole stack.
            int before = StorageOverlayLayout.stackStart(cells, index);
            int after = StorageOverlayLayout.stackEnd(cells, index);
            GridCellComponent next = after < cells.size() ? cells.get(after) : null;
            options = new ArrayList<>();
            options.add(option("Add Space Before", () -> insertSpacer(before)));
            options.add(option("Add Space After", () -> insertSpacer(after)));
            if (next instanceof SpacerCardComponent) {
                options.add(option("Remove Space After", () -> removeSpacer(next)));
            }
            if (cell.stackTag() != GridCellComponent.NO_STACK) {
                options.add(option("Remove from Stack", () -> unstack(card.getKey())));
            }
            options.add(option("Page Width...", () -> openWidthMenu(card.getKey(), (int) x, (int) y)));
        } else {
            int index = layout.spacerSlotAt(x, y);
            if (index < 0) return false;
            options = List.of(option("Add Space", () -> insertSpacer(index)));
        }
        popup = PopupComponent.menu(this.width, this.height, this.font, (int) x, (int) y, options);
        return true;
    }

    private void openWidthMenu(StorageKey key, int x, int y) {
        boolean usingDefault = PageWidths.getInstance().get(key).isEmpty();
        int current = StorageOverlayLayout.pageWidth(key);
        PopupComponent.WidgetFactory slider = (sx, sy, sw, sh) -> new PageWidthSlider(sx, sy, sw, sh,
                current, usingDefault, slots -> setPageWidth(key, slots, x, y));
        List<PopupComponent.Option> options = usingDefault
                ? List.of()
                : List.of(option("Reset to Default", () -> setPageWidth(key, null, x, y)));
        popup = PopupComponent.menu(this.width, this.height, this.font, x, y,
                slider, PageWidthSlider.preferredWidth(this.font), options);
    }

    private void setPageWidth(StorageKey key, @Nullable Integer slots, int menuX, int menuY) {
        // Pages in a stack always share one width, so the whole stack changes together.
        for (StorageKey mate : layout.stackMates(key)) {
            if (slots == null) PageWidths.getInstance().clear(mate);
            else PageWidths.getInstance().set(mate, slots);
        }
        ProfileSession.getInstance().saveAll();
        rebuildWidgets();
        openWidthMenu(key, menuX, menuY);
    }

    private void openSpacerWidthMenu(LayoutEntry.Spacer spacer, int x, int y) {
        int current = spacer.fill() ? StorageOverlayLayout.pageWidth() : spacer.slots();
        PopupComponent.WidgetFactory slider = (sx, sy, sw, sh) -> new PageWidthSlider(sx, sy, sw, sh,
                current, false, slots -> {
            replaceSpacer(spacer, spacer.withSlots(slots));
            openSpacerWidthMenu(spacer.withSlots(slots), x, y);
        });
        popup = PopupComponent.menu(this.width, this.height, this.font, x, y,
                slider, PageWidthSlider.preferredWidth(this.font), List.of());
    }

    private void replaceSpacer(LayoutEntry.Spacer from, LayoutEntry.Spacer to) {
        List<LayoutEntry> entries = layout.currentEntries();
        int index = entries.indexOf(from);
        if (index < 0) return;
        entries.set(index, to);
        applyOrder(entries);
    }

    private void unstack(StorageKey key) {
        applyOrder(LayoutOps.unstack(layout.currentEntries(), key));
    }

    private void insertSpacer(int cellIndex) {
        List<LayoutEntry> entries = layout.currentEntries();
        entries.add(layout.entryIndexOfCell(cellIndex),
                new LayoutEntry.Spacer(layout.nextSpacerId(), StorageOverlayLayout.pageWidth()));
        applyOrder(entries);
    }

    private void removeSpacer(GridCellComponent spacer) {
        List<LayoutEntry> entries = layout.currentEntries();
        entries.remove(spacer.entry());
        applyOrder(entries);
    }

    private void insertSection(int index) {
        LayoutEntry.Section section = new LayoutEntry.Section(layout.nextSectionId(), LayoutEntry.Section.DEFAULT_NAME, false);
        List<LayoutEntry> entries = layout.currentEntries();
        int at = layout.entryIndexOfCell(index);
        entries.add(at, section);
        entries.add(at + 1, new LayoutEntry.SectionEnd());
        applyOrder(entries);

        for (GridCellComponent cell : layout.getVisibleCells()) {
            if (cell instanceof SectionHeaderComponent header && header.entry().equals(section)) {
                beginSectionRename(header);
                return;
            }
        }
    }

    private void renameSection(SectionHeaderComponent header, String name) {
        List<LayoutEntry> entries = layout.currentEntries();
        int index = entries.indexOf(header.entry());
        if (index < 0) return;
        String trimmed = name.trim();
        entries.set(index, header.entry().withName(trimmed.isEmpty() ? LayoutEntry.Section.DEFAULT_NAME : trimmed));
        layout.saveOrder(entries);
    }

    private void paintSectionSlot(GuiGraphicsExtractor graphics, StorageOverlayLayout.SectionSlot slot) {
        Rect area = layout.boundaryArea();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.fill(slot.x(), slot.lineY(), slot.x() + slot.width(), slot.lineY() + 1, OverlayStyle.accentColor());

        TextComponent label = sectionLabel;
        if (label != null) {
            int labelWidth = this.font.width(ADD_SECTION_TEXT);
            int labelX = slot.x() + (slot.width() - labelWidth) / 2;
            int labelY = Math.clamp(slot.lineY() - this.font.lineHeight / 2,
                    area.y() + 1, area.bottom() - this.font.lineHeight);
            graphics.fill(labelX - 3, labelY - 1, labelX + labelWidth + 3, labelY + this.font.lineHeight,
                    SECTION_LABEL_BACKGROUND);
            label.setX(labelX);
            label.setY(labelY);
            label.extractRenderStateBase(graphics, realMouseX, realMouseY, 0, this.width, this.height);
        }
        graphics.disableScissor();
    }

    private void paintHeaderHover(GuiGraphicsExtractor graphics, SectionHeaderComponent header, Rect viewport, int mouseX, int mouseY) {
        if (header.isOverGrip(mouseX, mouseY)) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
            return;
        }
        if (header.isOverArrow(mouseX, mouseY) || header.isOverTrash(mouseX, mouseY)) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            return;
        }
        if (!header.isOverName(mouseX, mouseY)) return;
        Rect name = header.nameBounds();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        graphics.fill(name.x(), name.y(), name.right(), name.bottom(), OverlayStyle.titleHoverFill());
        graphics.disableScissor();
        graphics.requestCursor(CursorTypes.IBEAM);
    }

    // Spacers draw nothing themselves, so they only become visible here, in edit mode.
    private void paintSpacers(GuiGraphicsExtractor graphics, Rect viewport) {
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (GridCellComponent cell : layout.getCells()) {
            if (cell instanceof SpacerCardComponent spacer && cell.getWidth() > 0) {
                Rect r = cell.bounds();
                boolean fill = spacer.isFill();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.PAGE_SPACER.id(),
                        r.x(), r.y(), r.width(), r.height(), fill ? ARGB.white(FILL_SPACER_ALPHA) : -1);
                int textWidth = this.font.width(FILL_SPACER_TEXT);
                if (fill && textWidth + 8 <= r.width()) {
                    graphics.text(this.font, Component.literal(FILL_SPACER_TEXT),
                            r.x() + (r.width() - textWidth) / 2, r.y() + (r.height() - this.font.lineHeight) / 2 + 1,
                            OverlayStyle.secondaryTextColor(), OverlayStyle.titleShadow());
                }
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
        if (renameBox != null || viewport == null) return;

        StorageOverlayLayout.SectionSlot slot = layout.sectionSlotAt(mouseX, mouseY);
        if (slot != null) {
            paintSectionSlot(graphics, slot);
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            return;
        }

        GridCellComponent hovered = cellAt(mouseX, mouseY);
        if (hovered == null) return;
        if (hovered instanceof SectionHeaderComponent header) {
            paintHeaderHover(graphics, header, viewport, mouseX, mouseY);
            return;
        }

        Rect title = hovered instanceof PageCardComponent card && card.isOverTitleRow(mouseX, mouseY)
                ? card.titleRowBounds() : null;
        Rect bounds = hovered.bounds();

        // Hover cues: the frame says "draggable", the title wash says "click to rename".
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.PAGE_CARD_HOVER.id(),
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (title != null) {
            graphics.fill(title.x() + 2, title.y(), title.right() - 2, title.bottom(), OverlayStyle.titleHoverFill());
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
        EditBoxWidget search = layout.getSearchBox();
        boolean typing = search != null && (search.isFocused() || getFocused() == search);
        if (typing && (event.isEscape() || event.isConfirmation())) {
            search.setFocused(false);
            setFocused(null);
            return true;
        }
        if (!typing && CopyHighlight.isSearchKey(event)) {
            layout.searchFor(CopyHighlight.hovered());
            return true;
        }
        if (!typing && layout.handleNavigationKey(event)) return true;
        if (!typing && search != null && SearchAutofocus.shouldFocus(event, !CopyHighlight.hovered().isEmpty())) {
            setFocused(search);
            search.setFocused(true);
            search.keyPressed(event);
            return true;
        }
        boolean handled = super.keyPressed(event);
        if (typing && SearchAutofocus.shouldRelease(event, search)) {
            search.setFocused(false);
            setFocused(null);
        }
        return handled;
    }

    private @Nullable GridCellComponent cellAt(double x, double y) {
        if (!Rect.contains(layout.getPageOverviewBounds(), x, y)) return null;
        for (GridCellComponent cell : layout.getCells()) {
            if (!(cell instanceof SectionEndComponent) && cell.bounds().contains(x, y)) return cell;
        }
        return null;
    }

    private void beginRename(PageCardComponent card) {
        // PageNames treats a blank name as "back to default".
        startRename(card.beginTitleEdit(this.font), name -> PageNames.getInstance().set(card.getKey(), name));
    }

    private void beginSectionRename(SectionHeaderComponent header) {
        startRename(header.beginNameEdit(this.font), name -> renameSection(header, name));
    }

    private void startRename(EditBoxWidget box, Consumer<String> commit) {
        renameBox = box;
        renameCommit = commit;
        // Typed characters reach the box through screen focus.
        setFocused(box);
        box.setFocused(true);
    }

    private void endRename(boolean save) {
        if (renameBox == null || renameCommit == null) return;
        if (save) {
            renameCommit.accept(renameBox.getValue());
            ProfileSession.getInstance().saveAll();
        }
        renameBox = null;
        renameCommit = null;
        setFocused(null);
        rebuildWidgets();
    }

    private void openResetDialog(Set<ResetPart> selected) {
        String context = layout.isRiftContext() ? "Rift Storage" : "Storage";
        List<PopupComponent.Toggle> toggles = new ArrayList<>();
        boolean anyCustom = false;
        for (ResetPart part : ResetPart.values()) {
            int count = layout.customCount(part);
            anyCustom |= count > 0;
            toggles.add(new PopupComponent.Toggle(part.label(), String.valueOf(count),
                    selected.contains(part), count > 0, () -> {
                Set<ResetPart> next = EnumSet.noneOf(ResetPart.class);
                next.addAll(selected);
                if (!next.remove(part)) next.add(part);
                openResetDialog(next);
            }));
        }
        List<PopupComponent.Option> actions = List.of(
                option("Cancel", () -> {
                }),
                option("Reset Everything", anyCustom, () -> resetLayout(EnumSet.allOf(ResetPart.class))),
                option("Reset Selected", !selected.isEmpty(), () -> resetLayout(selected)));
        popup = PopupComponent.checklist(this.width, this.height, this.font,
                List.of(Component.literal("Reset " + context + " layout?"),
                        Component.literal("Select what to restore. This can't be undone.")
                                .withStyle(ChatFormatting.RED)),
                toggles, actions);
    }

    private void resetLayout(Set<ResetPart> parts) {
        layout.resetLayout(parts);
        rebuildWidgets();
    }

    private PopupComponent.Option option(String label, Runnable action) {
        return option(label, true, action);
    }

    private PopupComponent.Option option(String label, boolean enabled, Runnable action) {
        return new PopupComponent.Option(label, enabled, () -> {
            popup = null;
            action.run();
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double ownScrollY = EnhancedStorageConfig.invertScrollDirection ? -scrollY : scrollY;
        if (popup != null) return true;
        ScrollContainerWidget navigation = layout.getNavigationList();
        if (navigation != null
                && Rect.contains(layout.getNavigationBounds(), mouseX, mouseY)
                && navigation.mouseScrolled(mouseX, mouseY, scrollX, ownScrollY)) {
            return true;
        }
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null
                && Rect.contains(layout.getPageOverviewBounds(), mouseX, mouseY)
                && overview.mouseScrolled(mouseX, mouseY, scrollX, ownScrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}