package com.ykz.iot.network.payload.trade;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeStatusPayload(String key, String style) implements CustomPacketPayload {
    public static final Type<TradeStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "trade_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, TradeStatusPayload::key,
                    ByteBufCodecs.STRING_UTF8, TradeStatusPayload::style,
                    TradeStatusPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
