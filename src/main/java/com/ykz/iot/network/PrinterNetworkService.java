package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.blockentity.PrinterBlockEntity;
import com.ykz.iot.compat.exposure.ExposureCompat;
import com.ykz.iot.network.payload.printer.PrinterPrintRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = InternetofThings.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PrinterNetworkService {
    private static final int SEARCH_CHUNK_RADIUS = 64;
    private static final int MAX_PIXELS = 2048 * 2048;

    private PrinterNetworkService() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                PrinterPrintRequestPayload.TYPE,
                PrinterPrintRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        handlePrintRequest(player, payload);
                    }
                }));
    }

    private static void handlePrintRequest(ServerPlayer player, PrinterPrintRequestPayload payload) {
        if (!ExposureCompat.isCompatible()) {
            actionBar(player, "text.internet_of_things.printer.unavailable", ChatFormatting.RED);
            return;
        }

        long pixelCount = (long) payload.width() * payload.height();
        if (payload.width() <= 0 || payload.height() <= 0
                || pixelCount <= 0 || pixelCount > MAX_PIXELS
                || payload.pixels() == null
                || payload.pixels().length != pixelCount) {
            actionBar(player, "text.internet_of_things.printer.invalid_photo", ChatFormatting.RED);
            return;
        }

        @Nullable PrinterBlockEntity printer = findNearestOnlinePrinter(player.serverLevel(), player.blockPosition());
        if (printer == null) {
            actionBar(player, "text.internet_of_things.printer.not_found", ChatFormatting.RED);
            return;
        }

        PrinterBlockEntity.StartCheck status = printer.beginRemotePrint(
                player, payload.width(), payload.height(), payload.pixels(), payload.paletteId(), payload.unixTimestamp());

        switch (status) {
            case OK -> actionBar(player, "text.internet_of_things.printer.queued", ChatFormatting.GREEN);
            case OFFLINE -> actionBar(player, "text.internet_of_things.printer.offline", ChatFormatting.RED);
            case BUSY -> actionBar(player, "text.internet_of_things.printer.busy", ChatFormatting.RED);
            case OUTPUT_OCCUPIED -> actionBar(player, "text.internet_of_things.printer.output_occupied", ChatFormatting.RED);
            case INSUFFICIENT_DYES -> actionBar(player, "text.internet_of_things.printer.insufficient_dyes", ChatFormatting.RED);
            case INVALID_IMAGE -> actionBar(player, "text.internet_of_things.printer.invalid_photo", ChatFormatting.RED);
        }
    }

    private static @Nullable PrinterBlockEntity findNearestOnlinePrinter(ServerLevel level, BlockPos origin) {
        ChunkPos originChunk = new ChunkPos(origin);
        double bestDistance = Double.MAX_VALUE;
        PrinterBlockEntity best = null;

        for (int dx = -SEARCH_CHUNK_RADIUS; dx <= SEARCH_CHUNK_RADIUS; dx++) {
            for (int dz = -SEARCH_CHUNK_RADIUS; dz <= SEARCH_CHUNK_RADIUS; dz++) {
                int chunkX = originChunk.x + dx;
                int chunkZ = originChunk.z + dz;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof PrinterBlockEntity printer)) {
                        continue;
                    }
                    if (!PrinterBlockEntity.isNetworkOnline(level, printer.getBlockPos())) {
                        continue;
                    }

                    double distance = printer.getBlockPos().distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = printer;
                    }
                }
            }
        }
        return best;
    }

    private static void actionBar(ServerPlayer player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }
}
