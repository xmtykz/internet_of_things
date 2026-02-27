package com.ykz.iot.device;

import com.ykz.iot.network.IotNetworkSavedData;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SmartDeviceSavedData extends SavedData {
    private static final String DATA_NAME = "internet_of_things_devices";
    private static final String TAG_DEVICES = "Devices";

    private final Map<UUID, DoorDevice> devices = new HashMap<>();
    private final Long2ObjectOpenHashMap<UUID> byPos = new Long2ObjectOpenHashMap<>();

    public static SmartDeviceSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SmartDeviceSavedData::new, SmartDeviceSavedData::load),
                DATA_NAME
        );
    }

    public static long currentScheduleTime(ServerLevel level) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return level.getDayTime();
        }
        return level.getGameTime();
    }

    public static SmartDeviceSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        SmartDeviceSavedData data = new SmartDeviceSavedData();
        ListTag list = tag.getList(TAG_DEVICES, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag ct = (CompoundTag) t;
            DoorDevice d = DoorDevice.fromTag(ct);
            data.devices.put(d.id, d);
            data.byPos.put(d.pos, d.id);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (DoorDevice d : devices.values()) {
            list.add(d.toTag());
        }
        tag.put(TAG_DEVICES, list);
        return tag;
    }

    public Optional<DoorDevice> findByPos(BlockPos pos) {
        UUID id = byPos.get(pos.asLong());
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(devices.get(id));
    }

    public Optional<DoorDevice> findById(UUID id) {
        return Optional.ofNullable(devices.get(id));
    }

    public DoorDevice installAt(ServerLevel level, BlockPos pos, Block block, String ownerName) {
        String normalizedOwner = normalizeOwner(ownerName);
        long key = pos.asLong();
        UUID existing = byPos.get(key);
        if (existing != null) {
            DoorDevice d = devices.get(existing);
            if (d != null) {
                d.offline = false;
                d.blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (d.ownerName.isBlank() && !normalizedOwner.isBlank()) {
                    d.ownerName = normalizedOwner;
                }
                BlockState state = level.getBlockState(pos);
                d.theoreticalOpen = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
                d.pendingApply = false;
                setDirty();
                return d;
            }
        }

        String unique = nextUniqueName("Door", null);
        DoorDevice d = new DoorDevice(UUID.randomUUID(), key);
        d.name = unique;
        d.ownerName = normalizedOwner;
        d.blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        BlockState state = level.getBlockState(pos);
        d.theoreticalOpen = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);

        devices.put(d.id, d);
        byPos.put(key, d.id);
        setDirty();
        return d;
    }

    public boolean removeDevice(UUID id) {
        DoorDevice d = devices.remove(id);
        if (d == null) {
            return false;
        }
        byPos.remove(d.pos);
        setDirty();
        return true;
    }

    public boolean renameDevice(UUID id, String newName) {
        DoorDevice d = devices.get(id);
        if (d == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.length() > 16) {
            trimmed = trimmed.substring(0, 16);
        }
        if (!isNameAvailable(trimmed, id)) {
            return false;
        }
        d.name = trimmed;
        setDirty();
        return true;
    }

    public boolean setSchedule(UUID id, int openTick, int closeTick, boolean repeat, boolean enabled, long gameTimeNow, TimeUnitMode unit) {
        DoorDevice d = devices.get(id);
        if (d == null) {
            return false;
        }
        d.openTick = clampDayTick(openTick);
        d.closeTick = clampDayTick(closeTick);
        d.scheduleEnabled = enabled;
        d.repeat = repeat;
        d.timeUnitMode = unit;

        if (enabled) {
            if (repeat) {
                int currentDayTick = (int) (gameTimeNow % 24000L);
                boolean shouldOpen = isInOpenWindow(currentDayTick, d.openTick, d.closeTick);
                if (d.theoreticalOpen != shouldOpen) {
                    d.theoreticalOpen = shouldOpen;
                    d.pendingApply = true;
                }
                d.nextOpenAbs = -1;
                d.nextCloseAbs = -1;
            } else {
                d.nextOpenAbs = nextAbs(gameTimeNow, d.openTick);
                d.nextCloseAbs = nextAbs(gameTimeNow, d.closeTick);
                if (d.nextCloseAbs <= d.nextOpenAbs) {
                    d.nextCloseAbs += 24000L;
                }
            }
        } else {
            d.clearSchedule();
        }

        setDirty();
        return true;
    }

    public boolean clearSchedule(UUID id) {
        DoorDevice d = devices.get(id);
        if (d == null) {
            return false;
        }
        d.clearSchedule();
        setDirty();
        return true;
    }

    public boolean toggleDevice(ServerLevel level, UUID id) {
        DoorDevice d = devices.get(id);
        if (d == null || d.offline || d.scheduleEnabled) {
            return false;
        }
        boolean online = isNetworkOnline(level, d.pos);
        if (!online) {
            return false;
        }

        d.theoreticalOpen = !d.theoreticalOpen;
        d.pendingApply = true;
        applyIfLoaded(level, d);
        setDirty();
        return true;
    }

    public boolean consumeInstalledModuleOnRemoval(BlockPos pos) {
        Optional<DoorDevice> od = findByPos(pos);
        if (od.isEmpty()) {
            return false;
        }
        DoorDevice d = od.get();
        if (d.offline) {
            return false;
        }
        d.offline = true;
        d.pendingApply = false;
        setDirty();
        return true;
    }

    public static String formatOwnedName(String ownerName, String deviceName) {
        return formatOwnedName(ownerName, deviceName, "");
    }

    public static String formatOwnedName(String ownerName, String deviceName, String fallbackOwnerName) {
        String device = deviceName == null ? "" : deviceName.trim();
        if (device.isBlank()) {
            device = "Door";
        }
        String owner = normalizeOwner(ownerName);
        if (owner.isBlank()) {
            owner = normalizeOwner(fallbackOwnerName);
        }
        if (owner.isBlank()) {
            return device;
        }
        return owner + "\u7684" + device;
    }

    public List<DeviceSnapshot> buildSnapshots(ServerLevel level) {
        List<DeviceSnapshot> out = new ArrayList<>();
        for (DoorDevice d : devices.values()) {
            out.add(snapshot(level, d));
        }
        out.sort(Comparator
                .comparing(DeviceSnapshot::blockId)
                .thenComparingInt(s -> s.status.order)
                .thenComparing(s -> s.name.toLowerCase()));
        return out;
    }

    public Optional<DeviceSnapshot> buildSnapshotByPos(ServerLevel level, BlockPos pos, boolean allowUninstalled) {
        Optional<DoorDevice> od = findByPos(pos);
        if (od.isPresent()) {
            return Optional.of(snapshot(level, od.get()));
        }
        if (!allowUninstalled) {
            return Optional.empty();
        }
        DeviceSnapshot empty = new DeviceSnapshot(
                null,
                "",
                "",
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                pos.asLong(),
                DeviceStatus.UNLOADED,
                false,
                false,
                false,
                false,
                0,
                0,
                false,
                false,
                TimeUnitMode.TICKS
        );
        return Optional.of(empty);
    }

    public Optional<DeviceSnapshot> buildSnapshotById(ServerLevel level, UUID id) {
        DoorDevice d = devices.get(id);
        if (d == null) {
            return Optional.empty();
        }
        return Optional.of(snapshot(level, d));
    }

    public void tick(ServerLevel level, long gameTime) {
        boolean dirty = false;
        List<UUID> staleUpperDuplicates = new ArrayList<>();
        for (DoorDevice d : devices.values()) {
            BlockPos pos = BlockPos.of(d.pos);
            boolean loaded = level.isLoaded(pos);

            if (loaded) {
                BlockState currentState = level.getBlockState(pos);
                if (currentState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && currentState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                    BlockPos basePos = pos.below();
                    long baseKey = basePos.asLong();
                    UUID holder = byPos.get(baseKey);
                    if (holder != null && !holder.equals(d.id)) {
                        staleUpperDuplicates.add(d.id);
                        dirty = true;
                        continue;
                    }
                    byPos.remove(d.pos);
                    d.pos = baseKey;
                    byPos.put(baseKey, d.id);
                    pos = basePos;
                    loaded = level.isLoaded(pos);
                    dirty = true;
                }
            }

            if (loaded && !isNetworkableDoor(level, pos)) {
                if (!d.offline) {
                    d.offline = true;
                    d.pendingApply = false;
                    dirty = true;
                }
            }

            if (d.offline) {
                continue;
            }

            if (loaded && !d.pendingApply) {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(BlockStateProperties.OPEN)) {
                    boolean actualOpen = state.getValue(BlockStateProperties.OPEN);
                    if (actualOpen != d.theoreticalOpen) {
                        d.theoreticalOpen = actualOpen;
                        dirty = true;
                    }
                }
            }

            if (d.scheduleEnabled) {
                if (d.repeat) {
                    int currentDayTick = (int) (gameTime % 24000L);
                    boolean shouldOpen = isInOpenWindow(currentDayTick, d.openTick, d.closeTick);
                    if (d.theoreticalOpen != shouldOpen) {
                        d.theoreticalOpen = shouldOpen;
                        d.pendingApply = true;
                        dirty = true;
                    }
                } else {
                    while (true) {
                        long next = nextEvent(d.nextOpenAbs, d.nextCloseAbs);
                        if (next < 0 || gameTime < next) {
                            break;
                        }

                        if (d.nextOpenAbs >= 0 && d.nextOpenAbs == next) {
                            d.theoreticalOpen = true;
                            d.pendingApply = true;
                            d.nextOpenAbs = -1;
                            dirty = true;
                        }
                        if (d.nextCloseAbs >= 0 && d.nextCloseAbs == next) {
                            d.theoreticalOpen = false;
                            d.pendingApply = true;
                            d.nextCloseAbs = -1;
                            dirty = true;
                        }
                    }
                    if (d.nextOpenAbs < 0 && d.nextCloseAbs < 0) {
                        d.clearSchedule();
                        dirty = true;
                    }
                }
            }

            if (d.pendingApply) {
                boolean applied = applyIfLoaded(level, d);
                if (applied) {
                    dirty = true;
                }
            }
        }

        for (UUID id : staleUpperDuplicates) {
            DoorDevice removed = devices.remove(id);
            if (removed != null) {
                byPos.remove(removed.pos);
            }
        }

        if (dirty) {
            setDirty();
        }
    }

    private DeviceSnapshot snapshot(ServerLevel level, DoorDevice d) {
        BlockPos pos = BlockPos.of(d.pos);
        boolean loaded = level.isLoaded(pos);
        boolean online = !d.offline && isNetworkOnline(level, d.pos);

        DeviceStatus status;
        if (!online || d.offline) {
            status = DeviceStatus.OFFLINE;
        } else if (loaded) {
            status = DeviceStatus.LOADED;
        } else {
            status = DeviceStatus.UNLOADED;
        }

        return new DeviceSnapshot(
                d.id,
                d.name,
                d.ownerName,
                d.blockId,
                d.pos,
                status,
                d.theoreticalOpen,
                d.scheduleEnabled,
                d.repeat,
                d.pendingApply,
                d.openTick,
                d.closeTick,
                loaded,
                online,
                d.timeUnitMode
        );
    }

    private boolean applyIfLoaded(ServerLevel level, DoorDevice d) {
        BlockPos pos = BlockPos.of(d.pos);
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!isNetworkableDoor(level, pos)) {
            d.offline = true;
            d.pendingApply = false;
            return true;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return false;
        }

        if (state.getValue(BlockStateProperties.OPEN) != d.theoreticalOpen) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, d.theoreticalOpen), Block.UPDATE_ALL);
        }
        d.pendingApply = false;
        return true;
    }

    private static boolean isNetworkableDoor(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(IotTags.NETWORKABLE_DOORS);
    }

    private static boolean isNetworkOnline(ServerLevel level, long posLong) {
        BlockPos pos = BlockPos.of(posLong);
        ChunkPos cp = new ChunkPos(pos);
        return IotNetworkSavedData.get(level).getNetworkLevelAt(cp) >= 1;
    }

    private static String normalizeOwner(String ownerName) {
        if (ownerName == null) {
            return "";
        }
        return ownerName.trim();
    }

    private String nextUniqueName(String base, UUID selfId) {
        String tryName = base;
        int idx = 2;
        while (!isNameAvailable(tryName, selfId)) {
            tryName = base + idx;
            idx++;
        }
        return tryName;
    }

    private boolean isNameAvailable(String name, UUID selfId) {
        String lower = name.toLowerCase();
        for (DoorDevice d : devices.values()) {
            if (selfId != null && d.id.equals(selfId)) {
                continue;
            }
            if (d.name.toLowerCase().equals(lower)) {
                return false;
            }
        }
        return true;
    }

    private static int clampDayTick(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 23999) {
            return 23999;
        }
        return v;
    }

    private static long nextAbs(long now, int dayTick) {
        long day = now / 24000L;
        long cand = day * 24000L + dayTick;
        if (cand <= now) {
            cand += 24000L;
        }
        return cand;
    }

    private static long nextEvent(long open, long close) {
        if (open < 0) return close;
        if (close < 0) return open;
        return Math.min(open, close);
    }

    private static boolean isInOpenWindow(int currentDayTick, int openTick, int closeTick) {
        if (openTick == closeTick) {
            return false;
        }
        if (openTick < closeTick) {
            return currentDayTick >= openTick && currentDayTick < closeTick;
        }
        return currentDayTick >= openTick || currentDayTick < closeTick;
    }

    public enum DeviceStatus {
        LOADED(0),
        UNLOADED(1),
        OFFLINE(2);

        public final int order;

        DeviceStatus(int order) {
            this.order = order;
        }
    }

    public enum TimeUnitMode {
        TICKS,
        SECONDS
    }

    public static final class DeviceSnapshot {
        public final UUID id;
        public final String name;
        public final String ownerName;
        public final String blockId;
        public final long pos;
        public final DeviceStatus status;
        public final boolean theoreticalOpen;
        public final boolean scheduleEnabled;
        public final boolean repeat;
        public final boolean pendingApply;
        public final int openTick;
        public final int closeTick;
        public final boolean loaded;
        public final boolean online;
        public final TimeUnitMode unitMode;

        public DeviceSnapshot(UUID id, String name, String ownerName, String blockId, long pos, DeviceStatus status, boolean theoreticalOpen,
                              boolean scheduleEnabled, boolean repeat, boolean pendingApply, int openTick, int closeTick,
                              boolean loaded, boolean online, TimeUnitMode unitMode) {
            this.id = id;
            this.name = name;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.blockId = blockId;
            this.pos = pos;
            this.status = status;
            this.theoreticalOpen = theoreticalOpen;
            this.scheduleEnabled = scheduleEnabled;
            this.repeat = repeat;
            this.pendingApply = pendingApply;
            this.openTick = openTick;
            this.closeTick = closeTick;
            this.loaded = loaded;
            this.online = online;
            this.unitMode = unitMode;
        }

        public String blockId() {
            return blockId;
        }

        public String ownedName() {
            return SmartDeviceSavedData.formatOwnedName(ownerName, name);
        }

        public String ownedName(String fallbackOwnerName) {
            return SmartDeviceSavedData.formatOwnedName(ownerName, name, fallbackOwnerName);
        }

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            if (id != null) {
                t.putUUID("id", id);
            }
            t.putString("name", name);
            t.putString("ownerName", ownerName);
            t.putString("blockId", blockId);
            t.putLong("pos", pos);
            t.putString("status", status.name());
            t.putBoolean("theoreticalOpen", theoreticalOpen);
            t.putBoolean("scheduleEnabled", scheduleEnabled);
            t.putBoolean("repeat", repeat);
            t.putBoolean("pendingApply", pendingApply);
            t.putInt("openTick", openTick);
            t.putInt("closeTick", closeTick);
            t.putBoolean("loaded", loaded);
            t.putBoolean("online", online);
            t.putString("unitMode", unitMode.name());
            return t;
        }

        public static DeviceSnapshot fromTag(CompoundTag t) {
            UUID id = t.hasUUID("id") ? t.getUUID("id") : null;
            return new DeviceSnapshot(
                    id,
                    t.getString("name"),
                    t.contains("ownerName") ? t.getString("ownerName") : "",
                    t.getString("blockId"),
                    t.getLong("pos"),
                    DeviceStatus.valueOf(t.getString("status")),
                    t.getBoolean("theoreticalOpen"),
                    t.getBoolean("scheduleEnabled"),
                    t.getBoolean("repeat"),
                    t.getBoolean("pendingApply"),
                    t.getInt("openTick"),
                    t.getInt("closeTick"),
                    t.getBoolean("loaded"),
                    t.getBoolean("online"),
                    TimeUnitMode.valueOf(t.getString("unitMode"))
            );
        }
    }

    public static final class DoorDevice {
        public final UUID id;
        public long pos;

        public String name = "Door";
        public String ownerName = "";
        public String blockId = "minecraft:oak_door";
        public boolean offline = false;

        public boolean theoreticalOpen = false;
        public boolean pendingApply = false;

        public boolean scheduleEnabled = false;
        public boolean repeat = false;
        public int openTick = 0;
        public int closeTick = 0;
        public long nextOpenAbs = -1;
        public long nextCloseAbs = -1;
        public long lastOpenDay = -1;
        public long lastCloseDay = -1;
        public TimeUnitMode timeUnitMode = TimeUnitMode.TICKS;

        private DoorDevice(UUID id, long pos) {
            this.id = id;
            this.pos = pos;
        }

        public void clearSchedule() {
            scheduleEnabled = false;
            repeat = false;
            openTick = 0;
            closeTick = 0;
            nextOpenAbs = -1;
            nextCloseAbs = -1;
            lastOpenDay = -1;
            lastCloseDay = -1;
        }

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putLong("pos", pos);
            t.putString("name", name);
            t.putString("ownerName", ownerName);
            t.putString("blockId", blockId);
            t.putBoolean("offline", offline);
            t.putBoolean("theoreticalOpen", theoreticalOpen);
            t.putBoolean("pendingApply", pendingApply);
            t.putBoolean("scheduleEnabled", scheduleEnabled);
            t.putBoolean("repeat", repeat);
            t.putInt("openTick", openTick);
            t.putInt("closeTick", closeTick);
            t.putLong("nextOpenAbs", nextOpenAbs);
            t.putLong("nextCloseAbs", nextCloseAbs);
            t.putLong("lastOpenDay", lastOpenDay);
            t.putLong("lastCloseDay", lastCloseDay);
            t.putString("timeUnitMode", timeUnitMode.name());
            return t;
        }

        public static DoorDevice fromTag(CompoundTag t) {
            DoorDevice d = new DoorDevice(t.getUUID("id"), t.getLong("pos"));
            d.name = t.getString("name");
            if (t.contains("ownerName")) {
                d.ownerName = t.getString("ownerName");
            }
            d.blockId = t.getString("blockId");
            d.offline = t.getBoolean("offline");
            d.theoreticalOpen = t.getBoolean("theoreticalOpen");
            d.pendingApply = t.getBoolean("pendingApply");
            d.scheduleEnabled = t.getBoolean("scheduleEnabled");
            d.repeat = t.getBoolean("repeat");
            d.openTick = t.getInt("openTick");
            d.closeTick = t.getInt("closeTick");
            d.nextOpenAbs = t.getLong("nextOpenAbs");
            d.nextCloseAbs = t.getLong("nextCloseAbs");
            d.lastOpenDay = t.getLong("lastOpenDay");
            d.lastCloseDay = t.getLong("lastCloseDay");
            if (t.contains("timeUnitMode")) {
                d.timeUnitMode = TimeUnitMode.valueOf(t.getString("timeUnitMode"));
            }
            return d;
        }
    }
}
