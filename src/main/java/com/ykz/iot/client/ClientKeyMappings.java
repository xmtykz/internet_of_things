package com.ykz.iot.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ykz.iot.InternetofThings;
import com.ykz.iot.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = InternetofThings.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientKeyMappings {
    private ClientKeyMappings() {}

    // 默认 G，可在按键设置中修改
    public static final Lazy<KeyMapping> OPEN_PHONE = Lazy.of(() ->
            new KeyMapping(
                    "key.internet_of_things.open_phone",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    "key.categories.internet_of_things"
            )
    );

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PHONE.get());
    }

    @EventBusSubscriber(modid = InternetofThings.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class ClientTickHandler {
        private ClientTickHandler() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 你选了 3A：有其他界面时不弹
            if (mc.screen != null) return;

            // consumeClick 用 while，避免一帧内多次点击丢事件
            while (OPEN_PHONE.get().consumeClick()) {
                if (!hasPhoneInInventory(mc)) return;
                ClientHooks.openPhoneScreen();
            }
        }

        private static boolean hasPhoneInInventory(Minecraft mc) {
            // 背包+快捷栏都在 items 中；你选了 2A
            for (ItemStack stack : mc.player.getInventory().items) {
                if (!stack.isEmpty() && stack.is(ModItems.PHONE.get())) return true;
            }
            // 副手也算
            ItemStack off = mc.player.getOffhandItem();
            return !off.isEmpty() && off.is(ModItems.PHONE.get());
        }
    }
}