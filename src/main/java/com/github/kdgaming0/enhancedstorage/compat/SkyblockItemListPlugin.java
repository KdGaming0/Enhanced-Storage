package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.operationpotato.itemlist.api.ExclusionZoneManager;
import com.operationpotato.itemlist.api.HoveredItemManager;
import com.operationpotato.itemlist.api.Plugin;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * SkyBlock Item List plugin, declared under its {@code skyblock-item-list} entrypoint: only that mod reads it, so this
 * class never loads without it.
 *
 * <p>Its own screen-bounds provider already reads our {@code leftPos}/{@code imageWidth}, which we set to the overlay's
 * outline, so we don't register one.
 */
public final class SkyblockItemListPlugin implements Plugin {

    /**
     * Items on cached pages aren't real slots, so the list's default (the hovered slot) misses them. The stack we
     * track covers live slots and cached pages alike, so it doesn't matter which provider is asked first; its recipe
     * and usage keys and favoriting then work on stored items too.
     */
    @Override
    public void registerHoveredItems(HoveredItemManager manager) {
        manager.addProvider(screen -> screen instanceof StorageContainerScreen ? CopyHighlight.hovered() : ItemStack.EMPTY);
    }

    /**
     * SkyHanni's Chest Value panel sits outside the overlay's outline, where the list may reach.
     */
    @Override
    public void registerExclusionZones(ExclusionZoneManager manager) {
        manager.addProvider(StorageContainerScreen.class, screen -> {
            Rect panel = SkyHanniCompat.chestValuePanel();
            return panel == null ? List.of() : List.of(new Rect2i(panel.x(), panel.y(), panel.width(), panel.height()));
        });
    }
}
