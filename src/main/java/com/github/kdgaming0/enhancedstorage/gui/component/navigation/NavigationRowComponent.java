package com.github.kdgaming0.enhancedstorage.gui.component.navigation;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayTheme;
import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionHeaderComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * One line of the navigation panel: a section or a page that the grid can jump to.
 */
public class NavigationRowComponent extends AbstractComponent {

    private static final int HEIGHT = 11;
    private static final int ICON_SIZE = 8;
    private static final int ICON_X = 4;
    private static final int ICON_Y = 1;
    private static final int TEXT_Y = 2;
    private static final int TEXT_GAP = 3;
    private static final int GUIDE_X = ICON_X + 3;
    private static final int INDENT = GUIDE_X + 1 + TEXT_GAP - ICON_X;
    private static final int GUIDE_END_GAP = 2;
    private static final int LINE_GAP = 4;
    private static final int RIGHT_PAD = 0;
    private static final float CACHED_ALPHA = 0.7F;
    private static final float UNCACHED_ALPHA = 0.4F;

    private final GridCellComponent target;
    private final String name;
    private final boolean section;
    private final boolean collapsed;
    private final boolean indented;
    private final boolean open;
    private final boolean cached;
    private final boolean continues;
    private final boolean dimmed;
    private final boolean truncated;
    private final int iconX;
    private final int textEnd;

    private NavigationRowComponent(int width, GridCellComponent target, String name, boolean section,
                                   boolean collapsed, boolean indented, boolean open, boolean cached,
                                   boolean continues, boolean dimmed) {
        super(0, 0, width, HEIGHT);
        this.target = target;
        this.name = name;
        this.section = section;
        this.collapsed = collapsed;
        this.indented = indented;
        this.open = open;
        this.cached = cached;
        this.continues = continues;
        this.dimmed = dimmed;
        this.iconX = indented ? ICON_X + INDENT : ICON_X;

        Font font = Minecraft.getInstance().font;
        int textX = iconX + ICON_SIZE + TEXT_GAP;
        int textWidth = width - textX - RIGHT_PAD;
        this.truncated = font.width(name) > textWidth;
        this.textEnd = textX + Math.min(font.width(name), textWidth);
        int color = open ? OverlayTheme.navActiveTitleColor()
                : dimmed ? OverlayTheme.navUncachedColor()
                : section ? OverlayTheme.navEmphasisColor()
                : cached ? OverlayTheme.navPageColor()
                : OverlayTheme.navUncachedColor();
        TruncatedTextComponent text = new TruncatedTextComponent(textX, TEXT_Y, textWidth, Component.literal(name), color);
        text.setDrawShadow(OverlayTheme.titleShadow());
        addComponent(text);
    }

    /**
     * @param hasPages whether page rows follow, so the guide line connects to the triangle
     * @param dimmed   no page in the section matches the search (dim mode)
     */
    public static NavigationRowComponent section(int width, SectionHeaderComponent header, boolean hasPages,
                                                 boolean dimmed) {
        // The saved state, like the grid's arrow, even while the live page keeps the section open.
        LayoutEntry.Section entry = header.entry();
        return new NavigationRowComponent(width, header, entry.name(), true, entry.collapsed(), false, false, true,
                hasPages, dimmed);
    }

    /**
     * @param continues whether the next row is another page of the same section
     * @param dimmed    the page doesn't match the search (dim mode)
     */
    public static NavigationRowComponent page(int width, PageCardComponent card, boolean indented,
                                              boolean continues, boolean open, boolean dimmed) {
        StorageKey key = card.getKey();
        String name = PageNames.getInstance().get(key).orElse(key.displayName());
        return new NavigationRowComponent(width, card, name, false, false, indented, open, card.isCached(),
                continues, dimmed);
    }

    private static void blit(GuiGraphicsExtractor graphics, String sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(sprite), x, y, width, height);
    }

    public GridCellComponent target() {
        return target;
    }

    public String name() {
        return name;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), getHeight());
    }

    public boolean isOverTriangle(double x, double y) {
        return section && new Rect(getTotalX(), getTotalY(), ICON_X + ICON_SIZE + 2, HEIGHT).contains(x, y);
    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                       int parentWidth, int parentHeight) {
        int x = getTotalX();
        int y = getTotalY();
        if (open) blit(graphics, OverlayTheme.NAV_ROW_ACTIVE, x, y, getWidth(), HEIGHT);
        // Rows half outside the list are clipped, so only count hover inside the visible part.
        if (bounds().contains(mouseX, mouseY) && graphics.containsPointInScissor(mouseX, mouseY)) {
            blit(graphics, OverlayTheme.NAV_ROW_HOVER, x, y, getWidth(), HEIGHT);
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }

        if (section) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    OverlayTheme.sprite(collapsed ? OverlayTheme.NAV_SECTION_COLLAPSED : OverlayTheme.NAV_SECTION_EXPANDED),
                    x + ICON_X, y + ICON_Y, ICON_SIZE, ICON_SIZE, dimmed ? UNCACHED_ALPHA : 1.0F);
            // Joins the triangle to the guide line of the pages below.
            int belowIcon = ICON_Y + ICON_SIZE;
            if (continues) blit(graphics, OverlayTheme.NAV_GUIDE, x + GUIDE_X, y + belowIcon, 1, HEIGHT - belowIcon);
            int lineX = textEnd + LINE_GAP;
            if (lineX < getWidth()) blit(graphics, OverlayTheme.NAV_SECTION, x + lineX, y, getWidth() - lineX, HEIGHT);
        } else {
            // The last page's line stops short so it doesn't run into the next group.
            if (indented) {
                blit(graphics, OverlayTheme.NAV_GUIDE, x + GUIDE_X, y, 1, continues ? HEIGHT : HEIGHT - GUIDE_END_GAP);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.NAV_PAGE),
                    x + iconX, y + ICON_Y, ICON_SIZE, ICON_SIZE, cached && !dimmed ? CACHED_ALPHA : UNCACHED_ALPHA);
        }

        for (IComponent component : getComponents()) {
            component.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}