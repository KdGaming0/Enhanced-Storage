package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.gui.widget.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Custom ButtonWidget that updated its texture every frame
 */
public class PageCardButtonWidget extends ButtonWidget {

    private final Supplier<WidgetSprites> spriteSupplier;

    public PageCardButtonWidget(int x, int y, int width, int height,
                                Component message,
                                Supplier<WidgetSprites> spriteSupplier,
                                OnPress onPress) {
        super(x, y, width, height, message, onPress);
        this.spriteSupplier = spriteSupplier;

    }

    @Override
    protected void extractContents(@NotNull GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        WidgetSprites sprites = spriteSupplier.get();
        guiGraphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                sprites.get(this.active, this.isHoveredOrFocused()),
                this.getX(), this.getY(),
                this.getWidth(), this.getHeight(),
                ARGB.white(this.alpha)
        );
        this.extractDefaultLabel(guiGraphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}

