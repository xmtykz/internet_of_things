package com.ykz.iot.network.payload.device;

import com.ykz.iot.InternetofThings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeviceActionPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<DeviceActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "device_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceActionPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, DeviceActionPayload::tag, DeviceActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
