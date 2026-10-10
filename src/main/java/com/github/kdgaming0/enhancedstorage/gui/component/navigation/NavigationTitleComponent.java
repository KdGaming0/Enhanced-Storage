package com.github.kdgaming0.enhancedstorage.gui.component.navigation;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The navigation panel's title strip: "Pages", plus the collapse/expand toggle when sections exist.
 */
public class NavigationTitleComponent extends AbstractComponent {

    public static final int HEIGHT = 13;
    private static final String TITLE = "Pages";
    private static final int TEXT_X = 4;
    private static final int TEXT_Y = 3;
    private static final int MIN_GAP = 6;
    private static final int HOVER_PAD = 2;

    private final boolean hasToggle;
    private final int toggleX;
    private final int toggleWidth;

    public NavigationTitleComponent(int x, int y, int width, @Nullable String toggleLabel) {
        super(x, y, width, HEIGHT);
        Font font = Minecraft.getInstance().font;
        this.hasToggle = toggleLabel != null;
        this.toggleWidth = toggleLabel != null ? font.width(toggleLabel) : 0;
        this.toggleX = width - TEXT_X - toggleWidth;

        // On a narrow panel the toggle matters more than the title.
        if (!hasToggle || TEXT_X + font.width(TITLE) + MIN_GAP <= toggleX) {
            addText(TEXT_X, TITLE, OverlayStyle.navEmphasisColor());
        }
        if (toggleLabel != null) addText(toggleX, toggleLabel, OverlayStyle.navEmphasisColor());
    }

    private void addText(int x, String text, int color) {
        TextComponent component = new TextComponent(x, TEXT_Y, Component.literal(text), color);
        component.setDrawShadow(OverlayStyle.titleShadow());
        addComponent(component);
    }

    public boolean isOverToggle(double x, double y) {
        return hasToggle && toggleBounds().contains(x, y);
    }

    private Rect toggleBounds() {
        return new Rect(getTotalX() + toggleX - HOVER_PAD, getTotalY() + 1, toggleWidth + HOVER_PAD * 2, HEIGHT - 2);
    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                       int parentWidth, int parentHeight) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.SECTION_HEADER.id(),
                getTotalX(), getTotalY(), getWidth(), HEIGHT);
        if (isOverToggle(mouseX, mouseY)) {
            Rect hover = toggleBounds();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.NAV_ROW_HOVER.id(),
                    hover.x(), hover.y(), hover.width(), hover.height(), OverlayStyle.titleHoverFill());
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        for (IComponent component : getComponents()) {
            component.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}