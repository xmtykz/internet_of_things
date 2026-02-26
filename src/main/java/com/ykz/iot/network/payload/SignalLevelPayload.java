package com.ykz.iot.network.payload;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SignalLevelPayload(int level) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SignalLevelPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "signal_level")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalLevelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SignalLevelPayload::level,
                    SignalLevelPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}