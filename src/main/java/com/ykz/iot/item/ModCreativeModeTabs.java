package com.ykz.iot.item;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import com.ykz.iot.compat.exposure.ExposureCompat;
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

    public static final Supplier<CreativeModeTab> INTERNET_TAB =
            CREATIVE_MODE_TABS.register("internet_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.PHONE.get()))
                    .title(Component.translatable("itemGroup.internet_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PHONE);
                        output.accept(ModItems.NETWORK_MODULE);
                        output.accept(ModBlocks.SIGNAL_BASE_STATION);
                        if (ExposureCompat.isLoaded()) {
                            if (ModBlocks.PRINTER != null) {
                                output.accept(ModBlocks.PRINTER);
                            }
                            if (ModBlocks.SCANNER != null) {
                                output.accept(ModBlocks.SCANNER);
                            }
                        }
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
