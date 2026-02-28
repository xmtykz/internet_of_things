package com.ykz.iot.menu;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, InternetofThings.MODID);

    public static final Supplier<MenuType<PrinterMenu>> PRINTER =
            MENUS.register("printer", () -> IMenuTypeExtension.create(PrinterMenu::fromBuffer));
    public static final Supplier<MenuType<ScannerFilmMenu>> SCANNER_FILM;

    static {
        if (ModBlocks.SCANNER != null) {
            SCANNER_FILM = MENUS.register("scanner_film", () -> IMenuTypeExtension.create(ScannerFilmMenu::fromBuffer));
        } else {
            SCANNER_FILM = null;
        }
    }

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
