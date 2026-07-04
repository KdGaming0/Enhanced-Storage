package com.github.kdgaming0.enhancedstorage;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancedStorage implements ClientModInitializer {
    public static final String MOD_ID = "enhanced_storage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Enhanced Storage initialized.");
    }
}
