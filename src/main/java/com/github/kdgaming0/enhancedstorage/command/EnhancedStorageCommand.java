package com.github.kdgaming0.enhancedstorage.command;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
                .then(literal("test_storage")
                        .executes(EnhancedStorageCommand::executeTestStorage));
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

}
