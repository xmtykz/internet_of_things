package com.ykz.iot.device;

import com.ykz.iot.InternetofThings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = InternetofThings.MODID)
public final class SmartDeviceTicker {
    private static int tickCounter = 0;

    private SmartDeviceTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 60 != 0) {
            return;
        }

        var server = event.getServer();
        for (var level : server.getAllLevels()) {
            SmartDeviceSavedData.get(level).tick(level, SmartDeviceSavedData.currentScheduleTime(level));
        }
    }
}
