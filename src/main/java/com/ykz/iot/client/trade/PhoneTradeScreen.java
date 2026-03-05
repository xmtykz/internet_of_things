package com.ykz.iot.client.trade;

import com.ykz.iot.network.payload.trade.TradeActionPayload;
import com.ykz.iot.network.payload.trade.TradeRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PhoneTradeScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int TRADE_BUTTON_WIDTH = 44;
    private static final int TRADE_BUTTON_HEIGHT = 16;

    private final Screen parent;
    private final List<ClientTradeState.TradeEntry> rows = new ArrayList<>();

    private int listTop;
    private int listBottom;
    private int scroll;
    private int selectedIndex = -1;
    private Button deleteButton;
    private Component statusMessage;
    private int statusMessageTicks;

    public PhoneTradeScreen(Screen parent) {
        super(Component.translatable("screen.internet_of_things.phone.trade"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.listTop = 36;
        this.listBottom = this.height - 52;
        this.scroll = 0;

        this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.home.refresh"), b -> requestData())
                .pos(12, 10)
                .size(70, 20)
                .build());

        this.deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.internet_of_things.trade.delete"), b -> deleteSelected())
                .pos(this.width - 84, this.height - 34)
                .size(72, 20)
                .build());

        onDataUpdated();
        requestData();
    }

    public void onDataUpdated() {
        rows.clear();
        rows.addAll(ClientTradeState.entries());
        if (selectedIndex >= rows.size()) {
            selectedIndex = rows.isEmpty() ? -1 : rows.size() - 1;
        }
        if (deleteButton != null) {
            deleteButton.active = selectedIndex >= 0 && selectedIndex < rows.size();
        }
    }

    private void requestData() {
        PacketDistributor.sendToServer(new TradeRequestPayload());
    }

    private void deleteSelected() {
        ClientTradeState.TradeEntry selected = getSelected();
        if (selected == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "delete");
        tag.putString("id", selected.id());
        PacketDistributor.sendToServer(new TradeActionPayload(tag));
    }

    private ClientTradeState.TradeEntry getSelected() {
        if (selectedIndex < 0 || selectedIndex >= rows.size()) {
            return null;
        }
        return rows.get(selectedIndex);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusMessageTicks > 0) {
            statusMessageTicks--;
            if (statusMessageTicks <= 0) {
                statusMessage = null;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        renderStatusMessage(guiGraphics);

        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.internet_of_things.trade.empty"),
                    this.width / 2, this.height / 2, 0xAAAAAA);
            renderNextRestock(guiGraphics);
            return;
        }

        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        int y = listTop;
        ItemStack hoverStack = ItemStack.EMPTY;
        for (int i = scroll; i < rows.size() && y + ROW_HEIGHT <= listBottom; i++) {
            ClientTradeState.TradeEntry row = rows.get(i);
            boolean selected = i == selectedIndex;

            int bg = selected ? 0x55FFFFFF : 0x33000000;
            guiGraphics.fill(12, y, this.width - 12, y + ROW_HEIGHT - 2, bg);

            ItemStack rowHover = renderTradeRow(guiGraphics, row, y, mouseX, mouseY);
            if (!rowHover.isEmpty()) {
                hoverStack = rowHover;
            }
            y += ROW_HEIGHT;
        }

        renderNextRestock(guiGraphics);
        if (!hoverStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoverStack, mouseX, mouseY);
        }
    }

    private ItemStack renderTradeRow(GuiGraphics guiGraphics, ClientTradeState.TradeEntry row, int y, int mouseX, int mouseY) {
        int iconY = y + 4;
        int x = 16;
        ItemStack hoverStack = ItemStack.EMPTY;

        drawStackWithCount(guiGraphics, row.costA(), x, iconY);
        if (isInside(mouseX, mouseY, x, iconY, 16, 16) && !row.costA().isEmpty()) {
            hoverStack = row.costA();
        }
        x += 18;

        if (!row.costB().isEmpty()) {
            guiGraphics.drawString(this.font, "+", x, y + 8, 0xCCCCCC, false);
            x += 10;
            drawStackWithCount(guiGraphics, row.costB(), x, iconY);
            if (isInside(mouseX, mouseY, x, iconY, 16, 16) && !row.costB().isEmpty()) {
                hoverStack = row.costB();
            }
            x += 18;
        }

        guiGraphics.drawString(this.font, "->", x, y + 8, 0xCCCCCC, false);
        x += 14;
        drawStackWithCount(guiGraphics, row.result(), x, iconY);
        if (isInside(mouseX, mouseY, x, iconY, 16, 16) && !row.result().isEmpty()) {
            hoverStack = row.result();
        }
        x += 22;

        int textMaxWidth = Math.max(20, this.width - 255);
        String text = this.font.plainSubstrByWidth(resultDisplayName(row.result()), textMaxWidth);
        guiGraphics.drawString(this.font, text, x, y + 8, 0xFFFFFF, false);

        int stockRight = this.width - 16 - TRADE_BUTTON_WIDTH - 10;
        Component stock = Component.translatable("text.internet_of_things.trade.stock", row.remainingStock());
        guiGraphics.drawString(this.font, stock, stockRight - this.font.width(stock), y + 8, 0xD8D8D8, false);

        int bx = this.width - 16 - TRADE_BUTTON_WIDTH;
        int by = y + (ROW_HEIGHT - TRADE_BUTTON_HEIGHT) / 2;
        boolean hovered = mouseX >= bx && mouseX <= bx + TRADE_BUTTON_WIDTH && mouseY >= by && mouseY <= by + TRADE_BUTTON_HEIGHT;
        int btnBg = hovered ? 0xFF606060 : 0xFF4A4A4A;
        guiGraphics.fill(bx, by, bx + TRADE_BUTTON_WIDTH, by + TRADE_BUTTON_HEIGHT, btnBg);
        Component tradeText = Component.translatable("screen.internet_of_things.trade.trade_button");
        guiGraphics.drawCenteredString(this.font, tradeText, bx + TRADE_BUTTON_WIDTH / 2, by + 4, 0xFFFFFF);
        return hoverStack;
    }

    private void drawStackWithCount(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        guiGraphics.renderItem(stack, x, y);
        guiGraphics.renderItemDecorations(this.font, stack, x, y);
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private String resultDisplayName(ItemStack stack) {
        String base = stack.getHoverName().getString();
        if (!stack.is(Items.ENCHANTED_BOOK)) {
            return base;
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return base;
        }

        List<String> details = new ArrayList<>();
        for (var entry : enchantments.entrySet()) {
            details.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
        }
        if (details.isEmpty()) {
            return base;
        }
        return base + "（" + String.join("，", details) + "）";
    }

    private void renderNextRestock(GuiGraphics guiGraphics) {
        long next = ClientTradeState.nextRestockGameTime();
        long now = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        long remainingTicks = Math.max(0L, next - now);
        int minutes = (int) Math.ceil(remainingTicks / 1200.0D);

        Component text = Component.translatable("text.internet_of_things.trade.next_restock", minutes);
        int x = this.width - 8 - this.font.width(text);
        int y = this.height - 42;
        guiGraphics.drawString(this.font, text, x, y, 0xFFFFFF, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        if (scrollY < 0 && scroll < maxScroll) {
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

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mouseY < listTop || mouseY > listBottom || rows.isEmpty()) {
            return false;
        }

        int rowIndex = (int) ((mouseY - listTop) / ROW_HEIGHT) + scroll;
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return false;
        }

        selectedIndex = rowIndex;
        if (deleteButton != null) {
            deleteButton.active = true;
        }

        int rowY = listTop + (rowIndex - scroll) * ROW_HEIGHT;
        int bx = this.width - 16 - TRADE_BUTTON_WIDTH;
        int by = rowY + (ROW_HEIGHT - TRADE_BUTTON_HEIGHT) / 2;
        boolean onTradeButton = mouseX >= bx && mouseX <= bx + TRADE_BUTTON_WIDTH && mouseY >= by && mouseY <= by + TRADE_BUTTON_HEIGHT;
        if (onTradeButton) {
            sendTrade(rows.get(rowIndex), hasShiftDown());
        }
        return true;
    }

    private void sendTrade(ClientTradeState.TradeEntry row, boolean batch) {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", "trade");
        tag.putString("id", row.id());
        tag.putBoolean("batch", batch);
        PacketDistributor.sendToServer(new TradeActionPayload(tag));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void showInlineMessage(Component message, int ticks) {
        this.statusMessage = message;
        this.statusMessageTicks = Math.max(0, ticks);
    }

    private void renderStatusMessage(GuiGraphics guiGraphics) {
        if (statusMessage == null || statusMessageTicks <= 0) {
            return;
        }
        int y = this.height - 54;
        guiGraphics.drawCenteredString(this.font, statusMessage, this.width / 2, y, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
