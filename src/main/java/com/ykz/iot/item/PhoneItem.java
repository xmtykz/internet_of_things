package com.ykz.iot.item;

import com.ykz.iot.client.ClientSignalCache;
import com.ykz.iot.device.DoorPosHelper;
import com.ykz.iot.device.IotTags;
import com.ykz.iot.network.DeviceNetworkService;
import com.ykz.iot.util.SignalTextFormatter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class PhoneItem extends Item {
    public PhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            try {
                Class.forName("com.ykz.iot.client.ClientHooks")
                        .getMethod("openPhoneScreen")
                        .invoke(null);
            } catch (Throwable ignored) {
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = DoorPosHelper.normalizeDoorBasePos(level, context.getClickedPos());
        if (level.getBlockState(pos).is(IotTags.NETWORKABLE_DOORS)) {
            if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
                DeviceNetworkService.sendDeviceDetail(player, pos, true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            openPhoneScreenClientOnly();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(SignalTextFormatter.formatSignalText(ClientSignalCache.getSignalLevel()));
    }

    private static void openPhoneScreenClientOnly() {
        try {
            Class.forName("com.ykz.iot.client.ClientHooks")
                    .getMethod("openPhoneScreen")
                    .invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
