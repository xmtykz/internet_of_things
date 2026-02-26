package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.network.payload.SignalLevelPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = InternetofThings.MODID)
public final class IotNetworkPayloads {
    private IotNetworkPayloads() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SignalLevelPayload.TYPE,
                SignalLevelPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    // 反射调用：避免 Dedicated Server 侧 classloading 客户端类
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.client.ClientSignalCache");
                        cls.getMethod("setSignalLevel", int.class).invoke(null, payload.level());
                    } catch (Throwable ignored) {
                    }
                })
        );
    }
}