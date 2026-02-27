package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.device.DoorPosHelper;
import com.ykz.iot.device.IotTags;
import com.ykz.iot.device.SmartDeviceSavedData;
import com.ykz.iot.network.payload.device.DeviceActionPayload;
import com.ykz.iot.network.payload.device.DeviceDetailPayload;
import com.ykz.iot.network.payload.device.HomeDataPayload;
import com.ykz.iot.network.payload.device.HomeRequestPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@EventBusSubscriber(modid = InternetofThings.MODID)
public final class DeviceNetworkService {
    private DeviceNetworkService() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(HomeRequestPayload.TYPE, HomeRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        sendHomeData(player);
                    }
                }));

        registrar.playToServer(DeviceActionPayload.TYPE, DeviceActionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    handleAction(player, payload.tag());
                }));

        registrar.playToClient(HomeDataPayload.TYPE, HomeDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.client.home.ClientHomeState");
                        cls.getMethod("applyHomeData", CompoundTag.class).invoke(null, payload.tag());
                    } catch (Throwable ignored) {
                    }
                }));

        registrar.playToClient(DeviceDetailPayload.TYPE, DeviceDetailPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        Class<?> cls = Class.forName("com.ykz.iot.client.home.ClientHomeState");
                        cls.getMethod("openOrUpdateDetail", CompoundTag.class).invoke(null, payload.tag());
                    } catch (Throwable ignored) {
                    }
                }));
    }

    public static void sendHomeData(ServerPlayer player) {
        SmartDeviceSavedData data = SmartDeviceSavedData.get(player.serverLevel());
        ListTag list = new ListTag();
        for (SmartDeviceSavedData.DeviceSnapshot snapshot : data.buildSnapshots(player.serverLevel())) {
            list.add(snapshot.toTag());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("devices", list);
        PacketDistributor.sendToPlayer(player, new HomeDataPayload(tag));
    }

    public static void sendDeviceDetail(ServerPlayer player, BlockPos pos, boolean allowUninstalled) {
        pos = DoorPosHelper.normalizeDoorBasePos(player.serverLevel(), pos);
        SmartDeviceSavedData data = SmartDeviceSavedData.get(player.serverLevel());
        CompoundTag out = new CompoundTag();
        out.putLong("targetPos", pos.asLong());
        out.putBoolean("networkable", player.serverLevel().getBlockState(pos).is(IotTags.NETWORKABLE_DOORS));
        data.buildSnapshotByPos(player.serverLevel(), pos, allowUninstalled).ifPresent(snapshot -> out.put("device", snapshot.toTag()));
        PacketDistributor.sendToPlayer(player, new DeviceDetailPayload(out));
    }

    private static void handleAction(ServerPlayer player, CompoundTag tag) {
        String action = tag.getString("action");
        SmartDeviceSavedData data = SmartDeviceSavedData.get(player.serverLevel());

        switch (action) {
            case "request_detail_by_id" -> {
                if (!tag.hasUUID("id")) break;
                UUID id = tag.getUUID("id");
                data.buildSnapshotById(player.serverLevel(), id).ifPresent(snapshot -> {
                    CompoundTag out = new CompoundTag();
                    out.put("device", snapshot.toTag());
                    out.putLong("targetPos", snapshot.pos);
                    out.putBoolean("networkable", true);
                    PacketDistributor.sendToPlayer(player, new DeviceDetailPayload(out));
                });
            }
            case "install_by_pos" -> {
                BlockPos pos = DoorPosHelper.normalizeDoorBasePos(player.serverLevel(), BlockPos.of(tag.getLong("pos")));
                if (!player.serverLevel().getBlockState(pos).is(IotTags.NETWORKABLE_DOORS)) break;
                if (!consumeModule(player)) break;
                Block block = player.serverLevel().getBlockState(pos).getBlock();
                data.installAt(player.serverLevel(), pos, block, player.getScoreboardName());
                data.setDirty();
                sendDeviceDetail(player, pos, true);
                sendHomeData(player);
            }
            case "rename" -> {
                if (!tag.hasUUID("id")) break;
                data.renameDevice(tag.getUUID("id"), tag.getString("name"));
                sendHomeData(player);
                sendDetailById(player, tag.getUUID("id"));
            }
            case "set_schedule" -> {
                if (!tag.hasUUID("id")) break;
                UUID id = tag.getUUID("id");
                int openTick = tag.getInt("openTick");
                int closeTick = tag.getInt("closeTick");
                boolean repeat = tag.getBoolean("repeat");
                boolean enabled = tag.getBoolean("enabled");
                SmartDeviceSavedData.TimeUnitMode unit = SmartDeviceSavedData.TimeUnitMode.valueOf(tag.getString("unit"));
                data.setSchedule(id, openTick, closeTick, repeat, enabled, SmartDeviceSavedData.currentScheduleTime(player.serverLevel()), unit);
                sendHomeData(player);
                sendDetailById(player, id);
            }
            case "clear_schedule" -> {
                if (!tag.hasUUID("id")) break;
                UUID id = tag.getUUID("id");
                data.clearSchedule(id);
                sendHomeData(player);
                sendDetailById(player, id);
            }
            case "toggle" -> {
                if (!tag.hasUUID("id")) break;
                UUID id = tag.getUUID("id");
                data.toggleDevice(player.serverLevel(), id);
                sendHomeData(player);
                sendDetailById(player, id);
            }
            case "remove" -> {
                if (!tag.hasUUID("id")) break;
                data.removeDevice(tag.getUUID("id"));
                sendHomeData(player);
            }
            default -> {
            }
        }
    }

    private static void sendDetailById(ServerPlayer player, UUID id) {
        SmartDeviceSavedData data = SmartDeviceSavedData.get(player.serverLevel());
        data.buildSnapshotById(player.serverLevel(), id).ifPresent(snapshot -> {
            CompoundTag out = new CompoundTag();
            out.put("device", snapshot.toTag());
            out.putLong("targetPos", snapshot.pos);
            out.putBoolean("networkable", true);
            PacketDistributor.sendToPlayer(player, new DeviceDetailPayload(out));
        });
    }

    private static boolean consumeModule(ServerPlayer player) {
        var item = com.ykz.iot.item.ModItems.NETWORK_MODULE.get();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
