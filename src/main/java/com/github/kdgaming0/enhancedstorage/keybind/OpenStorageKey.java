package com.github.kdgaming0.enhancedstorage.keybind;

import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.util.SkyblockState;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;

/**
 * A key that opens storage, like typing /storage. Unbound until the player picks a key in Controls.
 */
public final class OpenStorageKey {

    private OpenStorageKey() {
    }

    public static void register() {
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.enhanced_storage.open_storage",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CopyHighlight.CATEGORY));

        // Key presses only count while no screen is open, so this never fires while typing in chat or a menu.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                LocalPlayer player = client.player;
                // /storage is a SkyBlock command; elsewhere it would only print an error.
                if (player != null && SkyblockState.isInSkyblock()) player.connection.sendCommand("storage");
            }
        });
    }
}
