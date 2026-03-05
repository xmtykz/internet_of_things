package com.ykz.iot.network;

import com.ykz.iot.network.payload.trade.TradeActionPayload;
import com.ykz.iot.network.payload.trade.TradeDataPayload;
import com.ykz.iot.network.payload.trade.TradeRequestPayload;
import com.ykz.iot.network.payload.trade.TradeStatusPayload;
import com.ykz.iot.trade.PlayerTradeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TradeNetworkService {
    private TradeNetworkService() {
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                TradeRequestPayload.TYPE,
                TradeRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        sendDataIfOnline(player);
                    }
                })
        );

        registrar.playToServer(
                TradeActionPayload.TYPE,
                TradeActionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        handleAction(player, payload.tag());
                    }
                })
        );

        registrar.playToClient(
                TradeDataPayload.TYPE,
                TradeDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.client.trade.ClientTradeState");
                        cls.getMethod("applyTradeData", CompoundTag.class).invoke(null, payload.tag());
                    } catch (Throwable ignored) {
                    }
                })
        );

        registrar.playToClient(
                TradeStatusPayload.TYPE,
                TradeStatusPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.client.trade.TradeClientBridge");
                        cls.getMethod("handleStatusMessage", String.class, String.class)
                                .invoke(null, payload.key(), payload.style());
                    } catch (Throwable ignored) {
                    }
                })
        );
    }

    private static void sendDataIfOnline(ServerPlayer player) {
        if (!isOnline(player)) {
            sendStatus(player, "text.internet_of_things.trade.offline", ChatFormatting.RED);
            return;
        }
        sendData(player, true);
    }

    private static void handleAction(ServerPlayer player, CompoundTag tag) {
        if (!isOnline(player)) {
            sendStatus(player, "text.internet_of_things.trade.offline", ChatFormatting.RED);
            return;
        }

        String action = tag.getString("action");
        PlayerTradeSavedData data = PlayerTradeSavedData.get(player.server);

        switch (action) {
            case "trade" -> {
                String tradeId = tag.getString("id");
                boolean batch = tag.getBoolean("batch");
                PlayerTradeSavedData.TradeActionStatus status = data.executeTrade(player, tradeId, batch);
                switch (status) {
                    case SUCCESS_ONE -> sendStatus(player, "text.internet_of_things.trade.success", ChatFormatting.GREEN);
                    case SUCCESS_BATCH -> sendStatus(player, "text.internet_of_things.trade.success_batch", ChatFormatting.GREEN);
                    case OUT_OF_STOCK -> sendStatus(player, "text.internet_of_things.trade.out_of_stock", ChatFormatting.RED);
                    case INSUFFICIENT_INPUT -> sendStatus(player, "text.internet_of_things.trade.no_input", ChatFormatting.RED);
                    case OUTPUT_FULL -> sendStatus(player, "text.internet_of_things.trade.output_full", ChatFormatting.RED);
                    case NOT_FOUND, FAILED -> sendStatus(player, "text.internet_of_things.trade.failed", ChatFormatting.RED);
                }
            }
            case "delete" -> {
                String tradeId = tag.getString("id");
                boolean removed = data.deleteTrade(player, tradeId);
                if (removed) {
                    sendStatus(player, "text.internet_of_things.trade.deleted", ChatFormatting.GRAY);
                } else {
                    sendStatus(player, "text.internet_of_things.trade.failed", ChatFormatting.RED);
                }
            }
            default -> {
            }
        }

        sendData(player, false);
    }

    private static void sendData(ServerPlayer player, boolean applyRestock) {
        CompoundTag data = PlayerTradeSavedData.get(player.server).buildSyncTag(player, applyRestock);
        PacketDistributor.sendToPlayer(player, new TradeDataPayload(data));
    }

    private static void sendStatus(ServerPlayer player, String key, ChatFormatting style) {
        PacketDistributor.sendToPlayer(player, new TradeStatusPayload(key, style.getName()));
    }

    private static boolean isOnline(ServerPlayer player) {
        return IotNetworkSavedData.get(player.serverLevel()).getNetworkLevelAt(player.chunkPosition()) >= 1;
    }
}
