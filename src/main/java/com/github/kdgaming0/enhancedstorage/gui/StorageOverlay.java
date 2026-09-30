package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class StorageOverlay extends AbstractScreen {

    private final StorageOverlayState state = StorageOverlayState.session();
    private final StorageOverlayLayout layout = new StorageOverlayLayout();
    private Deque<ItemStack> searchWarmupQueue;

    public StorageOverlay() {
        super(Component.literal("Storage Overlay"));
    }

    @Override
    protected void init() {
        layout.build(this, this.font, this.width, this.height,
                state, null, 0, this::onPageCardClicked, this::onSearchChanged, this::runToolkitCommand);

        // Queue every cached stack for search-text warmup; init() also runs on every rebuildWidgets, so only build the queue once per screen.
        if (searchWarmupQueue == null) {
            searchWarmupQueue = new ArrayDeque<>();
            PageCache.getInstance().all().values()
                    .forEach(page -> searchWarmupQueue.addAll(page.items()));
        }

        state.onStorageScreenOpened();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchWarmupQueue != null && !searchWarmupQueue.isEmpty()) {
            ItemSearch.warmUp(searchWarmupQueue, 3);
        }
    }

    // Called by PageCardComponent when a card is clicked.
    private void onPageCardClicked(StorageKey key) {

        if (Objects.equals(key, state.getOpenKey())) {
            return;
        }

        EnhancedStorage.LOGGER.info("Storage page card clicked: {} (type={}, cached={})",
                key.displayName(), key.type(),
                PageCache.getInstance().get(key).isPresent());

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

    private void runToolkitCommand(String command) {
        state.beginNavigation();
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    @Override
    public void removed() {
        super.removed();
        state.onStorageScreenClosed();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && Rect.contains(layout.getThemeButtonBounds(), event.x(), event.y())) {
            OverlayTheme.cycle();
            this.rebuildWidgets();
            return true;
        }

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