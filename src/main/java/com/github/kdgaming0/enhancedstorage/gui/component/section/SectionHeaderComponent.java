package com.github.kdgaming0.enhancedstorage.gui.component.section;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.LiveCell;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SectionHeaderComponent extends GridCellComponent implements LiveCell {

    public static final int OPEN_HEIGHT = 16;
    public static final int CLOSED_HEIGHT = 17;
    private static final int BORDER = 2;
    private static final int STRIP_OPEN = 14;
    private static final int STRIP_CLOSED = 13;
    private static final int ICON_SIZE = 10;
    private static final int ICON_Y = BORDER + 2;
    private static final int TEXT_Y = BORDER + 3;
    private static final int EDGE_PAD = 3;
    private static final int GAP = 3;
    private static final int NAME_STATS_GAP = 8;
    private static final int MIN_NAME_TARGET = 20;

    private final LayoutEntry.Section entry;
    private final boolean collapsed;
    private final boolean editMode;
    private final int gripX;
    private final int arrowX;
    private final int nameX;
    private final int trashX;
    private final int nameMaxWidth;
    private final int nameWidth;
    private final TruncatedTextComponent nameText;
    private boolean open;
    private boolean openForDrag;
    private @Nullable EditBoxWidget nameEditor;

    /**
     * @param collapsed whether the rows are hidden right now; can differ from the saved state
     */
    public SectionHeaderComponent(int width, LayoutEntry.Section entry, boolean collapsed, boolean editMode,
                                  int pages, int freeSlots) {
        super(0, 0, width, CLOSED_HEIGHT);
        this.entry = entry;
        this.collapsed = collapsed;
        this.editMode = editMode;
        Font font = Minecraft.getInstance().font;

        this.gripX = BORDER + EDGE_PAD;
        this.arrowX = editMode ? gripX + ICON_SIZE + GAP : gripX;
        this.nameX = arrowX + ICON_SIZE + GAP;
        this.trashX = width - BORDER - EDGE_PAD - ICON_SIZE;
        int statsRight = editMode ? trashX - GAP : width - BORDER - EDGE_PAD;

        String stats = pages + (pages == 1 ? " page" : " pages") + " · " + freeSlots
                + (freeSlots == 1 ? " empty slot" : " empty slots");
        int statsX = statsRight - font.width(stats);
        TextComponent statsText = new TextComponent(statsX, TEXT_Y, Component.literal(stats), OverlayStyle.titleColor());
        statsText.setDrawShadow(OverlayStyle.titleShadow());
        addComponent(statsText);

        this.nameMaxWidth = Math.max(0, statsX - NAME_STATS_GAP - nameX);
        String name = TextUtils.colorCodes(entry.name());
        this.nameWidth = Math.min(font.width(name), nameMaxWidth);
        this.nameText = new TruncatedTextComponent(nameX, TEXT_Y, nameMaxWidth,
                Component.literal(name), OverlayStyle.titleColor());
        nameText.setDrawShadow(OverlayStyle.titleShadow());
        addComponent(nameText);
    }

    @Override
    public LayoutEntry.Section entry() {
        return entry;
    }

    public boolean isCollapsed() {
        return collapsed && !openForDrag;
    }

    public void openForDrag() {
        this.openForDrag = true;
    }

    public void closeAfterDrag() {
        this.openForDrag = false;
    }

    /**
     * Set by the layout: true when rows of this section follow, so the strip shows its separator.
     */
    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), open ? OPEN_HEIGHT : CLOSED_HEIGHT);
    }

    // Add an extra px to the bounds to make it easier to click the icons.
    private Rect iconBounds(int localX) {
        return new Rect(getTotalX() + localX - 1, getTotalY() + ICON_Y - 1, ICON_SIZE + 2, ICON_SIZE + 2);
    }

    public boolean isOverArrow(double x, double y) {
        return iconBounds(arrowX).contains(x, y);
    }

    public boolean isOverGrip(double x, double y) {
        return editMode && iconBounds(gripX).contains(x, y);
    }

    public boolean isOverTrash(double x, double y) {
        return editMode && iconBounds(trashX).contains(x, y);
    }

    public Rect nameBounds() {
        return new Rect(getTotalX() + nameX - 2, getTotalY() + BORDER, Math.max(nameWidth, MIN_NAME_TARGET) + 4, STRIP_CLOSED);
    }

    public boolean isOverName(double x, double y) {
        return nameBounds().contains(x, y);
    }

    /**
     * Same borderless box as page titles. The default name is a hint, so a fresh header starts empty.
     */
    public EditBoxWidget beginNameEdit(Font font) {
        String defaultName = LayoutEntry.Section.DEFAULT_NAME;
        EditBoxWidget box = new EditBoxWidget(font, nameX, TEXT_Y, nameMaxWidth, font.lineHeight,
                Component.literal("Section name | Click to edit"));
        box.setBordered(false);
        box.setMaxLength(PageNames.MAX_NAME_LENGTH);
        box.setTextColor(OverlayStyle.titleColor());
        box.setResponder(text -> box.setSuggestion(text.isEmpty() ? defaultName : null));
        box.setValue(entry.name().equals(defaultName) ? "" : entry.name());
        box.setSuggestion(box.getValue().isEmpty() ? defaultName : null);

        this.nameEditor = box;
        this.addWidget(box);
        return box;
    }

    @Override
    public boolean drawsLive() {
        return nameEditor != null;
    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        // The box lives in header space, so re-pin it as the viewport scrolls.
        if (nameEditor != null) {
            nameEditor.uilib$updateParentPosition(getTotalX(), getTotalY());
        }

        int x = getTotalX();
        int y = getTotalY();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.SECTION_HEADER.id(),
                x + BORDER, y + BORDER, getWidth() - BORDER * 2, open ? STRIP_OPEN : STRIP_CLOSED);
        if (editMode) {
            drawIcon(graphics, Sprite.SECTION_GRIP.id(), gripX,
                    isOverGrip(mouseX, mouseY) ? OverlayStyle.iconHoverColor() : OverlayStyle.iconColor());
            drawIcon(graphics, Sprite.SECTION_TRASH.id(), trashX,
                    isOverTrash(mouseX, mouseY) ? OverlayStyle.dangerColor() : OverlayStyle.iconColor());
        }
        // Update the arrow icon based on the current state.
        boolean showCollapsed = entry.collapsed() && !openForDrag;
        drawIcon(graphics, showCollapsed ? Sprite.SECTION_COLLAPSE_ARROW.id() : Sprite.SECTION_EXPAND_ARROW.id(), arrowX,
                OverlayStyle.iconColor());

        for (IComponent component : getComponents()) {
            if (component == nameText && nameEditor != null) continue;
            component.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
        for (IWidget widget : getWidgets()) {
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawIcon(GuiGraphicsExtractor graphics, Identifier sprite, int localX, int color) {
        OverlayStyle.blitIcon(graphics, sprite,
                getTotalX() + localX, getTotalY() + ICON_Y, ICON_SIZE, ICON_SIZE, color);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}