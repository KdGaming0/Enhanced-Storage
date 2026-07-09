package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.gui.component.item.ItemComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class TooltipItemComponent extends ItemComponent {

    private boolean tooltipEnabled = true;

    public TooltipItemComponent(ItemStack itemStack) {
        super(itemStack);
    }

    public TooltipItemComponent(int x, int y, ItemStack itemStack) {
        super(x, y, itemStack);
    }

    public TooltipItemComponent(int x, int y, ItemStack itemStack, boolean decorated) {
        super(x, y, itemStack, decorated);
    }

    public boolean isTooltipEnabled() {
        return tooltipEnabled;
    }

    public void setTooltipEnabled(boolean tooltipEnabled) {
        this.tooltipEnabled = tooltipEnabled;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        if (tooltipEnabled && isHovered(guiGraphics, mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    getItemStack(),
                    mouseX,
                    mouseY
            );
        }
    }

    protected boolean isHovered(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        return mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight()
                && guiGraphics.containsPointInScissor(mouseX, mouseY);
    }
}