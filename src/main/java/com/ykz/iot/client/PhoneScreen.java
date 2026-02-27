package com.ykz.iot.client;

import com.ykz.iot.client.exposure.ExposureMissingScreen;
import com.ykz.iot.client.exposure.PhoneAlbumScreen;
import com.ykz.iot.client.home.HomeOfflineScreen;
import com.ykz.iot.client.home.HomeScreen;
import com.ykz.iot.compat.exposure.ExposureCompat;
import com.ykz.iot.util.SignalTextFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.album"), b -> {
                    if (!ExposureCompat.isCompatible()) {
                        this.minecraft.setScreen(new ExposureMissingScreen(this, ExposureCompat.getLoadedVersion()));
                        return;
                    }
                    this.minecraft.setScreen(new PhoneAlbumScreen(this));
                })
                .pos(leftX, topY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.trade"), b -> {})
                .pos(rightX, topY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.home"), b -> {
                    if (ClientSignalCache.getSignalLevel() < 1) {
                        this.minecraft.setScreen(new HomeOfflineScreen(this));
                    } else {
                        this.minecraft.setScreen(new HomeScreen(this));
                    }
                })
                .pos(leftX, bottomY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.production"), b -> {})
                .pos(rightX, bottomY).size(buttonW, buttonH).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.phone.camera"), b -> {
                    if (!ExposureCompat.isCompatible()) {
                        this.minecraft.setScreen(new ExposureMissingScreen(this, ExposureCompat.getLoadedVersion()));
                        return;
                    }
                    if (!ExposureCompat.startPhoneCameraSession()) {
                        this.minecraft.setScreen(new ExposureMissingScreen(this, ExposureCompat.getLoadedVersion()));
                    }
                })
                .pos(centerX - buttonW / 2, bottomY + buttonH + gap)
                .size(buttonW, buttonH)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);

        Component signalComp = SignalTextFormatter.formatSignalText(ClientSignalCache.getSignalLevel());
        int x = this.width - 8 - this.font.width(signalComp);
        int y = 8;
        guiGraphics.drawString(this.font, signalComp, x, y, 0xFFFFFF, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
