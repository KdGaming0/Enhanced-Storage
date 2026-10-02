package com.github.kdgaming0.enhancedstorage.command;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.storage.ProfileIdRequester;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.PageOrder;
import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class EnhancedStorageCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(commandRoot("enhancedstorage"));
            dispatcher.register(commandRoot("es"));
            dispatcher.register(searchCommand());
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> commandRoot(String name) {
        return literal(name)
                .then(literal("ecs")
                        .executes(EnhancedStorageCommand::executeStorageOpen)
                        .then(argument("search_query", StringArgumentType.greedyString())
                                .executes(EnhancedStorageCommand::executeStorageSearch)))
                .then(literal("config")
                        .executes(EnhancedStorageCommand::executeOpenConfig))
                .then(literal("edit")
                        .executes(ctx -> executeEdit(false))
                        .then(literal("rift")
                                .executes(ctx -> executeEdit(true))))
                .then(literal("test_storage")
                        .executes(EnhancedStorageCommand::executeTestStorage))
                .then(literal("debug")
                        .executes(EnhancedStorageCommand::executeDebug));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> searchCommand() {
        return literal("ecs")
                .executes(EnhancedStorageCommand::executeStorageOpen)
                .then(argument("search_query", StringArgumentType.greedyString())
                        .executes(EnhancedStorageCommand::executeStorageSearch));
    }

    private static int executeStorageSearch(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String searchQuery = StringArgumentType.getString(ctx, "search_query");
        Minecraft client = Minecraft.getInstance();

        client.schedule(() -> {
            try {
                StorageOverlayState.session().setSearchQuery(searchQuery);
                assert client.player != null;
                client.player.connection.sendCommand("storage");
            } catch (Exception e) {
                EnhancedStorage.LOGGER.error("Failed to open storage overlay", e);
            }
        });

        return 1;
    }

    private static int executeStorageOpen(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        client.schedule(() -> {
            try {
                assert client.player != null;
                client.player.connection.sendCommand("storage");
            } catch (Exception e) {
                EnhancedStorage.LOGGER.error("Failed to open storage overlay", e);
            }
        });

        return 1;
    }

    private static int executeOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        client.schedule(() -> {
            try {
                //? if >=26.2 {
                /*client.gui.setScreen(MidnightConfig.getScreen(client.gui.screen(), EnhancedStorage.MOD_ID));
                *///?} else {
                client.setScreen(MidnightConfig.getScreen(client.screen, EnhancedStorage.MOD_ID));
                //?}
            } catch (Exception e) {
                EnhancedStorage.LOGGER.error("Failed to open config menu", e);
            }
        });

        ctx.getSource().sendFeedback(Component.literal("§a[EnhancedStorage] Opening configuration menu..."));
        return 1;
    }

    private static int executeTestStorage(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(new StorageOverlay()));

        ctx.getSource().sendFeedback(Component.literal("§a[EnhancedStorage] Opening test storage overlay..."));
        return 1;
    }


    private static int executeEdit(boolean rift) {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(StorageOverlay.forEditMode(rift, false)));
        return 1;
    }

    private static int executeDebug(CommandContext<FabricClientCommandSource> ctx) {
        ProfileSession session = ProfileSession.getInstance();

        List<String> lines = new ArrayList<>();
        lines.add("§e[EnhancedStorage] Debug §8(click to copy)");
        lines.add("§7Profile: §f" + session.current().orElse("none") + " §7(" + session.state() + ")");
        lines.add("§7Profile id request: §f" + ProfileIdRequester.debugPhase()
                + "§7, staged pages: §f" + session.stagedCount());

        for (ProfileScopedStore store : session.stores()) {
            lines.add("§7" + store.fileName()
                    + ": loaded=§f" + store.isLoaded()
                    + "§7, dirty=§f" + store.isDirty());
        }

        lines.add("§7Cached pages: §f" + PageCache.getInstance().all().size()
                + "§7, known pages: §f" + PageCache.getInstance().allKnown().size());
        lines.add("§7Custom names: §f" + PageNames.getInstance().all().size()
                + "§7, custom positions: §f" + PageOrder.getInstance().all().size());

        String report = lines.stream()
                .map(ChatFormatting::stripFormatting)
                .collect(Collectors.joining("\n"));

        Style clickToCopy = Style.EMPTY
                .withClickEvent(new ClickEvent.CopyToClipboard(report))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy the full report")));

        for (String line : lines) {
            ctx.getSource().sendFeedback(Component.literal(line).withStyle(clickToCopy));
        }
        return 1;
    }

}
