package com.ykz.iot.client.trade;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class TradeClientBridge {
    private TradeClientBridge() {
    }

    public static void handleStatusMessage(String key, String style) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        Component message = Component.translatable(key);
        ChatFormatting formatting = ChatFormatting.getByName(style);
        if (formatting != null) {
            message = message.copy().withStyle(formatting);
        }

        if (mc.screen instanceof PhoneTradeScreen tradeScreen) {
            tradeScreen.showInlineMessage(message, 60);
            return;
        }

        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }
}
