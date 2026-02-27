package com.ykz.iot.block;

import com.ykz.iot.blockentity.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PrinterBlock extends Block implements EntityBlock {
    public PrinterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof PrinterBlockEntity printerBlockEntity)) {
            return InteractionResult.PASS;
        }

        if (!printerBlockEntity.isNetworkOnline()) {
            player.displayClientMessage(Component.translatable("text.internet_of_things.printer.offline").withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(printerBlockEntity, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrinterBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || com.ykz.iot.blockentity.ModBlockEntities.PRINTER == null) {
            return null;
        }
        if (blockEntityType == com.ykz.iot.blockentity.ModBlockEntities.PRINTER.get()) {
            return (lvl, pos, st, be) -> PrinterBlockEntity.serverTick(lvl, pos, st, (PrinterBlockEntity) be);
        }
        return null;
    }
}
