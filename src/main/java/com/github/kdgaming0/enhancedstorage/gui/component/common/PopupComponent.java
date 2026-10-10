package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageCardButtonWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A small panel with a few lines of text and a stack of buttons, drawn above the overlay.
 * Purely visual: the owning screen routes clicks to {@link #getWidgets()} and decides when it closes.
 */
public class PopupComponent extends AbstractComponent {

    private static final int PADDING = 6;
    private static final int LINE_GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_LABEL_PADDING = 16;
    private static final int CONFIRM_WIDTH = 200;
    private static final int TOGGLE_COLUMNS = 2;
    private static final int DIVIDER_GAP = 6;
    private static final int SCRIM_COLOR = 0xC0000000;
    private final boolean modal;
    private final int panelX;
    private final int panelY;
    private final int panelWidth;
    private final int panelHeight;
    private int dividerY = -1;

    // Spans the whole screen so the scrim covers everything; children use screen coordinates.
    private PopupComponent(int screenWidth, int screenHeight, int x, int y, int width, int height, boolean modal) {
        super(0, 0, screenWidth, screenHeight);
        this.modal = modal;
        this.panelX = Math.max(0, x);
        this.panelY = Math.max(0, y);
        this.panelWidth = width;
        this.panelHeight = height;
    }

    private PopupComponent(int screenWidth, int screenHeight, Font font, int x, int y, int width,
                           boolean modal, List<Component> lines, @Nullable WidgetFactory header, List<Option> options) {
        this(screenWidth, screenHeight, x, y, width,
                panelHeight(font, lines.size(), rowCount(header, options)), modal);

        int cursorY = addLines(font, lines);
        if (!lines.isEmpty() && rowCount(header, options) > 0) cursorY += BUTTON_GAP;

        if (header != null) {
            addWidget(header.create(panelX + PADDING, cursorY, panelWidth - PADDING * 2, BUTTON_HEIGHT));
            cursorY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        for (Option option : options) {
            addButton(option, panelX + PADDING, cursorY, panelWidth - PADDING * 2);
            cursorY += BUTTON_HEIGHT + BUTTON_GAP;
        }
    }

    /**
     * Centred over a dimming scrim: text lines, a grid of checkbox rows, a divider, then one row of actions.
     * The panel widens to fit its content.
     */
    public static PopupComponent checklist(int screenWidth, int screenHeight, Font font, List<Component> lines,
                                           List<Toggle> toggles, List<Option> actions) {
        int lineWidth = lines.stream().mapToInt(l -> font.width(l)).max().orElse(0);
        int toggleWidth = toggles.stream()
                .mapToInt(t -> ToggleRowWidget.contentWidth(font, t.label(), t.detail())).max().orElse(0);
        int actionWidth = actions.stream()
                .mapToInt(o -> font.width(o.label()) + BUTTON_LABEL_PADDING).max().orElse(0);
        int inner = Math.max(CONFIRM_WIDTH - PADDING * 2, Math.max(lineWidth, Math.max(
                rowWidth(toggleWidth, TOGGLE_COLUMNS), rowWidth(actionWidth, actions.size()))));

        int toggleRows = (toggles.size() + TOGGLE_COLUMNS - 1) / TOGGLE_COLUMNS;
        int height = PADDING * 2
                + lines.size() * (font.lineHeight + LINE_GAP) + (lines.isEmpty() ? 0 : BUTTON_GAP)
                + stackHeight(toggleRows) + DIVIDER_GAP * 2 + 1 + BUTTON_HEIGHT;
        int width = inner + PADDING * 2;

        PopupComponent popup = new PopupComponent(screenWidth, screenHeight,
                (screenWidth - width) / 2, (screenHeight - height) / 2, width, height, true);
        int innerX = popup.panelX + PADDING;

        int cursorY = popup.addLines(font, lines);
        if (!lines.isEmpty()) cursorY += BUTTON_GAP;

        int columnWidth = (inner - BUTTON_GAP * (TOGGLE_COLUMNS - 1)) / TOGGLE_COLUMNS;
        for (int i = 0; i < toggles.size(); i++) {
            Toggle toggle = toggles.get(i);
            int x = innerX + (i % TOGGLE_COLUMNS) * (columnWidth + BUTTON_GAP);
            int y = cursorY + (i / TOGGLE_COLUMNS) * (BUTTON_HEIGHT + BUTTON_GAP);
            ToggleRowWidget row = new ToggleRowWidget(x, y, columnWidth, BUTTON_HEIGHT,
                    toggle.label(), toggle.detail(), toggle.checked(), b -> toggle.action().run());
            row.active = toggle.enabled();
            popup.addWidget(row);
        }
        cursorY += stackHeight(toggleRows) + DIVIDER_GAP;
        popup.dividerY = cursorY;
        cursorY += 1 + DIVIDER_GAP;

        int buttonWidth = (inner - BUTTON_GAP * (actions.size() - 1)) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            int x = innerX + i * (buttonWidth + BUTTON_GAP);
            // The last button absorbs the rounding remainder so the row ends flush.
            int w = i == actions.size() - 1 ? innerX + inner - x : buttonWidth;
            popup.addButton(actions.get(i), x, cursorY, w);
        }
        return popup;
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

    private static int rowWidth(int cellWidth, int count) {
        return count == 0 ? 0 : cellWidth * count + BUTTON_GAP * (count - 1);
    }

    private static int stackHeight(int rows) {
        return rows * BUTTON_HEIGHT + Math.max(0, rows - 1) * BUTTON_GAP;
    }

    private static int panelHeight(Font font, int lineCount, int optionCount) {
        int text = lineCount * (font.lineHeight + LINE_GAP);
        int gap = lineCount > 0 && optionCount > 0 ? BUTTON_GAP : 0;
        return PADDING * 2 + text + gap + stackHeight(optionCount);
    }

    // A line styled with its own colour (e.g. a red warning) keeps it; plain lines follow the theme.
    private static int lineColor(Component line) {
        TextColor color = line.getStyle().getColor();
        return color != null ? 0xFF000000 | color.getValue() : OverlayStyle.titleColor();
    }

    private int addLines(Font font, List<Component> lines) {
        int cursorY = panelY + PADDING;
        for (Component line : lines) {
            TextComponent text = new TextComponent(panelX + PADDING, cursorY, line, lineColor(line));
            text.setDrawShadow(OverlayStyle.titleShadow());
            addComponent(text);
            cursorY += font.lineHeight + LINE_GAP;
        }
        return cursorY;
    }

    private void addButton(Option option, int x, int y, int width) {
        Identifier idle = Sprite.PAGE_CARD_IDLE.id();
        Identifier active = Sprite.PAGE_CARD_ACTIVE.id();
        WidgetSprites sprites = new WidgetSprites(idle, idle, active);
        int color = (option.enabled() ? OverlayStyle.titleColor() : OverlayStyle.secondaryTextColor()) & 0xFFFFFF;
        Component label = Component.literal(option.label()).withStyle(s -> s.withColor(color));
        PageCardButtonWidget button = new PageCardButtonWidget(x, y, width, BUTTON_HEIGHT, label,
                () -> sprites, b -> option.action().run());
        button.active = option.enabled();
        addWidget(button);
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
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
        if (modal) {
            guiGraphics.fill(0, 0, getWidth(), getHeight(), SCRIM_COLOR);
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Sprite.PAGE_CARD_IDLE.id(),
                panelX, panelY, panelWidth, panelHeight);
        if (dividerY >= 0) {
            int color = (OverlayStyle.secondaryTextColor() & 0xFFFFFF) | 0x60000000;
            guiGraphics.fill(panelX + PADDING, dividerY, panelX + panelWidth - PADDING, dividerY + 1, color);
        }
    }

    @FunctionalInterface
    public interface WidgetFactory {
        IWidget create(int x, int y, int width, int height);
    }

    public record Option(String label, boolean enabled, Runnable action) {
        public Option(String label, Runnable action) {
            this(label, true, action);
        }
    }

    public record Toggle(String label, String detail, boolean checked, boolean enabled, Runnable action) {
    }
}