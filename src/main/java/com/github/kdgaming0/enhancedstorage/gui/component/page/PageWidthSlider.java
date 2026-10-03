package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.api.widget.IWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayTheme;
import com.github.kdgaming0.enhancedstorage.storage.data.PageWidths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.function.IntConsumer;

/**
 * Themed page width slider. Snaps to whole slots and reports the width once the mouse is released,
 * so the overlay only rebuilds when the user lets go.
 */
public class PageWidthSlider extends AbstractSliderButton implements IWidget {

    private static final int STEPS = PageWidths.MAX_WIDTH - PageWidths.MIN_WIDTH;
    private static final int LABEL_PADDING = 16;

    private final int initialSlots;
    private final boolean usingDefault;
    private final IntConsumer onCommit;
    private int slots;

    public PageWidthSlider(int x, int y, int width, int height,
                           int initialSlots, boolean usingDefault, IntConsumer onCommit) {
        super(x, y, width, height, Component.empty(), toValue(initialSlots));
        this.initialSlots = initialSlots;
        this.usingDefault = usingDefault;
        this.onCommit = onCommit;
        this.slots = initialSlots;
        updateMessage();
    }

    public static int preferredWidth(Font font) {
        return font.width(label(PageWidths.MAX_WIDTH, true)) + LABEL_PADDING;
    }

    private static double toValue(int slots) {
        return (double) (slots - PageWidths.MIN_WIDTH) / STEPS;
    }

    private static String label(int slots, boolean isDefault) {
        return "Width: " + slots + " slots" + (isDefault ? " (default)" : "");
    }

    @Override
    protected void applyValue() {
        slots = PageWidths.MIN_WIDTH + (int) Math.round(value * STEPS);
        // Snapping the stored value keeps the handle on whole slots while dragging.
        value = toValue(slots);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label(slots, usingDefault && slots == initialSlots)));
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        if (slots != initialSlots) onCommit.accept(slots);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int color = ARGB.white(this.alpha);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.SLIDER_BUTTON),
                getX(), getY(), getWidth(), getHeight(), color);
        int handleX = getX() + (int) (value * (getWidth() - HANDLE_WIDTH));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE),
                handleX, getY(), HANDLE_WIDTH, getHeight(), color);

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int textX = getX() + (getWidth() - font.width(message)) / 2;
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        graphics.text(font, message, textX, textY, OverlayTheme.titleColor(), OverlayTheme.titleShadow());
        handleCursor(graphics);
    }
}