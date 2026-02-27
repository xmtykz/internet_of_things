package com.ykz.iot.client.home;

import com.ykz.iot.device.SmartDeviceSavedData;
import com.ykz.iot.network.payload.device.DeviceActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class DeviceDetailScreen extends Screen {
    private final Screen parent;

    private long targetPos;
    private boolean networkable;
    private SmartDeviceSavedData.DeviceSnapshot device;

    private EditBox nameBox;
    private EditBox openBox;
    private EditBox closeBox;

    private Button saveNameButton;
    private Button unitButton;
    private Button repeatButton;
    private Button saveScheduleButton;
    private Button clearScheduleButton;
    private Button toggleButton;
    private Button removeButton;
    private Button installButton;

    private SmartDeviceSavedData.TimeUnitMode unitMode = SmartDeviceSavedData.TimeUnitMode.TICKS;
    private boolean repeat = false;

    public DeviceDetailScreen(Screen parent, CompoundTag serverTag) {
        super(Component.translatable("screen.internet_of_things.device.title"));
        this.parent = parent;
        apply(serverTag);
    }

    public void updateFromServer(CompoundTag serverTag) {
        apply(serverTag);
        if (this.nameBox != null && this.device != null && this.device.id != null) {
            this.nameBox.setValue(this.device.name);
            this.unitMode = this.device.unitMode;
            this.repeat = this.device.repeat;
            this.openBox.setValue(Integer.toString(fromTicks(this.device.openTick, this.unitMode)));
            this.closeBox.setValue(Integer.toString(fromTicks(this.device.closeTick, this.unitMode)));
            this.unitButton.setMessage(unitModeText());
            this.repeatButton.setMessage(repeatText());
        }
        updateUiState();
    }

    private void apply(CompoundTag serverTag) {
        this.targetPos = serverTag.getLong("targetPos");
        this.networkable = serverTag.getBoolean("networkable");
        if (serverTag.contains("device")) {
            this.device = SmartDeviceSavedData.DeviceSnapshot.fromTag(serverTag.getCompound("device"));
            this.unitMode = this.device.unitMode;
            this.repeat = this.device.repeat;
        } else {
            this.device = null;
        }
    }

    @Override
    protected void init() {
        super.init();

        int left = this.width / 2 - 120;

        this.nameBox = new EditBox(this.font, left, 42, 240, 18, Component.literal("name"));
        this.nameBox.setMaxLength(16);
        this.addRenderableWidget(this.nameBox);

        this.openBox = new EditBox(this.font, left, 92, 114, 18, Component.literal("open"));
        this.closeBox = new EditBox(this.font, left + 126, 92, 114, 18, Component.literal("close"));
        this.addRenderableWidget(this.openBox);
        this.addRenderableWidget(this.closeBox);

        this.saveNameButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.save_name"), b -> sendRename())
                .pos(left, 64).size(114, 20).build());

        this.unitButton = this.addRenderableWidget(Button.builder(unitModeText(), b -> {
                    this.unitMode = (this.unitMode == SmartDeviceSavedData.TimeUnitMode.TICKS)
                            ? SmartDeviceSavedData.TimeUnitMode.SECONDS
                            : SmartDeviceSavedData.TimeUnitMode.TICKS;
                    b.setMessage(unitModeText());
                })
                .pos(left + 126, 64).size(114, 20).build());

        this.repeatButton = this.addRenderableWidget(Button.builder(repeatText(), b -> {
                    this.repeat = !this.repeat;
                    b.setMessage(repeatText());
                })
                .pos(left, 116).size(114, 20).build());

        this.saveScheduleButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.save_schedule"), b -> sendSchedule(true))
                .pos(left + 126, 116).size(114, 20).build());

        this.clearScheduleButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.clear_schedule"), b -> sendClearSchedule())
                .pos(left, 142).size(114, 20).build());

        this.toggleButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.toggle"), b -> sendToggle())
                .pos(left + 126, 142).size(114, 20).build());

        this.removeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.remove"), b -> sendRemove())
                .pos(left, 168).size(114, 20).build());

        this.installButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.install_module"), b -> sendInstall())
                .pos(left + 126, 168).size(114, 20).build());

        if (device != null && device.id != null) {
            nameBox.setValue(device.name);
            openBox.setValue(Integer.toString(fromTicks(device.openTick, unitMode)));
            closeBox.setValue(Integer.toString(fromTicks(device.closeTick, unitMode)));
        }

        updateUiState();
    }

    private void updateUiState() {
        if (nameBox == null) {
            return;
        }

        boolean installed = isInstalled();
        boolean offlineInstalled = installed && device.status == SmartDeviceSavedData.DeviceStatus.OFFLINE;

        boolean fullConfig = installed && !offlineInstalled;

        nameBox.visible = fullConfig;
        openBox.visible = fullConfig;
        closeBox.visible = fullConfig;

        saveNameButton.visible = fullConfig;
        unitButton.visible = fullConfig;
        repeatButton.visible = fullConfig;
        saveScheduleButton.visible = fullConfig;
        clearScheduleButton.visible = fullConfig;
        toggleButton.visible = fullConfig;

        installButton.visible = !installed;
        removeButton.visible = fullConfig;

        int centerX = this.width / 2;
        if (!installed) {
            installButton.setPosition(centerX - 60, this.height / 2 + 10);
            installButton.setWidth(120);
        }

        if (fullConfig) {
            toggleButton.active = !device.scheduleEnabled && device.status != SmartDeviceSavedData.DeviceStatus.OFFLINE;
            clearScheduleButton.active = device.scheduleEnabled;
            removeButton.active = true;
        } else {
            toggleButton.active = false;
            clearScheduleButton.active = false;
            removeButton.active = false;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int currentTick = currentDayTick();
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("text.internet_of_things.device.current_tick", currentTick),
                this.width / 2, 24, 0xFFFFFF);

        if (!networkable) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("text.internet_of_things.device.not_supported"),
                    this.width / 2, 40, 0xFF5555);
            return;
        }

        if (!isInstalled()) {
            return;
        }

        if (device.status == SmartDeviceSavedData.DeviceStatus.OFFLINE) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("text.internet_of_things.device.offline_only"),
                    this.width / 2, this.height / 2 - 6, 0xFF5555);
            return;
        }

        int color = switch (device.status) {
            case LOADED -> 0x66CCFF;
            case UNLOADED -> 0xAAAAAA;
            case OFFLINE -> 0xFF5555;
        };

        Component status = switch (device.status) {
            case LOADED -> Component.translatable("text.internet_of_things.status.loaded");
            case UNLOADED -> Component.translatable("text.internet_of_things.status.unloaded");
            case OFFLINE -> Component.translatable("text.internet_of_things.status.offline");
        };

        guiGraphics.drawString(this.font, status, this.width / 2 - 120, 40, color, false);

        Component openText = device.theoreticalOpen
                ? Component.translatable("text.internet_of_things.switch.on")
                : Component.translatable("text.internet_of_things.switch.off");
        int openColor = device.theoreticalOpen ? 0x66CCFF : 0xFF5555;
        guiGraphics.drawString(this.font, openText, this.width / 2 - 20, 40, openColor, false);

        guiGraphics.drawString(this.font,
                Component.translatable("screen.internet_of_things.device.open_time"),
                this.width / 2 - 120, 82, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.internet_of_things.device.close_time"),
                this.width / 2 + 6, 82, 0xFFFFFF, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E && !isAnyInputFocused()) {
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

    private boolean isInstalled() {
        return device != null && device.id != null;
    }

    private int currentDayTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        long time = mc.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                ? mc.level.getDayTime()
                : mc.level.getGameTime();
        return (int) (time % 24000L);
    }

    private void sendInstall() {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "install_by_pos");
        tag.putLong("pos", targetPos);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void sendRename() {
        if (device == null || device.id == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "rename");
        tag.putUUID("id", device.id);
        tag.putString("name", nameBox.getValue());
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void sendSchedule(boolean enabled) {
        if (device == null || device.id == null) {
            return;
        }
        int openRaw = parseInt(openBox.getValue());
        int closeRaw = parseInt(closeBox.getValue());

        int openTick = toTicks(openRaw, unitMode);
        int closeTick = toTicks(closeRaw, unitMode);

        CompoundTag tag = new CompoundTag();
        tag.putString("action", "set_schedule");
        tag.putUUID("id", device.id);
        tag.putInt("openTick", openTick);
        tag.putInt("closeTick", closeTick);
        tag.putBoolean("repeat", repeat);
        tag.putBoolean("enabled", enabled);
        tag.putString("unit", unitMode.name());
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void sendClearSchedule() {
        if (device == null || device.id == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "clear_schedule");
        tag.putUUID("id", device.id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void sendToggle() {
        if (device == null || device.id == null || device.status == SmartDeviceSavedData.DeviceStatus.OFFLINE || device.scheduleEnabled) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "toggle");
        tag.putUUID("id", device.id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void sendRemove() {
        if (device == null || device.id == null) {
            return;
        }
        UUID id = device.id;
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "remove");
        tag.putUUID("id", id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
        this.minecraft.setScreen(parent);
    }

    private Component unitModeText() {
        return (unitMode == SmartDeviceSavedData.TimeUnitMode.TICKS)
                ? Component.translatable("screen.internet_of_things.unit.ticks")
                : Component.translatable("screen.internet_of_things.unit.seconds");
    }

    private Component repeatText() {
        return repeat
                ? Component.translatable("screen.internet_of_things.schedule.repeat")
                : Component.translatable("screen.internet_of_things.schedule.single");
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int toTicks(int value, SmartDeviceSavedData.TimeUnitMode mode) {
        int v = Math.max(0, value);
        if (mode == SmartDeviceSavedData.TimeUnitMode.SECONDS) {
            v = v * 20;
        }
        return Math.min(v, 23999);
    }

    private static int fromTicks(int ticks, SmartDeviceSavedData.TimeUnitMode mode) {
        if (mode == SmartDeviceSavedData.TimeUnitMode.SECONDS) {
            return ticks / 20;
        }
        return ticks;
    }

    private boolean isAnyInputFocused() {
        return (nameBox != null && nameBox.isFocused())
                || (openBox != null && openBox.isFocused())
                || (closeBox != null && closeBox.isFocused());
    }
}
