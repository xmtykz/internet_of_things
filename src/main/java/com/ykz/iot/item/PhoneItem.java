package com.ykz.iot.item;

import com.ykz.iot.Config;
import com.ykz.iot.client.ClientSignalCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class PhoneItem extends Item {
    public PhoneItem(Properties properties) {
        super(properties);
    }

    // 右键空气：打开手机 GUI（你选了 4B：右键方块不拦截）
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 这里用反射调用客户端类，避免 Dedicated Server 因为类加载报错
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int level = ClientSignalCache.getSignalLevel();

        // 你确认：<1 就无信号（以后可能出现负数）
        if (level < 1) {
            tooltipComponents.add(Component.literal("无信号").withStyle(ChatFormatting.RED));
            return;
        }

        int showLevel = level;
        if (!Config.PHONE_TOOLTIP_UNLIMITED.getAsBoolean()) {
            showLevel = Math.min(level, Config.PHONE_TOOLTIP_MAX_LEVEL.get());
        }

        tooltipComponents.add(Component.literal("当前信号强度：" + showLevel + "级").withStyle(ChatFormatting.BLUE));
    }
}