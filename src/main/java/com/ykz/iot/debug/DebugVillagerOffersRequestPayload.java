package com.ykz.iot.debug;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DebugVillagerOffersRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<DebugVillagerOffersRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "debug_villager_offers_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugVillagerOffersRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, DebugVillagerOffersRequestPayload::entityId,
                    DebugVillagerOffersRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
