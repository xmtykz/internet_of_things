package com.ykz.iot.network.payload.scanner;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScannerExportDataPayload(int width,
                                       int height,
                                       byte[] pixels,
                                       String paletteId,
                                       long unixTimestamp) implements CustomPacketPayload {
    public static final Type<ScannerExportDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "scanner_export_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScannerExportDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ScannerExportDataPayload::width,
                    ByteBufCodecs.VAR_INT, ScannerExportDataPayload::height,
                    ByteBufCodecs.byteArray(2048 * 2048), ScannerExportDataPayload::pixels,
                    ByteBufCodecs.STRING_UTF8, ScannerExportDataPayload::paletteId,
                    ByteBufCodecs.VAR_LONG, ScannerExportDataPayload::unixTimestamp,
                    ScannerExportDataPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
