package com.ykz.iot.blockentity;

import com.ykz.iot.compat.exposure.ExposurePrintBridge;
import com.ykz.iot.network.IotNetworkSavedData;
import com.ykz.iot.menu.PrinterMenu;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PrinterBlockEntity extends BaseContainerBlockEntity {
    public static final int PRINT_DURATION_TICKS = 200;
    private static final int MAX_PIXELS = 2048 * 2048;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> isNetworkOnline() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(PrinterSlots.SLOTS, ItemStack.EMPTY);
    private int progress = 0;
    private int maxProgress = PRINT_DURATION_TICKS;
    private boolean printing = false;
    private int queuedWidth = 0;
    private int queuedHeight = 0;
    private byte[] queuedPixels = new byte[0];
    private String queuedPaletteId = "exposure:map_colors_plus";
    private long queuedTimestamp = 0L;
    private UUID requesterId;
    private String requesterName = "";

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrinterBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (be.printing) {
            be.progress = Mth.clamp(be.progress + 1, 0, be.maxProgress);
            if (be.progress >= be.maxProgress) {
                be.finishPrint();
            } else {
                be.setChanged();
            }
        }
    }

    public ContainerData getData() {
        return data;
    }

    public boolean isPrinting() {
        return printing;
    }

    public void setPrinting(boolean printing) {
        this.printing = printing;
        if (!printing) {
            this.progress = 0;
            clearQueuedImage();
        }
        setChanged();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        setChanged();
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    public boolean isNetworkOnline() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return IotNetworkSavedData.get(serverLevel).getNetworkLevelAt(serverLevel.getChunkAt(getBlockPos()).getPos()) >= 1;
    }

    public static boolean isNetworkOnline(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkAt(pos);
        return IotNetworkSavedData.get(level).getNetworkLevelAt(chunk.getPos()) >= 1;
    }

    public boolean hasEnoughDyes() {
        return isCyan(getItem(PrinterSlots.CYAN))
                && isMagenta(getItem(PrinterSlots.MAGENTA))
                && isYellow(getItem(PrinterSlots.YELLOW))
                && isBlack(getItem(PrinterSlots.BLACK));
    }

    public boolean hasPaper() {
        return isPaper(getItem(PrinterSlots.PAPER));
    }

    public boolean outputIsEmpty() {
        return getItem(PrinterSlots.OUTPUT).isEmpty();
    }

    public void consumeOneSetOfDyes() {
        getItem(PrinterSlots.PAPER).shrink(1);
        getItem(PrinterSlots.CYAN).shrink(1);
        getItem(PrinterSlots.MAGENTA).shrink(1);
        getItem(PrinterSlots.YELLOW).shrink(1);
        getItem(PrinterSlots.BLACK).shrink(1);
        setChanged();
    }

    public StartCheck checkCanStart() {
        if (!isNetworkOnline()) {
            return StartCheck.OFFLINE;
        }
        if (isPrinting()) {
            return StartCheck.BUSY;
        }
        if (!outputIsEmpty()) {
            return StartCheck.OUTPUT_OCCUPIED;
        }
        if (!hasPaper()) {
            return StartCheck.INSUFFICIENT_PAPER;
        }
        if (!hasEnoughDyes()) {
            return StartCheck.INSUFFICIENT_DYES;
        }
        return StartCheck.OK;
    }

    public StartCheck beginRemotePrint(ServerPlayer requester,
                                       int width,
                                       int height,
                                       byte[] pixels,
                                       String paletteId,
                                       long unixTimestamp) {
        StartCheck check = checkCanStart();
        if (check != StartCheck.OK) {
            return check;
        }
        long pixelCount = (long) width * height;
        if (width <= 0 || height <= 0 || pixelCount <= 0 || pixelCount > MAX_PIXELS
                || pixels == null || pixels.length != pixelCount) {
            return StartCheck.INVALID_IMAGE;
        }

        queuedWidth = width;
        queuedHeight = height;
        queuedPixels = pixels.clone();
        queuedPaletteId = (paletteId == null || paletteId.isBlank()) ? "exposure:map_colors_plus" : paletteId;
        queuedTimestamp = unixTimestamp > 0 ? unixTimestamp : Instant.now().getEpochSecond();
        requesterId = requester.getUUID();
        requesterName = requester.getScoreboardName();

        consumeOneSetOfDyes();
        this.maxProgress = PRINT_DURATION_TICKS;
        this.progress = 0;
        this.printing = true;
        setChanged();
        return StartCheck.OK;
    }

    private void finishPrint() {
        this.printing = false;
        this.progress = 0;

        if (!(level instanceof ServerLevel serverLevel)) {
            clearQueuedImage();
            setChanged();
            return;
        }

        if (!outputIsEmpty()) {
            sendRequesterMessage(Component.translatable("text.internet_of_things.printer.output_occupied").withStyle(net.minecraft.ChatFormatting.RED));
            clearQueuedImage();
            setChanged();
            return;
        }

        @Nullable ServerPlayer requester = getRequester(serverLevel);
        if (requester == null) {
            clearQueuedImage();
            setChanged();
            return;
        }

        String exposureId = createExposureId(requesterName, serverLevel.getGameTime());
        ItemStack printed = ExposurePrintBridge.createPrintedPhotograph(
                requester, exposureId, queuedWidth, queuedHeight, queuedPixels, queuedPaletteId, queuedTimestamp);
        if (printed.isEmpty()) {
            sendRequesterMessage(Component.translatable("text.internet_of_things.printer.failed").withStyle(net.minecraft.ChatFormatting.RED));
            clearQueuedImage();
            setChanged();
            return;
        }

        setItem(PrinterSlots.OUTPUT, printed);
        sendRequesterMessage(Component.translatable("text.internet_of_things.printer.done"));
        clearQueuedImage();
        setChanged();
    }

    private @Nullable ServerPlayer getRequester(ServerLevel level) {
        return requesterId == null ? null : level.getServer().getPlayerList().getPlayer(requesterId);
    }

    private void sendRequesterMessage(Component message) {
        if (!(level instanceof ServerLevel serverLevel) || requesterId == null) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(requesterId);
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    private String createExposureId(String name, long gameTime) {
        String safe = (name == null || name.isBlank()) ? "player" : name.replaceAll("[^A-Za-z0-9-]", "-");
        int salt = ThreadLocalRandom.current().nextInt(1000, 9999);
        return safe + "_print_" + gameTime + "_" + salt;
    }

    private void clearQueuedImage() {
        queuedWidth = 0;
        queuedHeight = 0;
        queuedPixels = new byte[0];
        queuedPaletteId = "exposure:map_colors_plus";
        queuedTimestamp = 0L;
        requesterId = null;
        requesterName = "";
    }

    public enum StartCheck {
        OK,
        OFFLINE,
        BUSY,
        OUTPUT_OCCUPIED,
        INSUFFICIENT_PAPER,
        INSUFFICIENT_DYES,
        INVALID_IMAGE
    }

    @Override
    public int getContainerSize() {
        return PrinterSlots.SLOTS;
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
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        if (slot == PrinterSlots.OUTPUT) {
            return false;
        }
        return switch (slot) {
            case PrinterSlots.PAPER -> isPaper(stack);
            case PrinterSlots.CYAN -> isCyan(stack);
            case PrinterSlots.MAGENTA -> isMagenta(stack);
            case PrinterSlots.YELLOW -> isYellow(stack);
            case PrinterSlots.BLACK -> isBlack(stack);
            default -> false;
        };
    }

    private static boolean isPaper(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.PAPER);
    }

    private static boolean isCyan(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.CYAN_DYE);
    }

    private static boolean isMagenta(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.MAGENTA_DYE);
    }

    private static boolean isYellow(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.YELLOW_DYE);
    }

    private static boolean isBlack(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.BLACK_DYE);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.internet_of_things.printer");
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory) {
        return new PrinterMenu(containerId, inventory, this, data);
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
        maxProgress = tag.contains("MaxProgress") ? tag.getInt("MaxProgress") : PRINT_DURATION_TICKS;
        printing = tag.getBoolean("Printing");
        requesterName = tag.getString("RequesterName");
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
        tag.putBoolean("Printing", printing);
        if (requesterId != null) {
            tag.putUUID("RequesterId", requesterId);
        }
        if (!requesterName.isBlank()) {
            tag.putString("RequesterName", requesterName);
        }
    }
}
