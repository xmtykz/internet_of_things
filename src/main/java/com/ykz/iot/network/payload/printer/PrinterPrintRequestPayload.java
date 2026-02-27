package com.ykz.iot.network.payload.printer;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PrinterPrintRequestPayload(int width,
                                         int height,
                                         byte[] pixels,
                                         String paletteId,
                                         long unixTimestamp) implements CustomPacketPayload {
    public static final Type<PrinterPrintRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "printer_print_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrinterPrintRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PrinterPrintRequestPayload::width,
                    ByteBufCodecs.VAR_INT, PrinterPrintRequestPayload::height,
                    ByteBufCodecs.byteArray(2048 * 2048), PrinterPrintRequestPayload::pixels,
                    ByteBufCodecs.STRING_UTF8, PrinterPrintRequestPayload::paletteId,
                    ByteBufCodecs.VAR_LONG, PrinterPrintRequestPayload::unixTimestamp,
                    PrinterPrintRequestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

