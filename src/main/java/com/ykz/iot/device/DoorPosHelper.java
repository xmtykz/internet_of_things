package com.ykz.iot.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class DoorPosHelper {
    private DoorPosHelper() {}

    public static BlockPos normalizeDoorBasePos(Level level, BlockPos clickedPos) {
        BlockState state = level.getBlockState(clickedPos);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return clickedPos.below();
        }
        return clickedPos;
    }
}
