package com.ykz.iot.blockentity;

import com.ykz.iot.network.IotNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SignalBaseStationBlockEntity extends BlockEntity {

    public SignalBaseStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNAL_BASE_STATION.get(), pos, state);
    }

    /**
     * 这一步是“保险”：如果你把网络系统加到一个已有存档里，
     * 老世界里已经存在的基站在区块第一次加载时会自动补登记一次（不会重复，因为 SavedData 做了去重）。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        IotNetworkSavedData.get(serverLevel).ensureStation(worldPosition);
    }
}