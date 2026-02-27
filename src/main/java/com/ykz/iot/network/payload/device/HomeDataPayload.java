package com.ykz.iot.network.payload.device;

import com.ykz.iot.InternetofThings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HomeDataPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<HomeDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "home_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HomeDataPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, HomeDataPayload::tag, HomeDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
