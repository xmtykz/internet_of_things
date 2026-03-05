package com.ykz.iot.network.payload.trade;

import com.ykz.iot.InternetofThings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeActionPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<TradeActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "trade_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, TradeActionPayload::tag, TradeActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
