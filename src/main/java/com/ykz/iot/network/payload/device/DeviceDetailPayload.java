package com.ykz.iot.network.payload.device;

import com.ykz.iot.InternetofThings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeviceDetailPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<DeviceDetailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "device_detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceDetailPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, DeviceDetailPayload::tag, DeviceDetailPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
