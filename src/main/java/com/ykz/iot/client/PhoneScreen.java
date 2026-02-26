package com.ykz.iot.client;

import com.ykz.iot.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PhoneScreen extends Screen {
    public PhoneScreen() {
        super(Component.translatable("screen.internet_of_things.phone.title"));
    }

    @Override
    protected void init() {
        super.init();

        int buttonW = 80;
        int buttonH = 20;
        int gap = 6;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int leftX = centerX - buttonW - gap / 2;
        int rightX = centerX + gap / 2;
        int topY = centerY - buttonH - gap / 2;
        int bottomY = centerY + gap / 2;

        // 相册 / 交易 / 家居 / 生产：暂时不做反应
        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.album"), b -> {})
                .pos(leftX, topY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.trade"), b -> {})
                .pos(rightX, topY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.home"), b -> {})
                .pos(leftX, bottomY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.production"), b -> {})
                .pos(rightX, bottomY).size(buttonW, buttonH).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1：renderBackground 需要四个参数
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 标题（上方居中）
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);

        // 右上角显示信号
        int raw = ClientSignalCache.getSignalLevel();
        Component signalComp;

        if (raw < 1) {
            signalComp = Component.literal("无信号").withStyle(ChatFormatting.RED);
        } else {
            int show = raw;
            if (!Config.PHONE_TOOLTIP_UNLIMITED.getAsBoolean()) {
                show = Math.min(raw, Config.PHONE_TOOLTIP_MAX_LEVEL.get());
            }
            signalComp = Component.literal("信号：" + show + "级").withStyle(ChatFormatting.BLUE);
        }

        int x = this.width - 8 - this.font.width(signalComp);
        int y = 8;
        guiGraphics.drawString(this.font, signalComp, x, y, 0xFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}