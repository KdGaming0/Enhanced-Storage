package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.TooltipItemComponent;
import com.github.kdgaming0.enhancedstorage.gui.layer.LayerRefresh;
import com.github.kdgaming0.enhancedstorage.gui.layer.PageLayerCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A panel sprite whose contents only change when they are rebuilt, drawn once into a {@link PageLayerCache} layer
 * and reused while {@code enabled} holds. Hovering must not change how it or its children look: the only hover
 * feedback it gives is the tooltips of its direct {@link TooltipItemComponent} children. Nothing may draw outside
 * the panel, since the layer is clipped to it.
 */
public class CachedPanelComponent extends SpriteComponent {

    private final BooleanSupplier enabled;
    private final LayerRefresh layerRefresh = new LayerRefresh();

    private record LayerKey(int generation, List<IComponent> children, int x, int y, int width, int height, Matrix3x2f pose) {
    }

    public CachedPanelComponent(int x, int y, int width, int height, Identifier spriteLocation, BooleanSupplier enabled) {
        super(x, y, width, height, spriteLocation);
        this.enabled = enabled;
    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                       int parentWidth, int parentHeight) {
        if (!enabled.getAsBoolean()) {
            super.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
            return;
        }

        int x = getTotalX();
        int y = getTotalY();
        LayerKey key = new LayerKey(PageLayerCache.sync(graphics), List.copyOf(getComponents()), x, y, getWidth(), getHeight(),
                new Matrix3x2f(graphics.pose()));
        if (layerRefresh.prefersDirect()) {
            super.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);
            return;
        }

        if (layerRefresh.needsRedraw(key)) {
            GuiGraphicsExtractor layer = PageLayerCache.beginLayer(graphics, x, y, getWidth(), getHeight());
            layer.nextStratum();
            int noMouse = PageLayerCache.OFFSCREEN_MOUSE;
            super.extractRenderStateBase(layer, noMouse, noMouse, partialTick, parentWidth, parentHeight);
            layerRefresh.drawn(key, PageLayerCache.endLayer(layer));
        }

        PageLayerCache.blit(graphics, x, y, x + getWidth(), y + getHeight());
        if (mouseX >= x && mouseX < x + getWidth() && mouseY >= y && mouseY < y + getHeight()) {
            for (IComponent component : getComponents()) {
                if (component instanceof TooltipItemComponent item) item.extractTooltip(graphics, mouseX, mouseY);
            }
        }
    }
}
