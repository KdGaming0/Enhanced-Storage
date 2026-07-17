package com.github.kdgaming0.enhancedstorage;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import com.github.kdgaming0.enhancedstorage.storage.*;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancedStorage implements ClientModInitializer {
    public static final String MOD_ID = "enhanced_storage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {

        MidnightConfig.init(MOD_ID, EnhancedStorageConfig.class);

        StorageProfile.getInstance().setOnChange(() -> {
            StorageCache.getInstance().reloadForCurrentProfile();
            StorageNames.getInstance().reloadForCurrentProfile();
            StorageOrder.getInstance().reloadForCurrentProfile();

            // If a storage overlay is currently open, rebuild it against the new data.
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.screen instanceof StorageContainerScreen storageScreen) {
                    storageScreen.rebuildForProfileChange();
                }
            });

            LOGGER.info("Storage profile changed; caches reloaded.");
        });

        StorageCaptureHandler.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("teststorage")
                    .executes(context -> {
                        Minecraft.getInstance().schedule(() -> {
                            Minecraft.getInstance().setScreen(new StorageOverlay());
                        });
                        return 1;
                    }));
        });

        LOGGER.info("Enhanced Storage initialized.");
    }
}
