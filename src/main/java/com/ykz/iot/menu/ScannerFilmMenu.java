package com.ykz.iot.menu;

import com.ykz.iot.blockentity.ScannerBlockEntity;
import com.ykz.iot.blockentity.ScannerSlots;
import com.ykz.iot.compat.exposure.ExposureScannerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class ScannerFilmMenu extends AbstractContainerMenu {
    public static final int BUTTON_SCAN = 0;
    public static final int BUTTON_PREV = 1;
    public static final int BUTTON_NEXT = 2;

    private final ScannerBlockEntity blockEntity;
    private final ContainerData data;
    private final BlockPos blockPos;

    public ScannerFilmMenu(int containerId, Inventory playerInventory, ScannerBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.SCANNER_FILM.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.blockPos = blockEntity.getBlockPos();

        addSlot(new Slot(blockEntity, ScannerSlots.FILM, -20, 42) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return blockEntity.canPlaceItem(ScannerSlots.FILM, stack);
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 127 + row * 18));
            }
        }

        for (int i = 0; i < 9; ++i) {
            addSlot(new Slot(playerInventory, i, 8 + i * 18, 185));
        }

        addDataSlots(data);
    }

    public static ScannerFilmMenu fromBuffer(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof ScannerBlockEntity scannerBlockEntity) {
            return new ScannerFilmMenu(containerId, playerInventory, scannerBlockEntity, new SimpleContainerData(5));
        }
        throw new IllegalStateException("Scanner block entity missing at " + pos);
    }

    public ScannerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        int max = data.get(1);
        return max <= 0 ? ScannerBlockEntity.SCAN_DURATION_TICKS : max;
    }

    public boolean isNetworkOnline() {
        return data.get(2) > 0;
    }

    public int getSelectedFrame() {
        return data.get(3);
    }

    public int getTotalFrames() {
        return data.get(4);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_PREV) {
            if (!player.level().isClientSide()) {
                blockEntity.changeFrame(-1);
            }
            return true;
        }

        if (buttonId == BUTTON_NEXT) {
            if (!player.level().isClientSide()) {
                blockEntity.changeFrame(1);
            }
            return true;
        }

        return buttonId == BUTTON_SCAN;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return blockEntity.stillValid(player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack quickMoved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        quickMoved = slotStack.copy();

        if (index < ScannerSlots.SLOTS) {
            if (!moveItemStackTo(slotStack, ScannerSlots.SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(slotStack, ScannerSlots.FILM, ScannerSlots.SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return quickMoved;
    }
}
