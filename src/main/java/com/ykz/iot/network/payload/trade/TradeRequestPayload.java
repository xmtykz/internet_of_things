package com.ykz.iot.network.payload.trade;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeRequestPayload() implements CustomPacketPayload {
    public static final Type<TradeRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "trade_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new TradeRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
