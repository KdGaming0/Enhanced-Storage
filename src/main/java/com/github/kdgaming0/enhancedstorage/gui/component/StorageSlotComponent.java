package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class StorageSlotComponent extends AbstractComponent {

    private final SpriteComponent base;
    private final @Nullable SpriteComponent highlightBack;
    private final @Nullable SpriteComponent highlightFront;
    private final @Nullable TooltipItemComponent item;

    private boolean hoverEnabled = true;

    public StorageSlotComponent(int x, int y, int width, int height, Identifier baseTexture,
                                @Nullable Identifier highlightBackTexture,
                                @Nullable Identifier highlightFrontTexture,
                                @Nullable ItemStack stack) {
        super(x, y, width, height);

        this.base = new SpriteComponent(0,0, width, height, baseTexture);
        this.addComponent(this.base);

        this.highlightBack = highlightBackTexture == null ? null
                : new SpriteComponent(0, 0, width, height, highlightBackTexture);
        if (this.highlightBack != null) this.addComponent(this.highlightBack);

        this.highlightFront = highlightFrontTexture == null ? null
                : new SpriteComponent(0, 0, width, height, highlightFrontTexture);
        if (this.highlightFront != null) this.addComponent(this.highlightFront);

        if (stack != null && !stack.isEmpty()) {
            this.item = new TooltipItemComponent(1,1,stack,true);
            this.item.setTooltipEnabled(EnhancedStorageConfig.showItemTooltipsOnCachedItems);
            this.addComponent(this.item);
        } else {
            this.item = null;
        }
    }

    public boolean isHoverEnabled() {
        return hoverEnabled;
    }

    public void setHoverEnabled(boolean hoverEnabled) {
        this.hoverEnabled = hoverEnabled;
    }

    public @Nullable TooltipItemComponent getItem() {
        return item;
    }

    protected boolean isHovered(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        return mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight()
                && guiGraphics.containsPointInScissor(mouseX, mouseY);
    }

    // Controls the rendering order: base -> back highlight -> item -> front highlight.
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        boolean hovered = isHovered(guiGraphics, mouseX, mouseY);

        base.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        if (hovered && highlightBack != null) {
            highlightBack.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (item != null) {
            item.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }

        if (hovered && highlightFront != null) {
            highlightFront.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {}
}
