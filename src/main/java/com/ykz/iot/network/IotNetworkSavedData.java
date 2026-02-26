package com.ykz.iot.network;

import com.ykz.iot.Config;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 维度独立保存：所有基站位置 + 基站所在区块计数。
 * 网络等级（你确认的规则）：
 * dist = |dx| + |dz|
 * 单基站贡献 = max(0, centerLevel - dist)
 * 多基站叠加 = 求和（无上限）
 */
public class IotNetworkSavedData extends SavedData {
    private static final String DATA_NAME = "internet_of_things_network";
    private static final String TAG_STATIONS = "Stations";

    // 保存“基站方块位置”（去重、用于存盘）
    private final LongOpenHashSet stationPositions = new LongOpenHashSet();

    // chunkPosLong -> count（同一 chunk 多个基站叠加）
    private final Long2IntOpenHashMap stationChunkCounts = new Long2IntOpenHashMap();

    public static IotNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(IotNetworkSavedData::new, IotNetworkSavedData::load),
                DATA_NAME
        );
    }

    public static IotNetworkSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        IotNetworkSavedData data = new IotNetworkSavedData();

        long[] arr = tag.getLongArray(TAG_STATIONS);
        for (long posLong : arr) {
            data.stationPositions.add(posLong);
            data.incChunkCount(BlockPos.of(posLong));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLongArray(TAG_STATIONS, stationPositions.toLongArray());
        return tag;
    }

    /** 放置时调用：记录基站（去重），并更新 chunk 计数 */
    public void addStation(BlockPos pos) {
        long key = pos.asLong();
        if (stationPositions.add(key)) {
            incChunkCount(pos);
            setDirty();
        }
    }

    /** 破坏时调用：移除基站，并更新 chunk 计数 */
    public void removeStation(BlockPos pos) {
        long key = pos.asLong();
        if (stationPositions.remove(key)) {
            decChunkCount(pos);
            setDirty();
        }
    }

    /** BlockEntity.onLoad 的保险调用：如果没登记过就补登记（不会重复） */
    public void ensureStation(BlockPos pos) {
        addStation(pos);
    }

    /** 获取某个区块的网络等级（菱形规则 + 叠加） */
    public int getNetworkLevelAt(ChunkPos playerChunk) {
        int basePower = Config.centerLevel();  // 1..64（中心区块贡献）
        int step = Config.decayStep();         // 1..16（每 step 个区块衰减 1 级）

        int cx = playerChunk.x;
        int cz = playerChunk.z;

        int total = 0;

        // 性能优化：不再扫描超大菱形区域（step 最大 16 时半径会很大）
        // 直接遍历所有“基站所在区块计数”来计算贡献，复杂度随基站数量变化，更稳。
        for (var entry : stationChunkCounts.long2IntEntrySet()) {
            long packed = entry.getLongKey();
            int count = entry.getIntValue();
            if (count == 0) continue;

            // ChunkPos.asLong 的解包：x 在低 32 位，z 在高 32 位
            int sx = (int) packed;
            int sz = (int) (packed >> 32);

            int dist = Math.abs(sx - cx) + Math.abs(sz - cz); // 菱形（曼哈顿距离）
            int effDist = dist / step;                        // 每 step 个区块衰减 1 级
            int contrib = basePower - effDist;                // dist=0..step-1 -> basePower

            if (contrib > 0) {
                total += contrib * count;
            }
        }

        // 世界基础网络等级：对所有区块统一叠加（可为负）
        total += Config.worldBaseLevel();

        return total;
    }

    private void incChunkCount(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        long ck = ChunkPos.asLong(cp.x, cp.z);
        stationChunkCounts.addTo(ck, 1);
    }

    private void decChunkCount(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        long ck = ChunkPos.asLong(cp.x, cp.z);
        int now = stationChunkCounts.addTo(ck, -1);
        if (now <= 0) stationChunkCounts.remove(ck);
    }
}