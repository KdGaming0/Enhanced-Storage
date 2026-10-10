package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.component.page.TooltipItemComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A square overlay button: a sprite icon or an item, centred on a nine-slice card background, with a tooltip.
 * Click handling is done by the screen (positional), so left and right clicks can do different things.
 */
public class ToolbarButtonComponent extends AbstractComponent {

    private static final int ICON_SIZE = 16;
    private static final int TITLE_COLOR = 0x55FF55;
    private static final int HINT_COLOR = 0xFFAA00;

    private final List<Component> tooltip;

    private ToolbarButtonComponent(int x, int y, int size, IComponent icon, List<Component> tooltip) {
        super(x, y, size, size);
        this.tooltip = tooltip;
        // Nine-slice background, scaled to the button size.
        this.addComponent(new SpriteComponent(0, 0, size, size, Sprite.PAGE_CARD_IDLE.id()));
        this.addComponent(icon);
    }

    public static ToolbarButtonComponent icon(int x, int y, int size, Identifier sprite, List<Component> tooltip) {
        int inset = (size - ICON_SIZE) / 2;
        return new ToolbarButtonComponent(x, y, size, new SpriteComponent(inset, inset, ICON_SIZE, ICON_SIZE, sprite), tooltip);
    }

    /**
     * The item is drawn without decorations or a tooltip of its own.
     */
    public static ToolbarButtonComponent item(int x, int y, int size, ItemStack stack, List<Component> tooltip) {
        int inset = (size - ICON_SIZE) / 2;
        TooltipItemComponent item = new TooltipItemComponent(inset, inset, stack, false);
        item.setTooltipEnabled(false);
        return new ToolbarButtonComponent(x, y, size, item, tooltip);
    }

    /**
     * A tooltip's first line: what the button does.
     */
    public static Component title(String text) {
        return line(text, TITLE_COLOR);
    }

    /**
     * A further tooltip line: how to use the button.
     */
    public static Component hint(String text) {
        return line(text, HINT_COLOR);
    }

    private static Component line(String text, int color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withItalic(false));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {

    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        super.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        boolean hovered = mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight();
        if (hovered && !tooltip.isEmpty()) {
            guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }
}
