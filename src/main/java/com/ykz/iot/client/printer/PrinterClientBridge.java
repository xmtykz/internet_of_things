package com.ykz.iot.client.printer;

import com.ykz.iot.client.exposure.PhoneAlbumScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PrinterClientBridge {
    private PrinterClientBridge() {
    }

    public static void handleStatusMessage(String key, String styleName) {
        ChatFormatting style = styleName != null ? ChatFormatting.getByName(styleName) : null;
        Component message = style == null
                ? Component.translatable(key)
                : Component.translatable(key).withStyle(style);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneAlbumScreen screen) {
            screen.showInlineMessage(message, 60);
            return;
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
        }
    }
}
