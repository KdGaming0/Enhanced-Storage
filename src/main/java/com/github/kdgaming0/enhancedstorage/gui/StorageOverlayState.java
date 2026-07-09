package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import org.jspecify.annotations.Nullable;

public class StorageOverlayState {

    private @Nullable StorageKey openKey;

    public @Nullable StorageKey getOpenKey() {
        return openKey;
    }

    public void setOpenKey(@Nullable StorageKey openKey) {
        this.openKey = openKey;
    }

    public boolean isOpen(StorageKey key) {
        return key.equals(openKey);
    }
}
