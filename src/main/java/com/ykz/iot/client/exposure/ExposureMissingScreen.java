package com.ykz.iot.client.exposure;

import com.ykz.iot.compat.exposure.ExposureCompat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ExposureMissingScreen extends Screen {
    private static final String DOWNLOAD_URL = "https://www.curseforge.com/minecraft/mc-mods/exposure";
    private final Screen parent;
    private final String detectedVersion;

    public ExposureMissingScreen(Screen parent, String detectedVersion) {
        super(Component.translatable("screen.internet_of_things.phone.camera.unavailable.title"));
        this.parent = parent;
        this.detectedVersion = detectedVersion == null ? "" : detectedVersion;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 260;
        int x = this.width / 2 - buttonWidth / 2;
        int y = this.height / 2 + 18;

        this.addRenderableWidget(Button.builder(Component.literal(DOWNLOAD_URL), b ->
                        Util.getPlatform().openUri(DOWNLOAD_URL))
                .pos(x, y)
                .size(buttonWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, 0xFF5555);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.internet_of_things.phone.camera.unavailable.need"),
                this.width / 2, this.height / 2 - 24, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.internet_of_things.phone.camera.unavailable.min_version", ExposureCompat.MIN_VERSION),
                this.width / 2, this.height / 2 - 12, 0xFFFFFF);

        if (!detectedVersion.isBlank()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.internet_of_things.phone.camera.unavailable.current_version", detectedVersion),
                    this.width / 2, this.height / 2, 0xAAAAAA);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

