package com.github.kdgaming0.enhancedstorage.gui.layout;

/**
 * A {@link PageViewport} cell that can change from frame to frame on its own, so it must be drawn every frame
 * rather than from the viewport's cached page layer.
 */
public interface LiveCell {

    boolean drawsLive();
}
