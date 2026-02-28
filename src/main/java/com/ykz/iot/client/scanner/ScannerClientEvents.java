package com.ykz.iot.client.scanner;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = InternetofThings.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ScannerClientEvents {
    private ScannerClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        if (ModMenus.SCANNER_FILM != null) {
            event.register(ModMenus.SCANNER_FILM.get(), ScannerFilmScreen::new);
        }
    }
}
