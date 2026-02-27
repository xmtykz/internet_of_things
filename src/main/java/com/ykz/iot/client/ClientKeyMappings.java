package com.ykz.iot.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ykz.iot.InternetofThings;
import com.ykz.iot.item.ModItems;
import com.ykz.iot.util.InventoryUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = InternetofThings.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientKeyMappings {
    private ClientKeyMappings() {}

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
            if (mc.player == null || mc.screen != null) {
                return;
            }

            while (OPEN_PHONE.get().consumeClick()) {
                if (!InventoryUtils.hasItem(mc.player, ModItems.PHONE.get())) {
                    return;
                }
                ClientHooks.openPhoneScreen();
            }
        }
    }
}
