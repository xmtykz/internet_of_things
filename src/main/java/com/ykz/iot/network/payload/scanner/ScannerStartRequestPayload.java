package com.ykz.iot.network.payload.scanner;

import com.ykz.iot.InternetofThings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScannerStartRequestPayload(BlockPos scannerPos) implements CustomPacketPayload {
    public static final Type<ScannerStartRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "scanner_start_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScannerStartRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ScannerStartRequestPayload::scannerPos,
                    ScannerStartRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
