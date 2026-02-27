package com.ykz.iot.device;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = InternetofThings.MODID)
public final class DeviceDropHandler {
    private DeviceDropHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getPlayer() == null || event.getPlayer().isCreative()) {
            return;
        }

        BlockPos basePos = DoorPosHelper.normalizeDoorBasePos(level, event.getPos());
        BlockState state = level.getBlockState(basePos);
        if (!state.is(IotTags.NETWORKABLE_DOORS)) {
            return;
        }

        spawnModuleDropIfInstalled(level, basePos);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Set<Long> handled = new HashSet<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(IotTags.NETWORKABLE_DOORS)) {
                continue;
            }

            BlockPos basePos = DoorPosHelper.normalizeDoorBasePos(level, pos);
            long key = basePos.asLong();
            if (!handled.add(key)) {
                continue;
            }

            spawnModuleDropIfInstalled(level, basePos);
        }
    }

    private static void spawnModuleDropIfInstalled(ServerLevel level, BlockPos basePos) {
        boolean consumed = SmartDeviceSavedData.get(level).consumeInstalledModuleOnRemoval(basePos);
        if (!consumed) {
            return;
        }

        ItemStack stack = new ItemStack(ModItems.NETWORK_MODULE.get());
        ItemEntity entity = new ItemEntity(
                level,
                basePos.getX() + 0.5D,
                basePos.getY() + 0.5D,
                basePos.getZ() + 0.5D,
                stack
        );
        level.addFreshEntity(entity);
    }
}
