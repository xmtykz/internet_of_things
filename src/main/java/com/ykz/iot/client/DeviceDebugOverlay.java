package com.ykz.iot.client;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.client.home.ClientHomeState;
import com.ykz.iot.device.DoorPosHelper;
import com.ykz.iot.device.IotTags;
import com.ykz.iot.device.SmartDeviceSavedData;
import com.ykz.iot.network.payload.device.HomeRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = InternetofThings.MODID, value = Dist.CLIENT)
public final class DeviceDebugOverlay {
    private static int lastSyncTick = Integer.MIN_VALUE;

    private DeviceDebugOverlay() {}

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos basePos = DoorPosHelper.normalizeDoorBasePos(mc.level, blockHitResult.getBlockPos());
        if (!mc.level.getBlockState(basePos).is(IotTags.NETWORKABLE_DOORS)) {
            return;
        }

        requestHomeSync(mc);
        SmartDeviceSavedData.DeviceSnapshot snapshot = ClientHomeState.findByPos(basePos.asLong());
        if (snapshot == null || snapshot.id == null) {
            return;
        }

        String fallbackOwner = mc.player.getScoreboardName();
        String ownedName = snapshot.ownedName(fallbackOwner);
        event.getLeft().add("SmartHome: " + ownedName);
    }

    private static void requestHomeSync(Minecraft mc) {
        int tick = mc.player.tickCount;
        if (lastSyncTick != Integer.MIN_VALUE && tick - lastSyncTick < 40) {
            return;
        }
        lastSyncTick = tick;
        PacketDistributor.sendToServer(new HomeRequestPayload());
    }
}
