package com.ykz.iot.trade;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.network.IotNetworkSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

@EventBusSubscriber(modid = InternetofThings.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TradeRecorder {
    private TradeRecorder() {
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int networkLevel = IotNetworkSavedData.get(player.serverLevel()).getNetworkLevelAt(player.chunkPosition());
        if (networkLevel < 1) {
            return;
        }

        PlayerTradeSavedData.get(player.server)
                .recordOnlineVillagerTrade(player, event.getAbstractVillager(), event.getMerchantOffer());
    }
}
