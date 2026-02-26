package com.ykz.iot.block;

import com.ykz.iot.blockentity.SignalBaseStationBlockEntity;
import com.ykz.iot.network.IotNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class SignalBaseStationBlock extends Block implements EntityBlock {

    public SignalBaseStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalBaseStationBlockEntity(pos, state);
    }

    /**
     * 统一用 onPlace 覆盖所有放置来源：玩家放置 / setblock / 结构生成等。
     * 注意：不要在 setPlacedBy 里也 add，否则玩家放置会重复计数。
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 只有从“不是本方块”变成“本方块”时才登记
        if (oldState.getBlock() != state.getBlock()) {
            IotNetworkSavedData.get(serverLevel).addStation(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);

        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 只有真正被替换成别的方块时才移除（比如被挖掉/爆炸）
        if (state.getBlock() != newState.getBlock()) {
            IotNetworkSavedData.get(serverLevel).removeStation(pos);
        }
    }
}