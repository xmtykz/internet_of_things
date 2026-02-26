package com.ykz.iot.client;

import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openPhoneScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // 你选了 3A：只有在“没有任何界面”时才弹出
        if (mc.screen != null) return;

        mc.setScreen(new PhoneScreen());
    }
}