package com.ykz.iot.client.home;

import com.ykz.iot.device.SmartDeviceSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class ClientHomeState {
    private static final List<SmartDeviceSavedData.DeviceSnapshot> DEVICES = new ArrayList<>();

    private ClientHomeState() {}

    public static List<SmartDeviceSavedData.DeviceSnapshot> devices() {
        return List.copyOf(DEVICES);
    }

    public static SmartDeviceSavedData.DeviceSnapshot findByPos(long posLong) {
        for (SmartDeviceSavedData.DeviceSnapshot snapshot : DEVICES) {
            if (snapshot.pos == posLong) {
                return snapshot;
            }
        }
        return null;
    }

    public static void applyHomeData(CompoundTag tag) {
        DEVICES.clear();
        ListTag list = tag.getList("devices", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            DEVICES.add(SmartDeviceSavedData.DeviceSnapshot.fromTag((CompoundTag) t));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HomeScreen homeScreen) {
            homeScreen.onDataUpdated();
        }
    }

    public static void openOrUpdateDetail(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        if (mc.screen instanceof DeviceDetailScreen detailScreen) {
            detailScreen.updateFromServer(tag);
            return;
        }

        mc.setScreen(new DeviceDetailScreen(mc.screen, tag));
    }
}
