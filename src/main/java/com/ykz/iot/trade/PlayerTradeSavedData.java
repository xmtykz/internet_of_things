package com.ykz.iot.trade;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerTradeSavedData extends SavedData {
    private static final String DATA_NAME = "internet_of_things_player_trades";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_TRADE_ONLINE = "trade_online";

    private final Map<UUID, PlayerTradeBook> books = new Object2ObjectLinkedOpenHashMap<>();

    public static PlayerTradeSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available");
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerTradeSavedData::new, PlayerTradeSavedData::load),
                DATA_NAME
        );
    }

    public static PlayerTradeSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        PlayerTradeSavedData data = new PlayerTradeSavedData();
        ListTag list = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag pt = (CompoundTag) t;
            if (!pt.hasUUID("player")) {
                continue;
            }
            UUID playerId = pt.getUUID("player");
            data.books.put(playerId, PlayerTradeBook.fromTag(pt.getCompound("book"), lookup));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerTradeBook> entry : books.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", entry.getKey());
            playerTag.put("book", entry.getValue().toTag(lookup));
            list.add(playerTag);
        }
        tag.put(TAG_PLAYERS, list);
        return tag;
    }

    public void recordOnlineVillagerTrade(ServerPlayer player, AbstractVillager villager, MerchantOffer offer) {
        String tradeId = TradeCodecUtil.tradeId(offer);
        if (isVillagerTradeSeen(villager, tradeId)) {
            return;
        }
        markVillagerTradeSeen(villager, tradeId);

        PlayerTradeBook book = getOrCreateBook(player.getUUID());
        int addMax = Math.max(1, offer.getMaxUses());
        TradeEntry existing = book.entries.get(tradeId);

        if (existing == null) {
            TradeEntry created = new TradeEntry(
                    tradeId,
                    offer.getBaseCostA().copy(),
                    offer.getCostB().copy(),
                    offer.getResult().copy(),
                    addMax,
                    addMax
            );
            book.entries.put(tradeId, created);
            setDirty();
            return;
        }

        existing.maxStock += addMax;
        existing.remainingStock = Math.min(existing.maxStock, existing.remainingStock + addMax);
        setDirty();
    }

    public TradeActionStatus executeTrade(ServerPlayer player, String tradeId, boolean batch) {
        PlayerTradeBook book = getOrCreateBook(player.getUUID());
        TradeEntry entry = book.entries.get(tradeId);
        if (entry == null) {
            return TradeActionStatus.NOT_FOUND;
        }
        if (entry.remainingStock <= 0) {
            return TradeActionStatus.OUT_OF_STOCK;
        }

        Inventory inventory = player.getInventory();
        int byCostA = maxTradesByCost(inventory, entry.costA);
        int byCostB = maxTradesByCost(inventory, entry.costB);
        if (!entry.costA.isEmpty() && !entry.costB.isEmpty() && ItemStack.isSameItemSameComponents(entry.costA, entry.costB)) {
            int total = countMatching(inventory, entry.costA);
            int need = Math.max(1, entry.costA.getCount()) + Math.max(1, entry.costB.getCount());
            int combined = total / need;
            byCostA = combined;
            byCostB = combined;
        }
        int byOutput = maxTradesByOutputCapacity(inventory, entry.result);

        if (byCostA <= 0 || byCostB <= 0) {
            return TradeActionStatus.INSUFFICIENT_INPUT;
        }
        if (byOutput <= 0) {
            return TradeActionStatus.OUTPUT_FULL;
        }

        int trades = Math.min(entry.remainingStock, Math.min(byCostA, Math.min(byCostB, byOutput)));
        if (!batch) {
            trades = Math.min(trades, 1);
        }
        if (trades <= 0) {
            return TradeActionStatus.FAILED;
        }

        if (!consumeCost(inventory, entry.costA, trades)) {
            return TradeActionStatus.INSUFFICIENT_INPUT;
        }
        if (!consumeCost(inventory, entry.costB, trades)) {
            return TradeActionStatus.INSUFFICIENT_INPUT;
        }

        if (!giveResult(inventory, entry.result, trades)) {
            return TradeActionStatus.OUTPUT_FULL;
        }

        entry.remainingStock -= trades;
        setDirty();
        player.containerMenu.broadcastChanges();
        return trades > 1 ? TradeActionStatus.SUCCESS_BATCH : TradeActionStatus.SUCCESS_ONE;
    }

    public boolean deleteTrade(ServerPlayer player, String tradeId) {
        PlayerTradeBook book = getOrCreateBook(player.getUUID());
        TradeEntry removed = book.entries.remove(tradeId);
        if (removed == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public CompoundTag buildSyncTag(ServerPlayer player, boolean applyRestock) {
        PlayerTradeBook book = getOrCreateBook(player.getUUID());
        long gameTime = player.serverLevel().getGameTime();
        if (applyRestock && applyRestockIfNeeded(book, gameTime)) {
            setDirty();
        }

        ListTag list = new ListTag();
        for (TradeEntry entry : book.entries.values()) {
            list.add(entry.toClientTag(player.serverLevel().registryAccess()));
        }

        CompoundTag out = new CompoundTag();
        out.put("entries", list);
        out.putLong("nextRestockGameTime", nextRestockBoundary(gameTime));
        return out;
    }

    private static boolean applyRestockIfNeeded(PlayerTradeBook book, long gameTime) {
        long dayIndex = Math.floorDiv(gameTime, 24000L);
        if (book.lastOpenDay == Long.MIN_VALUE) {
            book.lastOpenDay = dayIndex;
            return true;
        }
        if (dayIndex == book.lastOpenDay) {
            return false;
        }

        if (dayIndex > book.lastOpenDay) {
            long days = dayIndex - book.lastOpenDay;
            for (TradeEntry entry : book.entries.values()) {
                int perDay = (entry.maxStock + 1) / 2;
                long add = (long) perDay * days;
                long next = (long) entry.remainingStock + add;
                entry.remainingStock = (int) Math.min(entry.maxStock, next);
            }
        }

        book.lastOpenDay = dayIndex;
        return true;
    }

    public static long nextRestockBoundary(long gameTime) {
        long dayIndex = Math.floorDiv(gameTime, 24000L);
        return (dayIndex + 1L) * 24000L;
    }

    private static boolean isVillagerTradeSeen(AbstractVillager villager, String tradeId) {
        ListTag list = villager.getPersistentData().getList(TAG_TRADE_ONLINE, Tag.TAG_STRING);
        for (Tag tag : list) {
            if (tradeId.equals(tag.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void markVillagerTradeSeen(AbstractVillager villager, String tradeId) {
        CompoundTag data = villager.getPersistentData();
        ListTag list = data.getList(TAG_TRADE_ONLINE, Tag.TAG_STRING);
        list.add(StringTag.valueOf(tradeId));
        data.put(TAG_TRADE_ONLINE, list);
    }

    private PlayerTradeBook getOrCreateBook(UUID playerId) {
        return books.computeIfAbsent(playerId, id -> new PlayerTradeBook());
    }

    private static int maxTradesByCost(Inventory inventory, ItemStack cost) {
        if (cost == null || cost.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int count = countMatching(inventory, cost);
        return count / Math.max(1, cost.getCount());
    }

    private static int maxTradesByOutputCapacity(Inventory inventory, ItemStack result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        int space = outputCapacity(inventory, result);
        return space / Math.max(1, result.getCount());
    }

    private static int countMatching(Inventory inventory, ItemStack template) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int outputCapacity(Inventory inventory, ItemStack template) {
        int remaining = 0;
        int maxPerStack = Math.min(template.getMaxStackSize(), inventory.getMaxStackSize());

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                remaining += maxPerStack;
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int room = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - stack.getCount();
            if (room > 0) {
                remaining += room;
            }
        }
        return remaining;
    }

    private static boolean consumeCost(Inventory inventory, ItemStack template, int trades) {
        if (template == null || template.isEmpty()) {
            return true;
        }
        int need = Math.max(1, template.getCount()) * trades;
        for (int i = 0; i < inventory.getContainerSize() && need > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int take = Math.min(need, stack.getCount());
            stack.shrink(take);
            need -= take;
        }
        return need <= 0;
    }

    private static boolean giveResult(Inventory inventory, ItemStack template, int trades) {
        if (template == null || template.isEmpty()) {
            return false;
        }
        int total = Math.max(1, template.getCount()) * trades;
        int maxPerStack = Math.min(template.getMaxStackSize(), inventory.getMaxStackSize());
        List<ItemStack> toAdd = new ArrayList<>();

        while (total > 0) {
            int count = Math.min(maxPerStack, total);
            ItemStack stack = template.copy();
            stack.setCount(count);
            toAdd.add(stack);
            total -= count;
        }

        for (ItemStack stack : toAdd) {
            if (!inventory.add(stack)) {
                return false;
            }
        }
        return true;
    }

    public enum TradeActionStatus {
        SUCCESS_ONE,
        SUCCESS_BATCH,
        NOT_FOUND,
        OUT_OF_STOCK,
        INSUFFICIENT_INPUT,
        OUTPUT_FULL,
        FAILED
    }

    private static final class PlayerTradeBook {
        long lastOpenDay = Long.MIN_VALUE;
        final Map<String, TradeEntry> entries = new Object2ObjectLinkedOpenHashMap<>();

        CompoundTag toTag(HolderLookup.Provider lookup) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("lastOpenDay", lastOpenDay);
            ListTag list = new ListTag();
            for (TradeEntry entry : entries.values()) {
                list.add(entry.toStorageTag(lookup));
            }
            tag.put("entries", list);
            return tag;
        }

        static PlayerTradeBook fromTag(CompoundTag tag, HolderLookup.Provider lookup) {
            PlayerTradeBook book = new PlayerTradeBook();
            if (tag.contains("lastOpenDay")) {
                book.lastOpenDay = tag.getLong("lastOpenDay");
            }
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                TradeEntry entry = TradeEntry.fromStorageTag(lookup, (CompoundTag) t);
                if (entry != null) {
                    book.entries.put(entry.id, entry);
                }
            }
            return book;
        }
    }

    private static final class TradeEntry {
        final String id;
        final ItemStack costA;
        final ItemStack costB;
        final ItemStack result;
        int maxStock;
        int remainingStock;

        private TradeEntry(String id, ItemStack costA, ItemStack costB, ItemStack result, int maxStock, int remainingStock) {
            this.id = id;
            this.costA = costA == null ? ItemStack.EMPTY : costA;
            this.costB = costB == null ? ItemStack.EMPTY : costB;
            this.result = result == null ? ItemStack.EMPTY : result;
            this.maxStock = Math.max(1, maxStock);
            this.remainingStock = Math.max(0, remainingStock);
        }

        CompoundTag toStorageTag(HolderLookup.Provider lookup) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.put("costA", TradeCodecUtil.encodeStack(costA, lookup));
            tag.put("costB", TradeCodecUtil.encodeStack(costB, lookup));
            tag.put("result", TradeCodecUtil.encodeStack(result, lookup));
            tag.putInt("maxStock", maxStock);
            tag.putInt("remainingStock", remainingStock);
            return tag;
        }

        CompoundTag toClientTag(HolderLookup.Provider lookup) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.put("costA", TradeCodecUtil.encodeStack(costA, lookup));
            tag.put("costB", TradeCodecUtil.encodeStack(costB, lookup));
            tag.put("result", TradeCodecUtil.encodeStack(result, lookup));
            tag.putInt("remainingStock", remainingStock);
            return tag;
        }

        static TradeEntry fromStorageTag(HolderLookup.Provider lookup, CompoundTag tag) {
            String id = tag.getString("id");
            if (id == null || id.isBlank()) {
                return null;
            }
            ItemStack costA = TradeCodecUtil.decodeStack(lookup, tag.getCompound("costA"));
            ItemStack costB = TradeCodecUtil.decodeStack(lookup, tag.getCompound("costB"));
            ItemStack result = TradeCodecUtil.decodeStack(lookup, tag.getCompound("result"));
            int maxStock = Math.max(1, tag.getInt("maxStock"));
            int remaining = Math.max(0, tag.getInt("remainingStock"));
            return new TradeEntry(id, costA, costB, result, maxStock, Math.min(maxStock, remaining));
        }
    }
}
