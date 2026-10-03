package com.github.kdgaming0.enhancedstorage.storage.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks which container menus have received their contents from the server.
 * Until that happens the client-side menu is all air, which must not be
 * mistaken for a genuinely empty storage page: capturing then would overwrite
 * real cached contents with nothing.
 *
 * <p>State is kept per menu object, not per container id. The server reuses
 * ids, but every opened screen gets a brand new menu, so nothing can go stale.
 * The map is weak, so closed menus are forgotten automatically.</p>
 */
public final class ContainerContentTracker {

    private static final Map<AbstractContainerMenu, State> states =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ContainerContentTracker() {
    }

    /**
     * The full contents of a container arrived.
     */
    public static void markReceived(int containerId) {
        AbstractContainerMenu menu = menuWithId(containerId);
        if (menu == null) return;

        State state = states.computeIfAbsent(menu, _ -> new State());
        state.received = true;
        state.changed.set(true);
    }

    // ------------------------------------------------------------------
    // Called when packets arrive (by id, because that is all a packet knows)
    // ------------------------------------------------------------------

    /**
     * A single slot changed. Ignored until the full contents have arrived.
     */
    public static void markChanged(int containerId) {
        AbstractContainerMenu menu = menuWithId(containerId);
        if (menu == null) return;

        State state = states.get(menu);
        if (state != null && state.received) {
            state.changed.set(true);
        }
    }

    public static boolean hasReceived(AbstractContainerMenu menu) {
        State state = states.get(menu);
        return state != null && state.received;
    }

    // ------------------------------------------------------------------
    // Called by the capture code (by menu)
    // ------------------------------------------------------------------

    /**
     * True once per change: returns true, then false until the next change.
     */
    public static boolean consumeChanged(AbstractContainerMenu menu) {
        State state = states.get(menu);
        return state != null && state.changed.getAndSet(false);
    }

    /**
     * Forget everything. Called when joining or leaving a server.
     */
    public static void reset() {
        states.clear();
    }

    /**
     * The menu a container packet is talking about: the player's currently open menu,
     * if its id matches. Packets for any other id are stale and are ignored.
     */
    private static AbstractContainerMenu menuWithId(int containerId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        AbstractContainerMenu menu = player.containerMenu;
        return menu.containerId == containerId ? menu : null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static final class State {
        final AtomicBoolean changed = new AtomicBoolean();
        volatile boolean received;
    }
}