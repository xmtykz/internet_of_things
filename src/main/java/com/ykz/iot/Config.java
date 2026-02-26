package com.ykz.iot;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== 网络：基站中心信号强度（决定棱形半径）=====
    public static final ModConfigSpec.IntValue BASE_STATION_CENTER_LEVEL;

    // ===== 网络：基站信号区块衰减步长（每多少个区块衰减 1 级）=====
    public static final ModConfigSpec.IntValue BASE_STATION_DECAY_STEP;

    // ===== 网络：世界基础网络等级（所有区块统一的基础值）=====
    public static final ModConfigSpec.IntValue WORLD_BASE_NETWORK_LEVEL;

    // ===== 手机 tooltip 显示设置（COMMON）=====
    public static final ModConfigSpec.BooleanValue PHONE_TOOLTIP_UNLIMITED;
    public static final ModConfigSpec.IntValue PHONE_TOOLTIP_MAX_LEVEL;

    static {
        BUILDER.push("network");

        BASE_STATION_CENTER_LEVEL = BUILDER
                .comment("Signal base station center level. Range: 1~64. (Diamond radius depends on decay step)")
                .translation("config.internet_of_things.base_station_center_level")
                .defineInRange("baseStationCenterLevel", 4, 1, 64);

        BASE_STATION_DECAY_STEP = BUILDER
                .comment("Base station signal decay step in chunks. Every N chunks reduces 1 level. Range: 1~16.")
                .translation("config.internet_of_things.base_station_decay_step")
                .defineInRange("baseStationDecayStep", 1, 1, 16);

        WORLD_BASE_NETWORK_LEVEL = BUILDER
                .comment("World base network level applied to all chunks. Range: -64~64.")
                .translation("config.internet_of_things.world_base_network_level")
                .defineInRange("worldBaseNetworkLevel", 0, -64, 64);

        BUILDER.pop();

        BUILDER.push("phone");

        PHONE_TOOLTIP_UNLIMITED = BUILDER
                .comment("If true, phone tooltip shows real signal level without upper limit. (Max level option will be ignored)")
                .translation("config.internet_of_things.phone_tooltip_unlimited")
                .define("phoneTooltipUnlimited", false);

        PHONE_TOOLTIP_MAX_LEVEL = BUILDER
                .comment("当 手机信号显示无上限=false 时，手机工具提示框内显示的最大网络等级。范围：1~64。")
                .translation("config.internet_of_things.phone_tooltip_max_level")
                .defineInRange("phoneTooltipMaxLevel", 4, 1, 64);

        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    // 你主类和网络算法里会用到的 helper（避免到处 get()）
    public static int centerLevel() {
        int v = BASE_STATION_CENTER_LEVEL.get();
        if (v < 1) return 1;
        if (v > 64) return 64;
        return v;
    }

    public static int decayStep() {
        int v = BASE_STATION_DECAY_STEP.get();
        if (v < 1) return 1;
        if (v > 16) return 16;
        return v;
    }

    public static int worldBaseLevel() {
        // 已经 defineInRange(-64~64)，这里不额外钳制
        return WORLD_BASE_NETWORK_LEVEL.get();
    }
}