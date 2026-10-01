package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Browse-only overlay showing cached pages without an open server menu.
 * Used for testing outside Hypixel and as the host for edit mode.
 */
public class StorageOverlay extends AbstractScreen {

    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout = new StorageOverlayLayout();
    private final OverlayActions actions =
            OverlayActions.create(this, state, this::onPageCardClicked, this::rebuildWidgets);

    public StorageOverlay() {
        super(Component.literal("Storage Overlay"));
    }

    @Override
    protected void init() {
        layout.build(this, this.font, this.width, this.height, state, null, 0, actions);
        state.onStorageScreenOpened();
    }

    @Override
    public void tick() {
        super.tick();
        layout.tick();
    }

    private void onPageCardClicked(StorageKey key) {
        if (Objects.equals(key, state.getOpenKey())) return;

        EnhancedStorage.LOGGER.info("Storage page card clicked: {} (type={}, cached={})",
                key.displayName(), key.type(), PageCache.getInstance().get(key).isPresent());
        state.setOpenKey(key);
    }

    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (layout.handleButtonClick(event)) return true;

        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null && overview.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollContainerWidget overview = layout.getPageOverview();
        if (overview != null
                && Rect.contains(layout.getPageOverviewBounds(), mouseX, mouseY)
                && overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}