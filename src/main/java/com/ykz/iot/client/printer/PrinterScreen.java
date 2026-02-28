package com.ykz.iot.client.printer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ykz.iot.menu.PrinterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static final ResourceLocation PRINTER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("internet_of_things", "textures/gui/printer.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int PROGRESS_X = 116;
    private static final int PROGRESS_Y = 18;
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 0;
    private static final int PROGRESS_W = 24;
    private static final int PROGRESS_H = 17;

    public PrinterScreen(PrinterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(PRINTER_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        int progress = menu.getProgress();
        int max = menu.getMaxProgress();
        if (max > 0 && progress > 0) {
            int width = Math.max(1, Math.min(PROGRESS_W, (progress * PROGRESS_W) / max));
            guiGraphics.blit(PRINTER_TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                    PROGRESS_U, PROGRESS_V, width, PROGRESS_H, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        Component status = menu.isNetworkOnline()
                ? Component.translatable("text.internet_of_things.printer.tip_open_album")
                : Component.translatable("text.internet_of_things.printer.offline_banner");
        int color = menu.isNetworkOnline() ? 0x404040 : 0xFF4040;
        int x = leftPos + imageWidth - 6 - this.font.width(status);
        int y = topPos + 6;
        guiGraphics.drawString(this.font, status, x, y, color, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
