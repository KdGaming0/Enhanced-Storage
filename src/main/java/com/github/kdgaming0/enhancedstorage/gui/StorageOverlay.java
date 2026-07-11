package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.StorageCache;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public class StorageOverlay extends AbstractScreen {

    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout = new StorageOverlayLayout();

    public StorageOverlay() {
        super(Component.literal("Storage Overlay"));
    }

    @Override
    protected void init() {
        layout.build(this, this.font, this.width, this.height,
                state, null, 0, this::onPageCardClicked, this::onSearchChanged);
        state.onStorageScreenOpened();
    }

    // Called by PageCardComponent when a card is clicked.
    private void onPageCardClicked(StorageKey key) {

        if (Objects.equals(key, state.getOpenKey())) {
            return;
        }

        EnhancedStorage.LOGGER.info("Storage page card clicked: {} (type={}, cached={})",
                key.displayName(), key.type(),
                StorageCache.getInstance().get(key).isPresent());

        state.setOpenKey(key);
    }

    private void onSearchChanged() {
        Minecraft.getInstance().schedule(() -> {
            this.rebuildWidgets();

            var box = layout.getSearchBox();
            if (box != null) {
                this.setFocused(box);
                box.setFocused(true);
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
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
                && mouseX >= overview.getX() && mouseX < overview.getX() + overview.getWidth()
                && mouseY >= overview.getY() && mouseY < overview.getY() + overview.getHeight()
                && overview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}