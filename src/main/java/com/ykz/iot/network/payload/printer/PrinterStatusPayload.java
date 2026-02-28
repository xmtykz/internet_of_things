package com.ykz.iot.network.payload.printer;

import com.ykz.iot.InternetofThings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PrinterStatusPayload(String key, String style) implements CustomPacketPayload {
    public static final Type<PrinterStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "printer_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrinterStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PrinterStatusPayload::key,
                    ByteBufCodecs.STRING_UTF8, PrinterStatusPayload::style,
                    PrinterStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
