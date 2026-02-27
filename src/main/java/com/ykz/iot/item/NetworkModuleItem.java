package com.ykz.iot.item;

import com.ykz.iot.device.DoorPosHelper;
import com.ykz.iot.device.IotTags;
import com.ykz.iot.device.SmartDeviceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class NetworkModuleItem extends Item {
    public NetworkModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = DoorPosHelper.normalizeDoorBasePos(level, context.getClickedPos());
        if (!level.getBlockState(pos).is(IotTags.NETWORKABLE_DOORS)) {
            return InteractionResult.PASS;
        }

        SmartDeviceSavedData data = SmartDeviceSavedData.get(level);
        var existing = data.findByPos(pos);
        if (existing.isPresent() && !existing.get().offline) {
            return InteractionResult.CONSUME;
        }

        String ownerName = context.getPlayer() == null ? "" : context.getPlayer().getScoreboardName();
        data.installAt(level, pos, level.getBlockState(pos).getBlock(), ownerName);
        ItemStack stack = context.getItemInHand();
        stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}
