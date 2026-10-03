package com.github.kdgaming0.enhancedstorage.storage.profile;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.util.SkyblockState;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes sure the SkyBlock profile id reaches the {@link ProfileSession}.
 * Normally the id shows up in chat by itself; if it doesn't (single-profile
 * accounts), we send /profileid once and hide its output.
 */
public final class ProfileIdRequester {

    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("profile id:\\s*([0-9a-f-]{36})");
    private static final String SUGGEST_LINE = "click this to suggest it in chat";
    private static final int WAIT_TICKS = 200;
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private static final int SUPPRESS_TICKS_AFTER_SEND = 60;
    private static final int SUPPRESS_TICKS_AFTER_REPLY = 20;
    private static Phase phase = Phase.WAITING_IN_SKYBLOCK;
    private static int phaseTicks;
    private static int suppressProfileIdOutputTicks;
    private ProfileIdRequester() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ProfileIdRequester::tick);

        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> overlay || onGameMessage(message));
    }

    public static void reset() {
        phase = Phase.WAITING_IN_SKYBLOCK;
        phaseTicks = 0;
        suppressProfileIdOutputTicks = 0;
    }

    public static String debugPhase() {
        return phase.name();
    }

    private static boolean onGameMessage(Component message) {
        String text = TextUtils.stripText(message);

        // Hide the optional "click this to suggest it in chat" line produced by our request.
        if (suppressProfileIdOutputTicks > 0 && text.contains(SUGGEST_LINE)) {
            return false;
        }

        Matcher matcher = PROFILE_ID_PATTERN.matcher(text);
        if (!matcher.find()) return true;

        boolean wasOurRequest = phase == Phase.AWAITING_REPLY;
        if (wasOurRequest) {
            phase = Phase.DONE;
            suppressProfileIdOutputTicks = SUPPRESS_TICKS_AFTER_REPLY;
        }

        ProfileSession.getInstance().onProfileSeen(matcher.group(1));

        // Hides the profile ID if it was produced by our /profileid command.
        return !wasOurRequest;
    }

    private static void tick(Minecraft client) {
        if (suppressProfileIdOutputTicks > 0) {
            suppressProfileIdOutputTicks--;
        }

        switch (phase) {
            case WAITING_IN_SKYBLOCK -> tickWaiting(client);
            case AWAITING_REPLY -> tickAwaitingReply();
            case DONE -> {
            }
        }
    }

    private static void tickWaiting(Minecraft client) {
        if (ProfileSession.getInstance().isConfirmed()) {
            phase = Phase.DONE;   // the id arrived by itself, no request needed
            return;
        }

        if (client.player == null
                || client.getConnection() == null
                || !SkyblockState.isInSkyblock()) {
            phaseTicks = 0;
            return;
        }

        if (++phaseTicks >= WAIT_TICKS) {
            send(client);
        }
    }

    private static void tickAwaitingReply() {
        if (++phaseTicks >= REQUEST_TIMEOUT_TICKS) {
            EnhancedStorage.LOGGER.warn(
                    "No reply to /profileid after {}s; giving up until the next reset",
                    REQUEST_TIMEOUT_TICKS / 20);
            phase = Phase.DONE;
        }
    }

    private static void send(Minecraft client) {
        EnhancedStorage.LOGGER.info(
                "No SkyBlock profile id seen {}s after entering SkyBlock (single-profile account?); sending /profileid once",
                WAIT_TICKS / 20);

        phase = Phase.AWAITING_REPLY;
        phaseTicks = 0;
        suppressProfileIdOutputTicks = SUPPRESS_TICKS_AFTER_SEND;
        //noinspection DataFlowIssue
        client.getConnection().sendCommand("profileid");
    }

    private enum Phase {
        WAITING_IN_SKYBLOCK,
        AWAITING_REPLY,
        DONE
    }
}