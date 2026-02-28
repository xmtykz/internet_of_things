package com.ykz.iot.menu;

import com.ykz.iot.blockentity.PrinterBlockEntity;
import com.ykz.iot.blockentity.PrinterSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class PrinterMenu extends AbstractContainerMenu {
    private final PrinterBlockEntity blockEntity;
    private final ContainerData data;

    public PrinterMenu(int containerId, Inventory playerInventory, PrinterBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.PRINTER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new Slot(blockEntity, PrinterSlots.PAPER, 8, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.PAPER);
            }
        });
        // Leave space for "+" between paper and dyes.
        addSlot(new Slot(blockEntity, PrinterSlots.CYAN, 42, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.CYAN_DYE);
            }
        });
        addSlot(new Slot(blockEntity, PrinterSlots.MAGENTA, 60, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.MAGENTA_DYE);
            }
        });
        addSlot(new Slot(blockEntity, PrinterSlots.YELLOW, 78, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.YELLOW_DYE);
            }
        });
        addSlot(new Slot(blockEntity, PrinterSlots.BLACK, 96, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return stack.is(net.minecraft.world.item.Items.BLACK_DYE);
            }
        });
        addSlot(new Slot(blockEntity, PrinterSlots.OUTPUT, 148, 19) {
            @Override
            public boolean mayPlace(@NotNull net.minecraft.world.item.ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }

        for (int i = 0; i < 9; ++i) {
            addSlot(new Slot(playerInventory, i, 8 + i * 18, 109));
        }

        addDataSlots(data);
    }

    public static PrinterMenu fromBuffer(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof PrinterBlockEntity printerBlockEntity) {
            return new PrinterMenu(containerId, playerInventory, printerBlockEntity, new SimpleContainerData(3));
        }
        throw new IllegalStateException("Printer block entity missing at " + pos);
    }

    public PrinterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        int max = data.get(1);
        return max <= 0 ? PrinterBlockEntity.PRINT_DURATION_TICKS : max;
    }

    public boolean isNetworkOnline() {
        return data.get(2) > 0;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return blockEntity.stillValid(player);
    }

    @Override
    public @NotNull net.minecraft.world.item.ItemStack quickMoveStack(@NotNull Player player, int index) {
        net.minecraft.world.item.ItemStack quickMoved = net.minecraft.world.item.ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            net.minecraft.world.item.ItemStack slotStack = slot.getItem();
            quickMoved = slotStack.copy();

            if (index < PrinterSlots.SLOTS) {
                if (!moveItemStackTo(slotStack, PrinterSlots.SLOTS, this.slots.size(), true)) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(slotStack, PrinterSlots.PAPER, PrinterSlots.OUTPUT, false)) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(net.minecraft.world.item.ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return quickMoved;
    }
}
