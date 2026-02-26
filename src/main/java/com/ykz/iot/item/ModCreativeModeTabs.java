package com.ykz.iot.item;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InternetofThings.MODID);

    // 创建创造模式物品栏
    public static final Supplier<CreativeModeTab>  INTERNET_TAB =
            CREATIVE_MODE_TABS.register("internet_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.PHONE.get()))
                    .title(Component.translatable("itemGroup.internet_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PHONE);
                        output.accept(ModBlocks.SIGNAL_BASE_STATION);
                    }).build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TABS.register(eventBus);
    }
}











