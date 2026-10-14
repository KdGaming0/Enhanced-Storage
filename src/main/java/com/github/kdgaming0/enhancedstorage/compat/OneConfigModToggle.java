package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import org.polyfrost.oneconfig.api.toggle.v1.ModToggle;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

public final class OneConfigModToggle implements ModToggle {

    @Override
    public boolean isEnabled() {
        return EnhancedStorageConfig.enableOverlay || EnhancedStorageConfig.enableRiftOverlay;
    }

    @Override
    public void setEnabled(boolean enabled) {
        EnhancedStorageConfig.enableOverlay = enabled;
        EnhancedStorageConfig.enableRiftOverlay = enabled;
        EnhancedStorageConfig.write(MOD_ID);
    }
}
