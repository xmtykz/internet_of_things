package com.ykz.iot.client.trade;

import com.ykz.iot.trade.TradeCodecUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ClientTradeState {
    private static final List<TradeEntry> ENTRIES = new ArrayList<>();
    private static long nextRestockGameTime = 0L;

    private ClientTradeState() {
    }

    public static List<TradeEntry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static long nextRestockGameTime() {
        return nextRestockGameTime;
    }

    public static void applyTradeData(CompoundTag tag) {
        ENTRIES.clear();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            TradeEntry entry = TradeEntry.fromTag((CompoundTag) t);
            if (entry != null) {
                ENTRIES.add(entry);
            }
        }
        nextRestockGameTime = tag.getLong("nextRestockGameTime");

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneTradeScreen tradeScreen) {
            tradeScreen.onDataUpdated();
        }
    }

    public record TradeEntry(String id, ItemStack costA, ItemStack costB, ItemStack result, int remainingStock) {
        static TradeEntry fromTag(CompoundTag tag) {
            String id = tag.getString("id");
            if (id == null || id.isBlank()) {
                return null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return null;
            }
            ItemStack costA = TradeCodecUtil.decodeStack(mc.level.registryAccess(), tag.getCompound("costA"));
            ItemStack costB = TradeCodecUtil.decodeStack(mc.level.registryAccess(), tag.getCompound("costB"));
            ItemStack result = TradeCodecUtil.decodeStack(mc.level.registryAccess(), tag.getCompound("result"));
            int remaining = Math.max(0, tag.getInt("remainingStock"));
            return new TradeEntry(id, costA, costB, result, remaining);
        }
    }
}
