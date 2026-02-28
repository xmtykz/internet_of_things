package com.ykz.iot.client.exposure;

import com.mojang.blaze3d.platform.NativeImage;
import com.ykz.iot.compat.exposure.ExposureCompat;
import com.ykz.iot.compat.exposure.ExposureRuntime;
import com.ykz.iot.network.payload.printer.PrinterPrintRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.neoforged.neoforge.network.PacketDistributor;

public class PhoneAlbumScreen extends Screen {
    private static final int OUTER_PADDING = 10;
    private static final int GRID_TOP = 34;
    private static final int GRID_BOTTOM = 52;
    private static final int CELL_SIZE = 72;
    private static final int GRID_GAP = 6;
    private static final int THUMB_SIZE = 640;

    private final Screen parent;
    private final List<Path> photos = new ArrayList<>();
    private final Map<Path, TextureRef> textures = new HashMap<>();

    private int scrollRows = 0;
    private Path selectedPhoto;
    private Component statusMessage;
    private int statusMessageTicks = 0;
    private Button shareButton;
    private Button printButton;
    private Button exportButton;
    private Button deleteButton;

    public PhoneAlbumScreen(Screen parent) {
        super(Component.translatable("screen.internet_of_things.phone.album"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        clearTextures();
        photos.clear();
        scrollRows = 0;
        selectedPhoto = null;
        loadPhotoList();
        initActionButtons();
        updateActionButtonsState();
    }

    private void loadPhotoList() {
        Path albumRoot = getAlbumRoot();
        if (!Files.exists(albumRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.list(albumRoot)) {
            photos.addAll(stream
                    .filter(Files::isRegularFile)
                    .filter(this::isPngFile)
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed())
                    .toList());
        } catch (IOException ignored) {
        }
    }

    private boolean isPngFile(Path path) {
        String n = path.getFileName().toString().toLowerCase();
        return n.endsWith(".png");
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private Path getAlbumRoot() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("screenshots")
                .resolve("phone_photo")
                .resolve(ExposureRuntime.currentWorldFolder());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        renderStatusMessage(guiGraphics);

        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int maxScroll = getMaxScrollRows(cols, visibleRows);
        scrollRows = Mth.clamp(scrollRows, 0, maxScroll);

        int start = scrollRows * cols;
        int maxVisible = visibleRows * cols;
        int end = Math.min(photos.size(), start + maxVisible);

        if (photos.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("No photos").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xAAAAAA);
            return;
        }

        for (int index = start; index < end; index++) {
            int relative = index - start;
            int row = relative / cols;
            int col = relative % cols;
            int x = OUTER_PADDING + col * (CELL_SIZE + GRID_GAP);
            int y = GRID_TOP + row * (CELL_SIZE + GRID_GAP);

            Path photo = photos.get(index);
            TextureRef texture = getOrCreateTexture(photo);
            int borderColor = photo.equals(selectedPhoto) ? 0xFFFFFFFF : 0xFF303030;
            guiGraphics.fill(x - 1, y - 1, x + CELL_SIZE + 1, y + CELL_SIZE + 1, borderColor);
            guiGraphics.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, 0xFF111111);
            if (texture != null && texture.location != null) {
                guiGraphics.blit(texture.location, x, y,
                        CELL_SIZE, CELL_SIZE,
                        0.0F, 0.0F,
                        THUMB_SIZE, THUMB_SIZE,
                        THUMB_SIZE, THUMB_SIZE);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int maxScroll = getMaxScrollRows(cols, visibleRows);
        int delta = scrollY < 0 ? 1 : -1;
        scrollRows = Mth.clamp(scrollRows + delta, 0, maxScroll);
        return true;
    }

    @Override
    public void tick() {
        if (statusMessageTicks > 0) {
            statusMessageTicks--;
            if (statusMessageTicks == 0) {
                statusMessage = null;
            }
        }
        super.tick();
    }

    private int getColumns() {
        int available = this.width - OUTER_PADDING * 2;
        return Math.max(1, (available + GRID_GAP) / (CELL_SIZE + GRID_GAP));
    }

    private int getVisibleRows() {
        int availableHeight = this.height - GRID_TOP - GRID_BOTTOM;
        return Math.max(1, (availableHeight + GRID_GAP) / (CELL_SIZE + GRID_GAP));
    }

    private int getMaxScrollRows(int cols, int visibleRows) {
        int rows = (int) Math.ceil(photos.size() / (double) cols);
        return Math.max(0, rows - visibleRows);
    }

    private TextureRef getOrCreateTexture(Path path) {
        TextureRef existing = textures.get(path);
        if (existing != null) {
            return existing;
        }

        TextureRef created = createTexture(path);
        if (created != null) {
            textures.put(path, created);
        }
        return created;
    }

    private TextureRef createTexture(Path path) {
        try (InputStream in = Files.newInputStream(path);
             NativeImage source = NativeImage.read(in)) {
            NativeImage thumb = createSquareThumbnail(source, THUMB_SIZE);
            DynamicTexture texture = new DynamicTexture(thumb);
            texture.setFilter(false, false);
            ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("iot/phone_album/" + Integer.toHexString(path.toString().hashCode()), texture);
            return new TextureRef(location, texture);
        } catch (Exception ignored) {
            return null;
        }
    }

    private NativeImage createSquareThumbnail(NativeImage source, int size) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int crop = Math.min(srcW, srcH);
        int x0 = (srcW - crop) / 2;
        int y0 = (srcH - crop) / 2;

        NativeImage out = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = x0 + (x * crop) / size;
                int sy = y0 + (y * crop) / size;
                out.setPixelRGBA(x, y, source.getPixelRGBA(sx, sy));
            }
        }
        return out;
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
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int index = getPhotoIndexAt(mouseX, mouseY);
            if (index >= 0 && index < photos.size()) {
                selectedPhoto = photos.get(index);
                updateActionButtonsState();
                return true;
            }
        }
        return false;
    }

    private int getPhotoIndexAt(double mouseX, double mouseY) {
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int maxScroll = getMaxScrollRows(cols, visibleRows);
        scrollRows = Mth.clamp(scrollRows, 0, maxScroll);

        int localX = Mth.floor(mouseX) - OUTER_PADDING;
        int localY = Mth.floor(mouseY) - GRID_TOP;
        if (localX < 0 || localY < 0) {
            return -1;
        }

        int span = CELL_SIZE + GRID_GAP;
        int col = localX / span;
        int row = localY / span;
        if (col < 0 || col >= cols || row < 0 || row >= visibleRows) {
            return -1;
        }

        int withinX = localX % span;
        int withinY = localY % span;
        if (withinX >= CELL_SIZE || withinY >= CELL_SIZE) {
            return -1;
        }

        int index = scrollRows * cols + row * cols + col;
        return index < photos.size() ? index : -1;
    }

    private void initActionButtons() {
        int buttonW = 54;
        int buttonH = 20;
        int gap = 6;
        int totalWidth = buttonW * 4 + gap * 3;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - buttonH - 8;

        shareButton = this.addRenderableWidget(Button.builder(Component.literal("\u5206\u4eab"), b -> {})
                .pos(startX, y).size(buttonW, buttonH).build());
        printButton = this.addRenderableWidget(Button.builder(Component.literal("\u6253\u5370"), b -> printSelectedPhoto())
                .pos(startX + (buttonW + gap), y).size(buttonW, buttonH).build());
        exportButton = this.addRenderableWidget(Button.builder(Component.literal("\u5bfc\u51fa"), b -> {})
                .pos(startX + (buttonW + gap) * 2, y).size(buttonW, buttonH).build());
        deleteButton = this.addRenderableWidget(Button.builder(Component.literal("\u5220\u9664"), b -> deleteSelectedPhoto())
                .pos(startX + (buttonW + gap) * 3, y).size(buttonW, buttonH).build());
    }
    private void updateActionButtonsState() {
        boolean hasSelection = selectedPhoto != null && photos.contains(selectedPhoto);
        if (shareButton != null) shareButton.active = hasSelection;
        if (printButton != null) printButton.active = hasSelection;
        if (exportButton != null) exportButton.active = hasSelection;
        if (deleteButton != null) deleteButton.active = hasSelection;
    }

    private void deleteSelectedPhoto() {
        if (selectedPhoto == null) {
            updateActionButtonsState();
            return;
        }
        Path path = selectedPhoto;
        int index = photos.indexOf(path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return;
        }
        photos.remove(path);
        releaseTexture(path);
        if (!photos.isEmpty()) {
            int nextIndex = index;
            if (nextIndex < 0) {
                nextIndex = 0;
            }
            if (nextIndex >= photos.size()) {
                nextIndex = photos.size() - 1;
            }
            selectedPhoto = photos.get(nextIndex);
        } else {
            selectedPhoto = null;
        }

        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int maxScroll = getMaxScrollRows(cols, visibleRows);
        scrollRows = Mth.clamp(scrollRows, 0, maxScroll);
        updateActionButtonsState();
    }

    private void printSelectedPhoto() {
        if (selectedPhoto == null || !photos.contains(selectedPhoto)) {
            updateActionButtonsState();
            return;
        }
        if (!ExposureCompat.isCompatible()) {
            showInlineMessage(Component.translatable("text.internet_of_things.printer.unavailable").withStyle(ChatFormatting.RED), 60);
            return;
        }

        ExposurePrintPayloadEncoder.EncodedPhoto encoded = ExposurePrintPayloadEncoder.encode(selectedPhoto);
        if (encoded == null) {
            showInlineMessage(Component.translatable("text.internet_of_things.printer.invalid_photo").withStyle(ChatFormatting.RED), 60);
            return;
        }
        long pixelCount = (long) encoded.width() * encoded.height();
        if (encoded.width() <= 0 || encoded.height() <= 0
                || pixelCount <= 0
                || encoded.pixels() == null || encoded.pixels().length != pixelCount) {
            showInlineMessage(Component.translatable("text.internet_of_things.printer.invalid_photo").withStyle(ChatFormatting.RED), 60);
            return;
        }

        PacketDistributor.sendToServer(new PrinterPrintRequestPayload(
                encoded.width(),
                encoded.height(),
                encoded.pixels(),
                encoded.paletteId(),
                encoded.unixTimestamp()
        ));
    }

    public void showInlineMessage(Component message, int ticks) {
        statusMessage = message;
        statusMessageTicks = Math.max(0, ticks);
    }

    private void renderStatusMessage(GuiGraphics guiGraphics) {
        if (statusMessage == null || statusMessageTicks <= 0) {
            return;
        }
        int y = this.height - 54;
        guiGraphics.drawCenteredString(this.font, statusMessage, this.width / 2, y, 0xFFFFFF);
    }

    @Override
    public void removed() {
        super.removed();
        clearTextures();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void releaseTexture(Path path) {
        TextureRef ref = textures.remove(path);
        if (ref == null) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(ref.location);
        } catch (Exception ignored) {
        }
        try {
            ref.texture.close();
        } catch (Exception ignored) {
        }
    }

    private void clearTextures() {
        for (Path path : new ArrayList<>(textures.keySet())) {
            releaseTexture(path);
        }
    }

    private record TextureRef(ResourceLocation location, DynamicTexture texture) {
    }
}

