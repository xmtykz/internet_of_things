package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.blockentity.ScannerBlockEntity;
import com.ykz.iot.compat.exposure.ExposureCompat;
import com.ykz.iot.network.payload.scanner.ScannerExportDataPayload;
import com.ykz.iot.network.payload.scanner.ScannerStartRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = InternetofThings.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ScannerNetworkService {
    private ScannerNetworkService() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                ScannerStartRequestPayload.TYPE,
                ScannerStartRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        handleStartRequest(player, payload.scannerPos());
                    }
                })
        );

        registrar.playToClient(
                ScannerExportDataPayload.TYPE,
                ScannerExportDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.compat.exposure.ExposureScannerClientBridge");
                        cls.getMethod("exportToPhoneAlbum", ScannerExportDataPayload.class).invoke(null, payload);
                    } catch (Throwable ignored) {
                    }
                })
        );
    }

    private static void handleStartRequest(ServerPlayer player, BlockPos pos) {
        if (!ExposureCompat.isCompatible()) {
            actionBar(player, "text.internet_of_things.scanner.unavailable", ChatFormatting.RED);
            return;
        }

        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof ScannerBlockEntity scanner)) {
            actionBar(player, "text.internet_of_things.scanner.not_found", ChatFormatting.RED);
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            actionBar(player, "text.internet_of_things.scanner.too_far", ChatFormatting.RED);
            return;
        }

        ScannerBlockEntity.StartCheck status = scanner.startScan(player);
        switch (status) {
            case OK -> actionBar(player, "text.internet_of_things.scanner.queued", ChatFormatting.GREEN);
            case OFFLINE -> actionBar(player, "text.internet_of_things.scanner.offline", ChatFormatting.RED);
            case BUSY -> actionBar(player, "text.internet_of_things.scanner.busy", ChatFormatting.RED);
            case NO_SOURCE -> actionBar(player, "text.internet_of_things.scanner.no_source", ChatFormatting.RED);
            case INVALID_FRAME -> actionBar(player, "text.internet_of_things.scanner.invalid_frame", ChatFormatting.RED);
        }
    }

    private static void actionBar(ServerPlayer player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }
}
