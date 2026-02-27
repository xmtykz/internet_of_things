package com.ykz.iot.network.payload.device;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HomeRequestPayload() implements CustomPacketPayload {
    public static final Type<HomeRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "home_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HomeRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new HomeRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
