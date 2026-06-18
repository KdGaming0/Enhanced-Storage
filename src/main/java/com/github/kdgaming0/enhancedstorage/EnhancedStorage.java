package com.github.kdgaming0.enhancedstorage;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.integration.SkyblockEnhancementsIntegration;
import com.github.kdgaming0.enhancedstorage.storage.StorageLifecycle;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancedStorage implements ClientModInitializer {
    public static final String MOD_ID = "enhanced_storage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        MidnightConfig.init(MOD_ID, EnhancedStorageConfig.class);
        StorageLifecycle.init();
        if (SkyblockEnhancementsIntegration.isPresent()) {
            LOGGER.info("SkyBlock-Enhancements detected; deferring cursor-position saving to it while its feature is enabled.");
        }
        LOGGER.info("Enhanced Storage initialized.");
    }
}
