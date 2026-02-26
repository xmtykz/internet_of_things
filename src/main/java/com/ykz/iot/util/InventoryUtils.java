package com.ykz.iot.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtils {
    private InventoryUtils() {}

    public static boolean hasItem(Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        ItemStack off = player.getOffhandItem();
        return !off.isEmpty() && off.is(item);
    }
}
