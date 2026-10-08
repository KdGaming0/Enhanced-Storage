package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle.Sprite;
import com.github.kdgaming0.enhancedstorage.gui.component.page.TooltipItemComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A clickable button that shows an item icon on a nine-slice card background.
 * Click handling is done by the screen (positional) so left/right can be split.
 */
public class ItemButtonComponent extends AbstractComponent {

    public ItemButtonComponent(int x, int y, int width, int height, ItemStack stack) {
        super(x, y, width, height);

        // Nine-slice background, scaled to the button size.
        SpriteComponent background = new SpriteComponent(0, 0, width, height, Sprite.PAGE_CARD_IDLE.id());
        this.addComponent(background);

        // Center the 16x16 item on the background.
        int itemX = (width - 16) / 2;
        int itemY = (height - 16) / 2;
        TooltipItemComponent item = new TooltipItemComponent(itemX, itemY, stack, false);
        item.setTooltipEnabled(false);
        this.addComponent(item);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {

    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        super.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        boolean hovered = mouseX >= getTotalX() && mouseX < getTotalX() + getWidth() && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight();
        if (hovered) {
            guiGraphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    List.of(
                            Component.literal("Open Toolkits").withStyle(s -> s.withColor(0x55FF55).withItalic(false)),
                            Component.literal("Left-click: Farming Toolkit").withStyle(s -> s.withColor(0xFFAA00).withItalic(false)),
                            Component.literal("Right-click: Hunting Toolkit").withStyle(s -> s.withColor(0xFFAA00).withItalic(false))
                    ),
                    java.util.Optional.empty(),
                    mouseX, mouseY
            );
        }
    }
}