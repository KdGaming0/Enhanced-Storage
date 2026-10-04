package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.highlight.SearchHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class StorageSlotComponent extends AbstractComponent {

    public static final int ITEM_INSET = 1;
    private static final int ITEM_SIZE = 16;
    private final @Nullable SpriteComponent highlightBack;
    private final @Nullable SpriteComponent highlightFront;
    private final @Nullable TooltipItemComponent item;
    private final ItemStack stack;
    private final int itemInset;
    private boolean hoverEnabled = true;
    private SearchState searchState = SearchState.NONE;

    public StorageSlotComponent(int x, int y, int width, int height,
                                @Nullable Identifier highlightBackTexture,
                                @Nullable Identifier highlightFrontTexture,
                                @Nullable ItemStack stack) {
        super(x, y, width, height);
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.itemInset = itemInset(width);

        int highlightOffset = itemInset - ITEM_INSET;
        int highlightSize = ITEM_SIZE + ITEM_INSET * 2;
        this.highlightBack = highlightBackTexture == null ? null
                : new SpriteComponent(highlightOffset, highlightOffset, highlightSize, highlightSize, highlightBackTexture);
        if (this.highlightBack != null) this.addComponent(this.highlightBack);

        this.highlightFront = highlightFrontTexture == null ? null
                : new SpriteComponent(highlightOffset, highlightOffset, highlightSize, highlightSize, highlightFrontTexture);
        if (this.highlightFront != null) this.addComponent(this.highlightFront);

        if (stack != null && !stack.isEmpty()) {
            this.item = new TooltipItemComponent(itemInset, itemInset, stack, true);
            this.item.setTooltipEnabled(EnhancedStorageConfig.showItemTooltipsOnCachedItems);
            this.addComponent(this.item);
        } else {
            this.item = null;
        }
    }

    public static int itemInset(int slotSize) {
        return (slotSize - ITEM_SIZE) / 2;
    }

    public void setSearchState(SearchState searchState) {
        this.searchState = searchState;
    }

    public void setHoverEnabled(boolean hoverEnabled) {
        this.hoverEnabled = hoverEnabled;
    }

    public @Nullable TooltipItemComponent getItem() {
        return item;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean contains(double x, double y) {
        return x >= getTotalX() && x < getTotalX() + getWidth() && y >= getTotalY() && y < getTotalY() + getHeight();
    }

    protected boolean isHovered(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        return mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight()
                && guiGraphics.containsPointInScissor(mouseX, mouseY);
    }

    // Controls the rendering order: back highlight -> item -> front highlight.
    // (The slot background itself is drawn by the card as one tiled sprite.)
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        // Scissor only clips GPU output — skip the CPU-side extraction entirely for slots
        // scrolled outside the viewport (cards straddling the viewport edge).
        int totalX = getTotalX();
        int totalY = getTotalY();
        if (!guiGraphics.containsPointInScissor(totalX, totalY)
                && !guiGraphics.containsPointInScissor(totalX, totalY + getHeight() - 1)) {
            return;
        }

        boolean hovered = isHovered(guiGraphics, mouseX, mouseY);

        int itemX = totalX + itemInset;
        int itemY = totalY + itemInset;

        if (searchState == SearchState.MATCH) {
            SearchHighlight.drawMatch(guiGraphics, itemX, itemY);
        }
        if (CopyHighlight.isCopy(stack)) {
            CopyHighlight.draw(guiGraphics, itemX, itemY);
        }
        // Children are leaves — extract them directly instead of via extractRenderStateBase,
        // which allocates two filtered stream lists per component per frame.
        if (hovered && highlightBack != null) {
            highlightBack.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (item != null) {
            item.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (hovered && highlightFront != null) {
            highlightFront.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (searchState == SearchState.NO_MATCH) {
            SearchHighlight.drawNoMatch(guiGraphics, itemX, itemY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
    }

    public enum SearchState {NONE, MATCH, NO_MATCH}
}
