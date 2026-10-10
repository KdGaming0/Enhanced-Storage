package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;

/**
 * A checkbox row: box, label, and a right-aligned detail.
 */
public class ToggleRowWidget extends ButtonWidget {

    private static final int INSET = 5;
    private static final int BOX = 9;
    private static final int BOX_GAP = 4;
    private static final int DETAIL_GAP = 8;

    private final Component label;
    private final Component detail;
    private final boolean checked;

    public ToggleRowWidget(int x, int y, int width, int height, String label, String detail,
                           boolean checked, OnPress onPress) {
        super(x, y, width, height, Component.literal(label), onPress);
        this.label = Component.literal(label);
        this.detail = Component.literal(detail);
        this.checked = checked;
    }

    public static int contentWidth(Font font, String label, String detail) {
        return INSET + BOX + BOX_GAP + font.width(label) + DETAIL_GAP + font.width(detail) + INSET;
    }

    private static void drawOutline(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y + size - 1, x + size, y + size, color);
        g.fill(x, y + 1, x + 1, y + size - 1, color);
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    private static int dim(int color) {
        return (color & 0xFFFFFF) | 0x80000000;
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        Identifier sprite = checked ? Sprite.PAGE_CARD_ACTIVE.id()
                : active && isHoveredOrFocused() ? Sprite.PAGE_CARD_HOVER.id()
                : Sprite.PAGE_CARD_IDLE.id();
        float spriteAlpha = active ? alpha : alpha * 0.5f;
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                getX(), getY(), getWidth(), getHeight(), ARGB.white(spriteAlpha));

        Font font = Minecraft.getInstance().font;
        int textColor = active ? OverlayStyle.titleColor() : dim(OverlayStyle.secondaryTextColor());
        int detailColor = active ? OverlayStyle.secondaryTextColor() : textColor;
        boolean shadow = OverlayStyle.titleShadow();

        int boxX = getX() + INSET;
        int boxY = getY() + (getHeight() - BOX) / 2;
        drawOutline(guiGraphics, boxX, boxY, BOX, textColor);
        if (checked) {
            guiGraphics.fill(boxX + 2, boxY + 2, boxX + BOX - 2, boxY + BOX - 2, textColor);
        }

        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        guiGraphics.text(font, label, boxX + BOX + BOX_GAP, textY, textColor, shadow);
        guiGraphics.text(font, detail, getX() + getWidth() - INSET - font.width(detail), textY, detailColor, shadow);
    }
}