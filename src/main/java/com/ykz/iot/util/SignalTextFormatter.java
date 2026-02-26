package com.ykz.iot.util;

import com.ykz.iot.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class SignalTextFormatter {
    private SignalTextFormatter() {}

    public static int displayLevel(int raw) {
        if (Config.PHONE_TOOLTIP_UNLIMITED.getAsBoolean()) {
            return raw;
        }
        return Math.min(raw, Config.PHONE_TOOLTIP_MAX_LEVEL.get());
    }

    public static Component formatSignalText(int rawLevel) {
        if (rawLevel < 1) {
            return Component.translatable("text.internet_of_things.signal.offline")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("text.internet_of_things.signal.level", displayLevel(rawLevel))
                .withStyle(ChatFormatting.BLUE);
    }
}
