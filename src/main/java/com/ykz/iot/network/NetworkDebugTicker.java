package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import com.ykz.iot.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = InternetofThings.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NetworkDebugTicker {
    private static int tickCounter = 0;

    // 记录玩家上一次的网络等级（用于判断“断开/连接”）
    private static final Map<UUID, Integer> LAST_NET_LEVEL = new HashMap<>();

    // 进度ID
    private static final ResourceLocation ADV_FIRST_BASE_STATION =
            ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "first_base_station");
    private static final ResourceLocation ADV_FIVE_G_COMMUNICATION =
            ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "five_g_communication");

    private NetworkDebugTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;

        // 每 20 tick = 1 秒
        if (tickCounter % 20 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            ChunkPos cp = player.chunkPosition();

            int netLevel = IotNetworkSavedData.get(level).getNetworkLevelAt(cp);

            // ====== 断网/联网提示：只要背包或快捷栏里有手机，就检测状态变化 ======
            if (hasItemInInventory(player, ModItems.PHONE.get())) {
                Integer last = LAST_NET_LEVEL.get(player.getUUID());
                if (last == null) {
                    // 第一次记录不提示（避免刚进世界就弹）
                    LAST_NET_LEVEL.put(player.getUUID(), netLevel);
                } else {
                    boolean wasConnected = last >= 1;
                    boolean nowConnected = netLevel >= 1;

                    // 如果上次提示是>=1而这次<1则红色提示“网络已断开”，反之则普通提示“网络已连接”
                    if (wasConnected && !nowConnected) {
                        // ActionBar：第二个参数 true = 不进聊天框
                        player.displayClientMessage(
                                Component.literal("网络已断开").withStyle(ChatFormatting.RED),
                                true
                        );
                    } else if (!wasConnected && nowConnected) {
                        player.displayClientMessage(
                                Component.literal("网络已连接"),
                                true
                        );

                        // ====== 联网瞬间授予进度：5G通讯（连接到互联网）======
                        // 只在“断网 -> 联网”的瞬间触发一次
                        // 为了符合你的树结构：需要先有“第一座基站”（进度完成或背包里确实有基站）
                        boolean hasStationItem = hasItemInInventory(player, ModBlocks.SIGNAL_BASE_STATION.get().asItem());
                        boolean hasFirstStationAdv = hasAdvancementDone(player, ADV_FIRST_BASE_STATION);
                        if (hasStationItem || hasFirstStationAdv) {
                            grantAdvancement(player, ADV_FIVE_G_COMMUNICATION);
                        }
                    }

                    LAST_NET_LEVEL.put(player.getUUID(), netLevel);
                }
            } else {
                // 没手机就不触发“断开/连接”提示，但仍更新记录，避免之后拿到手机时出现奇怪跳变
                LAST_NET_LEVEL.put(player.getUUID(), netLevel);
            }

            // ====== 网络等级调试提示：只有手持信号基站时才每秒提示 ======
            if (isHoldingBaseStation(player)) {
                // ActionBar：第二个参数 true = 不进聊天框
                player.displayClientMessage(
                        Component.translatable("msg.internet_of_things.network_level", netLevel, cp.x, cp.z),
                        true
                );
            }
        }
    }

    // 玩家退出时清理记录，避免 map 越积越大
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_NET_LEVEL.remove(player.getUUID());
        }
    }

    private static boolean isHoldingBaseStation(ServerPlayer player) {
        Item baseStationItem = ModBlocks.SIGNAL_BASE_STATION.get().asItem();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(baseStationItem) || off.is(baseStationItem);
    }

    private static boolean hasItemInInventory(ServerPlayer player, Item item) {
        // 背包+快捷栏都包含在这里
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        // 副手也算（可选：你没要求，但一般认为“身上有手机”也应包含副手）
        ItemStack off = player.getOffhandItem();
        return !off.isEmpty() && off.is(item);
    }

    private static boolean hasAdvancementDone(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv == null) return false;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        return progress.isDone();
    }

    private static void grantAdvancement(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;

        // 将该进度的所有未完成 criterion 全部 award（impossible 的进度也能这么给）
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }
}