package com.ykz.iot.network;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import com.ykz.iot.item.ModItems;
import com.ykz.iot.util.InventoryUtils;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = InternetofThings.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NetworkDebugTicker {
    private static int tickCounter = 0;
    private static final Map<UUID, Integer> LAST_NET_LEVEL = new HashMap<>();

    private static final ResourceLocation ADV_FIRST_BASE_STATION =
            ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "first_base_station");
    private static final ResourceLocation ADV_FIVE_G_COMMUNICATION =
            ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "five_g_communication");

    private NetworkDebugTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;
        if (tickCounter % 20 != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            ChunkPos cp = player.chunkPosition();
            int netLevel = IotNetworkSavedData.get(level).getNetworkLevelAt(cp);

            if (InventoryUtils.hasItem(player, ModItems.PHONE.get())) {
                Integer last = LAST_NET_LEVEL.get(player.getUUID());
                if (last == null) {
                    LAST_NET_LEVEL.put(player.getUUID(), netLevel);
                } else {
                    boolean wasConnected = last >= 1;
                    boolean nowConnected = netLevel >= 1;

                    if (wasConnected && !nowConnected) {
                        player.displayClientMessage(
                                Component.translatable("text.internet_of_things.network.disconnected").withStyle(ChatFormatting.RED),
                                true
                        );
                    } else if (!wasConnected && nowConnected) {
                        player.displayClientMessage(
                                Component.translatable("text.internet_of_things.network.connected"),
                                true
                        );

                        boolean hasStationItem = InventoryUtils.hasItem(player, ModBlocks.SIGNAL_BASE_STATION.get().asItem());
                        boolean hasFirstStationAdv = hasAdvancementDone(player, ADV_FIRST_BASE_STATION);
                        if (hasStationItem || hasFirstStationAdv) {
                            grantAdvancement(player, ADV_FIVE_G_COMMUNICATION);
                        }
                    }

                    LAST_NET_LEVEL.put(player.getUUID(), netLevel);
                }
            } else {
                LAST_NET_LEVEL.put(player.getUUID(), netLevel);
            }

            if (isHoldingBaseStation(player)) {
                player.displayClientMessage(
                        Component.translatable("msg.internet_of_things.network_level", netLevel, cp.x, cp.z),
                        true
                );
            }
        }
    }

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

    private static boolean hasAdvancementDone(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        return progress.isDone();
    }

    private static void grantAdvancement(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }
}
