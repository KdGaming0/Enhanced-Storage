package com.github.kdgaming0.enhancedstorage.storage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which container ids have received their initial
 * {@code ClientboundContainerSetContentPacket}. Until that packet arrives the
 * client-side menu is all air, which must not be mistaken for a genuinely
 * empty storage page — capturing before then would overwrite real cached
 * contents with nothing.
 */
public final class ContainerContentTracker {

    private static final Set<Integer> received = ConcurrentHashMap.newKeySet();

    private ContainerContentTracker() {
    }

    public static void markReceived(int containerId) {
        received.add(containerId);
    }

    public static boolean hasReceived(int containerId) {
        return received.contains(containerId);
    }

    /**
     * The server reuses container ids, so forget everything whenever a new
     * screen opens or the connection changes.
     */
    public static void reset() {
        received.clear();
    }
}
