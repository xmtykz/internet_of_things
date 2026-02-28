package com.ykz.iot.blockentity;

import com.ykz.iot.compat.exposure.ExposureScannerBridge;
import com.ykz.iot.menu.ScannerFilmMenu;
import com.ykz.iot.network.IotNetworkSavedData;
import com.ykz.iot.network.payload.scanner.ScannerExportDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

import net.neoforged.neoforge.network.PacketDistributor;

public class ScannerBlockEntity extends BaseContainerBlockEntity {
    public static final int SCAN_DURATION_TICKS = 200;

    public enum StartCheck {
        OK,
        OFFLINE,
        BUSY,
        NO_SOURCE,
        INVALID_FRAME
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> isNetworkOnline() ? 1 : 0;
                case 3 -> selectedFrame;
                case 4 -> getTotalFrames();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                case 3 -> setSelectedFrame(value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(ScannerSlots.SLOTS, ItemStack.EMPTY);
    private int progress = 0;
    private int maxProgress = SCAN_DURATION_TICKS;
    private boolean scanning = false;
    private int selectedFrame = 0;
    private UUID requesterId;
    private String queuedFrameId = "";

    public ScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCANNER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScannerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (!be.scanning) {
            return;
        }
        be.progress = Mth.clamp(be.progress + 1, 0, be.maxProgress);
        if (be.progress >= be.maxProgress) {
            be.finishScan();
        } else {
            be.setChanged();
        }
    }

    public ContainerData getData() {
        return data;
    }

    public boolean isScanning() {
        return scanning;
    }

    public int getSelectedFrame() {
        return selectedFrame;
    }

    public void setSelectedFrame(int selectedFrame) {
        int max = Math.max(0, getTotalFrames() - 1);
        this.selectedFrame = Mth.clamp(selectedFrame, 0, max);
        setChanged();
    }

    public void changeFrame(int delta) {
        if (delta == 0) {
            return;
        }
        setSelectedFrame(selectedFrame + delta);
    }

    public boolean isNetworkOnline() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return IotNetworkSavedData.get(serverLevel).getNetworkLevelAt(serverLevel.getChunkAt(getBlockPos()).getPos()) >= 1;
    }

    private int getTotalFrames() {
        ItemStack input = getItem(ScannerSlots.FILM);
        if (ExposureScannerBridge.isPhotograph(input)) {
            return 1;
        }
        return ExposureScannerBridge.getStoredFramesCount(input);
    }

    public StartCheck checkCanStart() {
        if (!isNetworkOnline()) {
            return StartCheck.OFFLINE;
        }
        if (isScanning()) {
            return StartCheck.BUSY;
        }

        ItemStack input = getItem(ScannerSlots.FILM);
        if (getTotalFrames() <= 0) {
            return StartCheck.NO_SOURCE;
        }
        Optional<Object> frame = ExposureScannerBridge.getSourceFrame(input, selectedFrame, ExposureScannerBridge.isPhotograph(input));
        if (frame.isEmpty() || ExposureScannerBridge.getFrameId(frame.get()).isEmpty()) {
            return StartCheck.INVALID_FRAME;
        }
        return StartCheck.OK;
    }

    public StartCheck startScan(ServerPlayer requester) {
        StartCheck check = checkCanStart();
        if (check != StartCheck.OK) {
            return check;
        }

        ItemStack input = getItem(ScannerSlots.FILM);
        Optional<Object> frame = ExposureScannerBridge.getSourceFrame(input, selectedFrame, ExposureScannerBridge.isPhotograph(input));
        if (frame.isEmpty()) {
            return StartCheck.INVALID_FRAME;
        }
        Optional<String> frameId = ExposureScannerBridge.getFrameId(frame.get());
        if (frameId.isEmpty()) {
            return StartCheck.INVALID_FRAME;
        }

        this.requesterId = requester.getUUID();
        this.queuedFrameId = frameId.get();
        this.progress = 0;
        this.maxProgress = SCAN_DURATION_TICKS;
        this.scanning = true;
        setChanged();
        return StartCheck.OK;
    }

    private void finishScan() {
        this.scanning = false;
        this.progress = 0;

        if (!(level instanceof ServerLevel serverLevel)) {
            clearQueue();
            setChanged();
            return;
        }
        ServerPlayer requester = getRequester(serverLevel);
        if (requester == null || queuedFrameId.isBlank()) {
            clearQueue();
            setChanged();
            return;
        }

        Optional<ExposureScannerBridge.ExportedExposureData> exportedData = ExposureScannerBridge.readExposureDataById(queuedFrameId);
        if (exportedData.isEmpty()) {
            clearQueue();
            setChanged();
            return;
        }

        ExposureScannerBridge.ExportedExposureData data = exportedData.get();
        PacketDistributor.sendToPlayer(requester, new ScannerExportDataPayload(
                data.width(),
                data.height(),
                data.pixels(),
                data.paletteId(),
                data.unixTimestamp()
        ));

        clearQueue();
        setChanged();
    }

    private @Nullable ServerPlayer getRequester(ServerLevel level) {
        if (requesterId == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(requesterId);
    }

    private void clearQueue() {
        requesterId = null;
        queuedFrameId = "";
    }

    @Override
    public int getContainerSize() {
        return ScannerSlots.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == ScannerSlots.FILM) {
                setSelectedFrame(0);
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot == ScannerSlots.FILM) {
            setSelectedFrame(0);
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return slot == ScannerSlots.FILM
                && (ExposureScannerBridge.isDevelopedFilm(stack) || ExposureScannerBridge.isPhotograph(stack));
    }

    @Override
    public void clearContent() {
        items.clear();
        setSelectedFrame(0);
        setChanged();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.internet_of_things.scanner");
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory) {
        return new ScannerFilmMenu(containerId, inventory, this, data);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("Progress");
        maxProgress = tag.contains("MaxProgress") ? tag.getInt("MaxProgress") : SCAN_DURATION_TICKS;
        scanning = tag.getBoolean("Scanning");
        selectedFrame = tag.getInt("SelectedFrame");
        queuedFrameId = tag.getString("QueuedFrameId");
        setSelectedFrame(selectedFrame);
        if (tag.hasUUID("RequesterId")) {
            requesterId = tag.getUUID("RequesterId");
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putBoolean("Scanning", scanning);
        tag.putInt("SelectedFrame", selectedFrame);
        if (!queuedFrameId.isBlank()) {
            tag.putString("QueuedFrameId", queuedFrameId);
        }
        if (requesterId != null) {
            tag.putUUID("RequesterId", requesterId);
        }
    }
}
