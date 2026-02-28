package com.ykz.iot.client.scanner;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ykz.iot.compat.exposure.ExposureScannerClientBridge;
import com.ykz.iot.menu.ScannerFilmMenu;
import com.ykz.iot.network.payload.scanner.ScannerStartRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;

import net.neoforged.neoforge.network.PacketDistributor;

public class ScannerFilmScreen extends AbstractContainerScreen<ScannerFilmMenu> {
    private static final ResourceLocation MAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "textures/gui/scanner.png");
    private static final ResourceLocation FILM_OVERLAYS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "textures/gui/scanner_film_overlays.png");
    private static final ResourceLocation PHOTO_OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "textures/gui/scanner_photo_outline.png");
    private static final WidgetSprites SCAN_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "scanner/scan_button"),
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "scanner/scan_button_disabled"),
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "scanner/scan_button_highlighted"));

    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;
    private static final int FRAME_SIZE = 54;
    private static final int PROGRESS_W = 24;
    private static final int PROGRESS_H = 17;
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 0;

    private ImageButton scanButton;

    public ScannerFilmScreen(ScannerFilmMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 209;
        this.inventoryLabelY = 116;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;

        int centerX = leftPos + (imageWidth - 22) / 2;
        int buttonY = topPos + 89;
        scanButton = this.addRenderableWidget(new ImageButton(
                centerX,
                buttonY,
                22,
                22,
                SCAN_BUTTON_SPRITES,
                b -> sendScanRequest(),
                Component.translatable("gui.internet_of_things.scanner.scan")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        guiGraphics.blit(MAIN_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_W, TEXTURE_H);
        guiGraphics.blit(MAIN_TEXTURE, leftPos - 27, topPos + 35, 0, 209, 28, 31, TEXTURE_W, TEXTURE_H);

        int progress = menu.getProgress();
        int max = menu.getMaxProgress();
        if (max > 0 && progress > 0) {
            int width = Math.max(1, Math.min(PROGRESS_W, (progress * PROGRESS_W) / max));
            int progressX = leftPos + (imageWidth - PROGRESS_W) / 2;
            int progressY = topPos + 91;
            guiGraphics.blit(MAIN_TEXTURE, progressX, progressY, PROGRESS_U, PROGRESS_V, width, PROGRESS_H, TEXTURE_W, TEXTURE_H);
        }

        Preview preview = resolvePreview();
        if (preview.mode == PreviewMode.NONE) {
            guiGraphics.blit(FILM_OVERLAYS_TEXTURE, leftPos + 4, topPos + 15, 0, 136, 168, 68, TEXTURE_W, TEXTURE_H);
            renderStatus(guiGraphics, preview);
            return;
        }

        if (preview.mode == PreviewMode.FILM) {
            renderFilmFrames(guiGraphics, preview.frames, mouseX, mouseY);
        } else {
            renderPhotoFrame(guiGraphics, preview.frames.get(0));
        }

        renderStatus(guiGraphics, preview);
    }

    private void renderFilmFrames(GuiGraphics guiGraphics, List<Object> frames, int mouseX, int mouseY) {
        int selected = Mth.clamp(menu.getSelectedFrame(), 0, Math.max(0, frames.size() - 1));
        Object leftFrame = selected > 0 ? frames.get(selected - 1) : null;
        Object centerFrame = selected < frames.size() ? frames.get(selected) : null;
        Object rightFrame = selected + 1 < frames.size() ? frames.get(selected + 1) : null;

        float[] filmColor = ExposureScannerClientBridge.getFilmColor(menu.getSlot(0).getItem());
        RenderSystem.setShaderColor(filmColor[0], filmColor[1], filmColor[2], filmColor[3]);
        guiGraphics.blit(FILM_OVERLAYS_TEXTURE, leftPos + 1, topPos + 15, 0, leftFrame != null ? 68 : 0, 54, 68, TEXTURE_W, TEXTURE_H);
        guiGraphics.blit(FILM_OVERLAYS_TEXTURE, leftPos + 55, topPos + 15, 55, rightFrame != null ? 0 : 68, 64, 68, TEXTURE_W, TEXTURE_H);
        if (rightFrame != null) {
            boolean hasMore = selected + 2 < frames.size();
            guiGraphics.blit(FILM_OVERLAYS_TEXTURE, leftPos + 119, topPos + 15, 120, hasMore ? 68 : 0, 56, 68, TEXTURE_W, TEXTURE_H);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        PoseStack pose = guiGraphics.pose();
        if (leftFrame != null) {
            ExposureScannerClientBridge.renderNegativeFrame(leftFrame, pose, leftPos + 6, topPos + 22, FRAME_SIZE,
                    isOverLeftFrame(mouseX, mouseY, frames.size()) ? 0.8f : 0.25f);
        }
        if (centerFrame != null) {
            ExposureScannerClientBridge.renderNegativeFrame(centerFrame, pose, leftPos + 61, topPos + 22, FRAME_SIZE, 0.9f);
        }
        if (rightFrame != null) {
            ExposureScannerClientBridge.renderNegativeFrame(rightFrame, pose, leftPos + 116, topPos + 22, FRAME_SIZE,
                    isOverRightFrame(mouseX, mouseY, frames.size()) ? 0.8f : 0.25f);
        }
    }

    private void renderPhotoFrame(GuiGraphics guiGraphics, Object frame) {
        int paperX = leftPos + 54;
        int paperY = topPos + 13;
        guiGraphics.blit(PHOTO_OUTLINE_TEXTURE, paperX + 3, paperY + 2, 0, 0, 64, 64, 64, 64);

        PoseStack pose = guiGraphics.pose();
        ExposureScannerClientBridge.renderPhotographFrame(frame, pose, leftPos + 61, topPos + 20, FRAME_SIZE, 1.0f);
    }

    private void renderStatus(GuiGraphics guiGraphics, Preview preview) {
        Component status;
        if (!menu.isNetworkOnline()) {
            status = Component.translatable("text.internet_of_things.scanner.offline_banner");
        } else {
            status = Component.translatable("text.internet_of_things.scanner.online_banner");
        }
        int color = menu.isNetworkOnline() ? 0x404040 : 0xFF4040;
        int x = leftPos + imageWidth - 6 - this.font.width(status);
        int y = topPos + 6;
        guiGraphics.drawString(this.font, status, x, y, color, false);
    }

    private void updateButtonState() {
        if (scanButton == null) {
            return;
        }
        boolean hasInput = !menu.getSlot(0).getItem().isEmpty();
        boolean hasFrames = menu.getTotalFrames() > 0;
        boolean scanning = menu.getProgress() > 0;
        scanButton.visible = !scanning;
        scanButton.active = !scanning && menu.isNetworkOnline() && hasInput && hasFrames;
        if (!hasInput) {
            scanButton.setTooltip(Tooltip.create(Component.literal("输入已冲洗胶卷或照片")));
        } else {
            scanButton.setTooltip(Tooltip.create(Component.translatable("gui.internet_of_things.scanner.scan")));
        }
    }

    private Preview resolvePreview() {
        ItemStack input = menu.getSlot(0).getItem();
        if (input.isEmpty()) {
            return new Preview(PreviewMode.NONE, Collections.emptyList());
        }

        List<Object> filmFrames = ExposureScannerClientBridge.getStoredFrames(input);
        if (!filmFrames.isEmpty()) {
            return new Preview(PreviewMode.FILM, filmFrames);
        }

        Object photoFrame = ExposureScannerClientBridge.getPhotographFrame(input);
        if (photoFrame != null) {
            return new Preview(PreviewMode.PHOTO, List.of(photoFrame));
        }

        return new Preview(PreviewMode.NONE, Collections.emptyList());
    }

    private void sendScanRequest() {
        PacketDistributor.sendToServer(new ScannerStartRequestPayload(menu.getBlockPos()));
    }

    private void changeFrame(int delta) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        int buttonId = delta > 0 ? ScannerFilmMenu.BUTTON_NEXT : ScannerFilmMenu.BUTTON_PREV;
        menu.clickMenuButton(minecraft.player, buttonId);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
    }

    private boolean isOverLeftFrame(double mouseX, double mouseY, int frameCount) {
        return isHovering(6, 22, FRAME_SIZE, FRAME_SIZE, mouseX, mouseY) && menu.getSelectedFrame() > 0 && frameCount > 1;
    }

    private boolean isOverRightFrame(double mouseX, double mouseY, int frameCount) {
        return isHovering(116, 22, FRAME_SIZE, FRAME_SIZE, mouseX, mouseY)
                && menu.getSelectedFrame() + 1 < frameCount && frameCount > 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Preview preview = resolvePreview();
            if (preview.mode == PreviewMode.FILM) {
                if (isOverLeftFrame(mouseX, mouseY, preview.frames.size())) {
                    changeFrame(-1);
                    return true;
                }
                if (isOverRightFrame(mouseX, mouseY, preview.frames.size())) {
                    changeFrame(1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Preview preview = resolvePreview();
        if (preview.mode != PreviewMode.FILM) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            changeFrame(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            changeFrame(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private enum PreviewMode {
        NONE,
        FILM,
        PHOTO
    }

    private record Preview(PreviewMode mode, List<Object> frames) {
    }
}
