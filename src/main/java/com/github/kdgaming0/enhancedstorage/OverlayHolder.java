package com.github.kdgaming0.enhancedstorage;

/**
 * Exposes whether a container screen currently has an active StorageOverlay.
 * Implemented via mixin on {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}.
 */
public interface OverlayHolder {
    boolean es$hasOverlay();
}
