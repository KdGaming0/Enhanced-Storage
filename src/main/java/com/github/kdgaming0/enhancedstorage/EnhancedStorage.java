package com.github.kdgaming0.enhancedstorage;

import com.github.kdgaming0.enhancedstorage.command.EnhancedStorageCommand;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.config.LayoutSpacing;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.highlight.ItemTotals;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.github.kdgaming0.enhancedstorage.storage.StorageEvents;
import com.github.kdgaming0.enhancedstorage.storage.capture.StorageCaptureHandler;
import com.github.kdgaming0.enhancedstorage.storage.data.*;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileIdRequester;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.github.kdgaming0.enhancedstorage.storage.profile.StorageLifecycle;
import com.github.kdgaming0.enhancedstorage.util.SkyblockState;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancedStorage implements ClientModInitializer {
    public static final String MOD_ID = "enhanced_storage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {

        MidnightConfig.init(MOD_ID, EnhancedStorageConfig.class);
        LayoutSpacing.register();

        ProfileSession session = ProfileSession.getInstance();
        session.register(PageCache.getInstance(), PageNames.getInstance(), PageOrder.getInstance(),
                PageLayout.getInstance(), PageWidths.getInstance());

        StorageEvents.PROFILE_CHANGED.subscribe(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.screen instanceof StorageContainerScreen screen) {
                    screen.rebuildForProfileChange();
                }
            });
        });

        StorageEvents.INDEX_CHANGED.subscribe(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.screen instanceof StorageContainerScreen screen) {
                    screen.refreshCards();
                }
            });
        });

        SkyblockState.register();
        ProfileIdRequester.register();
        StorageLifecycle.register();
        StorageCaptureHandler.register();
        EnhancedStorageCommand.register();
        CopyHighlight.register();
        ItemTotals.register();

        LOGGER.info("Enhanced Storage initialized.");
    }
}
