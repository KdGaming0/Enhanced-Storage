package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.gui.component.item.ItemComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public class TooltipItemComponent extends ItemComponent {

    private boolean tooltipEnabled = true;
    private boolean glintHideable;
    private ItemStack noGlintStack;
    private int shownCount = 1;
    private FormattedCharSequence countText = FormattedCharSequence.EMPTY;

    public TooltipItemComponent(ItemStack itemStack) {
        super(itemStack);
    }

    public TooltipItemComponent(int x, int y, ItemStack itemStack) {
        super(x, y, itemStack);
    }

    public TooltipItemComponent(int x, int y, ItemStack itemStack, boolean decorated) {
        super(x, y, itemStack, decorated);
    }

    // Vanilla's itemDecorations is wrapped by several mods that fire events per item per frame
    // (SkyHanni, skyblock-api, ...), which dominates the overlay's render cost. Cached items only
    // need the stack count and durability bar, so draw those directly.
    private void extractDecorationsFast(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y) {
        int count = stack.getCount();
        if (count != 1) {
            Font font = Minecraft.getInstance().font;
            if (count != shownCount) {
                shownCount = count;
                countText = Language.getInstance().getVisualOrder(FormattedText.of(String.valueOf(count)));
            }
            guiGraphics.text(font, countText, x + 19 - 2 - font.width(countText), y + 6 + 3, 0xFFFFFFFF, true);
        }

        if (stack.isBarVisible()) {
            int barWidth = stack.getBarWidth();
            int barColor = stack.getBarColor();
            guiGraphics.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
            guiGraphics.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, 0xFF000000 | barColor);
        }
    }

    public void setTooltipEnabled(boolean tooltipEnabled) {
        this.tooltipEnabled = tooltipEnabled;
    }

    public void setGlintHideable(boolean glintHideable) {
        this.glintHideable = glintHideable;
    }

    private ItemStack glintlessStack() {
        if (!glintHideable || !EnhancedStorageConfig.hideEnchantGlintOnCachedItems) return null;
        ItemStack stack = getItemStack();
        if (noGlintStack == null) {
            if (stack.hasFoil()) {
                noGlintStack = stack.copy();
                noGlintStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            } else {
                noGlintStack = ItemStack.EMPTY;
            }
        }
        return noGlintStack.isEmpty() ? null : noGlintStack;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        ItemStack glintless = glintlessStack();
        if (EnhancedStorageConfig.fastCachedItemDecorations && isDecorated()) {
            ItemStack stack = getItemStack();
            int x = getTotalX();
            int y = getTotalY();

            guiGraphics.fakeItem(glintless != null ? glintless : stack, x, y);
            extractDecorationsFast(guiGraphics, stack, x, y);
        } else if (glintless != null) {
            int x = getTotalX();
            int y = getTotalY();

            guiGraphics.fakeItem(glintless, x, y);
            if (isDecorated()) guiGraphics.itemDecorations(Minecraft.getInstance().font, getItemStack(), x, y);
        } else {
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        extractTooltip(guiGraphics, mouseX, mouseY);
    }

    /** Shows the item's tooltip if the mouse is over it; also used where the item itself is drawn from a cache. */
    public void extractTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (tooltipEnabled && !CopyHighlight.hidesTooltips() && isHovered(guiGraphics, mouseX, mouseY)) {
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