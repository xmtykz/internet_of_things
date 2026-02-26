package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.network.payload.SignalLevelPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = InternetofThings.MODID)
public final class SignalLevelSyncTicker {
    private SignalLevelSyncTicker() {}

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 != 0) return; // 1秒一次

        var server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int level = IotNetworkSavedData.get(player.serverLevel()).getNetworkLevelAt(player.chunkPosition());
            PacketDistributor.sendToPlayer(player, new SignalLevelPayload(level));
        }
    }
}