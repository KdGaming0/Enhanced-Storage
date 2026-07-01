package com.github.kdgaming0.enhancedstorage.integration;

import com.github.kdgaming0.enhancedstorage.gui.Rect;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZonesProvider;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Optional integration with Roughly Enough Items (REI).
 *
 * @see <a href="https://github.com/shedaniel/RoughlyEnoughItems">REI</a> — pattern adapted from its own
 *      {@code DefaultRecipeBookExclusionZones} + client plugin.
 */
public final class EnhancedStorageReiPlugin implements REIClientPlugin, ExclusionZonesProvider<AbstractContainerScreen<?>> {

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AbstractContainerScreen.class, this);
    }

    @Override
    public Collection<Rectangle> provide(AbstractContainerScreen<?> screen) {
        List<Rect> bounds = StorageOverlay.getActiveBoundsFor(screen);
        if (bounds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Rectangle> zones = new ArrayList<>(bounds.size());
        for (Rect r : bounds) {
            zones.add(new Rectangle(r.x, r.y, r.width, r.height));
        }
        return zones;
    }
}
