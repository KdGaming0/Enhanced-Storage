package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.gui.component.item.ItemComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
        if (EnhancedStorageConfig.fastCachedItemDecorations && isDecorated()) {
            ItemStack stack = getItemStack();
            int x = getTotalX();
            int y = getTotalY();

            guiGraphics.fakeItem(stack, x, y);
            extractDecorationsFast(guiGraphics, stack, x, y);
        } else {
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (tooltipEnabled && isHovered(guiGraphics, mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    getItemStack(),
                    mouseX,
                    mouseY
            );
        }
    }

    // Vanilla's itemDecorations is wrapped by several mods that fire events per item per frame
    // (SkyHanni, skyblock-api, ...), which dominates the overlay's render cost. Cached items only
    // need the stack count and durability bar, so draw those directly.
    private static void extractDecorationsFast(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y) {
        if (stack.getCount() != 1) {
            Font font = Minecraft.getInstance().font;
            String count = String.valueOf(stack.getCount());
            guiGraphics.text(font, count, x + 19 - 2 - font.width(count), y + 6 + 3, 0xFFFFFFFF, true);
        }

        if (stack.isBarVisible()) {
            int barWidth = stack.getBarWidth();
            int barColor = stack.getBarColor();
            guiGraphics.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
            guiGraphics.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, 0xFF000000 | barColor);
        }
    }

    protected boolean isHovered(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        return mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight()
                && guiGraphics.containsPointInScissor(mouseX, mouseY);
    }
}