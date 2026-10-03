package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A small panel with a few lines of text and a stack of buttons, drawn above the overlay.
 * Purely visual: the owning screen routes clicks to {@link #getWidgets()} and decides when it closes.
 */
public class PopupComponent extends AbstractComponent {

    public record Option(String label, Runnable action) {
    }

    @FunctionalInterface
    public interface WidgetFactory {
        IWidget create(int x, int y, int width, int height);
    }

    private static final int PADDING = 6;
    private static final int LINE_GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_LABEL_PADDING = 16;
    private static final int CONFIRM_WIDTH = 200;

    private static final int SCRIM_COLOR = 0xC0000000;

    private final boolean modal;
    private final int panelX;
    private final int panelY;
    private final int panelWidth;
    private final int panelHeight;

    /**
     * Centred over a dimming scrim. The first line is drawn as the title.
     */
    public static PopupComponent confirm(int screenWidth, int screenHeight, Font font,
                                         List<Component> lines, List<Option> options) {
        int height = panelHeight(font, lines.size(), options.size());
        return new PopupComponent(screenWidth, screenHeight, font,
                (screenWidth - CONFIRM_WIDTH) / 2, (screenHeight - height) / 2,
                CONFIRM_WIDTH, true, lines, null, options);
    }

    /**
     * A right-click menu opening at the cursor, shifted to stay on screen.
     */
    public static PopupComponent menu(int screenWidth, int screenHeight, Font font, int x, int y, List<Option> options) {
        return menu(screenWidth, screenHeight, font, x, y, null, 0, options);
    }

    /**
     * A right-click menu with an extra full-width widget, such as a slider, above the buttons.
     */
    public static PopupComponent menu(int screenWidth, int screenHeight, Font font, int x, int y,
                                      @Nullable WidgetFactory header, int headerWidth, List<Option> options) {
        int labelWidth = options.stream().mapToInt(o -> font.width(o.label())).max().orElse(0);
        int width = PADDING * 2 + Math.max(labelWidth + BUTTON_LABEL_PADDING, headerWidth);
        int height = panelHeight(font, 0, rowCount(header, options));
        return new PopupComponent(screenWidth, screenHeight, font,
                Math.min(x, screenWidth - width), Math.min(y, screenHeight - height),
                width, false, List.of(), header, options);
    }

    private static int rowCount(@Nullable WidgetFactory header, List<Option> options) {
        return options.size() + (header != null ? 1 : 0);
    }

    private static int panelHeight(Font font, int lineCount, int optionCount) {
        int text = lineCount * (font.lineHeight + LINE_GAP);
        int gap = lineCount > 0 && optionCount > 0 ? BUTTON_GAP : 0;
        int buttons = optionCount * BUTTON_HEIGHT + Math.max(0, optionCount - 1) * BUTTON_GAP;
        return PADDING * 2 + text + gap + buttons;
    }

    // Spans the whole screen so the scrim covers everything; children use screen coordinates.
    private PopupComponent(int screenWidth, int screenHeight, Font font, int x, int y, int width,
                           boolean modal, List<Component> lines, @Nullable WidgetFactory header, List<Option> options) {
        super(0, 0, screenWidth, screenHeight);
        this.modal = modal;
        this.panelX = Math.max(0, x);
        this.panelY = Math.max(0, y);
        this.panelWidth = width;
        this.panelHeight = panelHeight(font, lines.size(), rowCount(header, options));

        int cursorY = panelY + PADDING;
        for (Component line : lines) {
            TextComponent text = new TextComponent(panelX + PADDING, cursorY, line, lineColor(line));
            text.setDrawShadow(OverlayTheme.titleShadow());
            addComponent(text);
            cursorY += font.lineHeight + LINE_GAP;
        }
        if (!lines.isEmpty() && rowCount(header, options) > 0) cursorY += BUTTON_GAP;

        if (header != null) {
            addWidget(header.create(panelX + PADDING, cursorY, panelWidth - PADDING * 2, BUTTON_HEIGHT));
            cursorY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        Identifier idle = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE);
        Identifier active = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE);
        WidgetSprites sprites = new WidgetSprites(idle, idle, active);
        int labelColor = OverlayTheme.titleColor() & 0xFFFFFF;

        for (Option option : options) {
            Component label = Component.literal(option.label()).withStyle(s -> s.withColor(labelColor));
            addWidget(new PageCardButtonWidget(panelX + PADDING, cursorY, panelWidth - PADDING * 2, BUTTON_HEIGHT,
                    label, () -> sprites, b -> option.action().run()));
            cursorY += BUTTON_HEIGHT + BUTTON_GAP;
        }
    }

    // A line styled with its own colour (e.g. a red warning) keeps it; plain lines follow the theme.
    private static int lineColor(Component line) {
        TextColor color = line.getStyle().getColor();
        return color != null ? 0xFF000000 | color.getValue() : OverlayTheme.titleColor();
    }

    public boolean isOverPanel(double x, double y) {
        return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight;
    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                       float partialTick, int parentWidth, int parentHeight) {
        this.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        for (IWidget widget : getWidgets()) {
            widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
        for (IComponent component : getComponents()) {
            component.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
        if (modal) {
            guiGraphics.fill(0, 0, getWidth(), getHeight(), SCRIM_COLOR);
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE),
                panelX, panelY, panelWidth, panelHeight);
    }
}