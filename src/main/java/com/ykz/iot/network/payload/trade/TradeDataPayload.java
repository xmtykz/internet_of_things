package com.ykz.iot.network.payload.trade;

import com.ykz.iot.InternetofThings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeDataPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<TradeDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "trade_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeDataPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, TradeDataPayload::tag, TradeDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
