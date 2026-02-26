package com.ykz.iot.item;

import com.ykz.iot.InternetofThings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(InternetofThings.MODID);

    public static final DeferredItem<Item> PHONE =
            ITEMS.register("phone", () -> new PhoneItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}