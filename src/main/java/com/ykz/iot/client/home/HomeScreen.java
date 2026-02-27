package com.ykz.iot.client.home;

import com.ykz.iot.device.SmartDeviceSavedData;
import com.ykz.iot.network.payload.device.DeviceActionPayload;
import com.ykz.iot.network.payload.device.HomeRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HomeScreen extends Screen {
    private final Screen parent;

    private int listTop;
    private int listBottom;
    private int scroll;
    private final List<Row> rows = new ArrayList<>();
    private int selectedDeviceIndex = -1;
    private int syncCooldown = 0;
    private Button configureButton;
    private Button toggleButton;
    private Button removeButton;

    public HomeScreen(Screen parent) {
        super(Component.translatable("screen.internet_of_things.home.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.listTop = 36;
        this.listBottom = this.height - 52;

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.home.refresh"), b -> requestHomeData())
                .pos(12, 10)
                .size(70, 20)
                .build());

        this.configureButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.home.configure"), b -> openSelectedDetail())
                .pos(this.width - 162, this.height - 34)
                .size(72, 20)
                .build());

        this.toggleButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.home.toggle"), b -> toggleSelected())
                .pos(this.width - 84, this.height - 34)
                .size(72, 20)
                .build());

        this.removeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.device.remove"), b -> removeSelected())
                .pos(this.width - 240, this.height - 34)
                .size(72, 20)
                .build());

        rebuildRows();
        updateActionButtons();
        requestHomeData();
    }

    public void onDataUpdated() {
        rebuildRows();
        updateActionButtons();
    }

    private void requestHomeData() {
        PacketDistributor.sendToServer(new HomeRequestPayload());
    }

    private void openSelectedDetail() {
        SmartDeviceSavedData.DeviceSnapshot selected = getSelectedDevice();
        if (selected == null || selected.id == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "request_detail_by_id");
        tag.putUUID("id", selected.id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void toggleSelected() {
        SmartDeviceSavedData.DeviceSnapshot selected = getSelectedDevice();
        if (selected == null || selected.id == null) {
            return;
        }
        if (selected.status == SmartDeviceSavedData.DeviceStatus.OFFLINE || selected.scheduleEnabled) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "toggle");
        tag.putUUID("id", selected.id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void removeSelected() {
        SmartDeviceSavedData.DeviceSnapshot selected = getSelectedDevice();
        if (selected == null || selected.id == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "remove");
        tag.putUUID("id", selected.id);
        PacketDistributor.sendToServer(new DeviceActionPayload(tag));
    }

    private void rebuildRows() {
        rows.clear();
        List<SmartDeviceSavedData.DeviceSnapshot> devices = ClientHomeState.devices();
        if (!devices.isEmpty()) {
            rows.add(Row.group("\u95E8"));
        }
        for (SmartDeviceSavedData.DeviceSnapshot d : devices) {
            rows.add(Row.device(d));
        }

        if (selectedDeviceIndex >= devices.size()) {
            selectedDeviceIndex = devices.isEmpty() ? -1 : 0;
        }
        updateActionButtons();
    }

    private SmartDeviceSavedData.DeviceSnapshot getSelectedDevice() {
        if (selectedDeviceIndex < 0 || selectedDeviceIndex >= ClientHomeState.devices().size()) {
            return null;
        }
        return ClientHomeState.devices().get(selectedDeviceIndex);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int y = listTop;
        int rowHeight = 22;
        int first = scroll;
        int deviceCursor = -1;

        for (int i = first; i < rows.size(); i++) {
            if (y + rowHeight > listBottom) {
                break;
            }
            Row row = rows.get(i);
            if (row.group) {
                guiGraphics.drawString(this.font, Component.literal(row.groupName), 16, y + 7, 0xDDDDDD, false);
            } else {
                deviceCursor++;
                SmartDeviceSavedData.DeviceSnapshot d = row.device;

                int bg = (deviceCursor == selectedDeviceIndex) ? 0x55FFFFFF : 0x33000000;
                guiGraphics.fill(12, y, this.width - 12, y + rowHeight - 2, bg);

                ItemStack icon = resolveIcon(d.blockId);
                guiGraphics.renderItem(icon, 16, y + 3);

                guiGraphics.drawString(this.font, d.name, 38, y + 4, 0xFFFFFF, false);
                guiGraphics.drawString(this.font, statusText(d), 38, y + 13, statusColor(d), false);

                if (d.status != SmartDeviceSavedData.DeviceStatus.OFFLINE) {
                    int openColor = d.theoreticalOpen ? 0x66CCFF : 0xFF5555;
                    Component openText = d.theoreticalOpen
                            ? Component.translatable("text.internet_of_things.switch.on")
                            : Component.translatable("text.internet_of_things.switch.off");
                    guiGraphics.drawString(this.font, openText, this.width - 72, y + 8, openColor, false);
                }
            }
            y += rowHeight;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && scroll < Math.max(0, rows.size() - 1)) {
            scroll++;
            return true;
        }
        if (scrollY > 0 && scroll > 0) {
            scroll--;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.minecraft.setScreen(parent);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseY >= listTop && mouseY <= listBottom) {
            int rowHeight = 22;
            int index = (int) ((mouseY - listTop) / rowHeight) + scroll;
            if (index >= 0 && index < rows.size()) {
                int deviceIndex = -1;
                for (int i = 0; i <= index; i++) {
                    if (!rows.get(i).group) {
                        deviceIndex++;
                    }
                }
                if (!rows.get(index).group && deviceIndex >= 0) {
                    selectedDeviceIndex = deviceIndex;
                    updateActionButtons();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }
        syncCooldown = 20;
        requestHomeData();
    }

    private void updateActionButtons() {
        if (configureButton == null) {
            return;
        }
        SmartDeviceSavedData.DeviceSnapshot selected = getSelectedDevice();
        boolean has = selected != null && selected.id != null;
        boolean offline = has && selected.status == SmartDeviceSavedData.DeviceStatus.OFFLINE;

        configureButton.active = has;
        toggleButton.active = has && !offline && !selected.scheduleEnabled;
        removeButton.visible = true;
        removeButton.active = has;
    }

    private static ItemStack resolveIcon(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(block.asItem());
    }

    private Component statusText(SmartDeviceSavedData.DeviceSnapshot d) {
        return switch (d.status) {
            case LOADED -> Component.translatable("text.internet_of_things.status.loaded");
            case UNLOADED -> Component.translatable("text.internet_of_things.status.unloaded");
            case OFFLINE -> Component.translatable("text.internet_of_things.status.offline");
        };
    }

    private int statusColor(SmartDeviceSavedData.DeviceSnapshot d) {
        return switch (d.status) {
            case LOADED -> 0x66CCFF;
            case UNLOADED -> 0xAAAAAA;
            case OFFLINE -> 0xFF5555;
        };
    }

    private static final class Row {
        final boolean group;
        final String groupName;
        final SmartDeviceSavedData.DeviceSnapshot device;

        private Row(boolean group, String groupName, SmartDeviceSavedData.DeviceSnapshot device) {
            this.group = group;
            this.groupName = groupName;
            this.device = device;
        }

        static Row group(String name) {
            return new Row(true, name, null);
        }

        static Row device(SmartDeviceSavedData.DeviceSnapshot d) {
            return new Row(false, null, d);
        }
    }
}
