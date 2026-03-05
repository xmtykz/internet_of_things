package com.ykz.iot.debug;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DebugNetworkService {
    private DebugNetworkService() {
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                DebugVillagerOffersRequestPayload.TYPE,
                DebugVillagerOffersRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        handleDumpVillagerOffers(player, payload.entityId());
                    }
                })
        );
    }

    private static void handleDumpVillagerOffers(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof AbstractVillager villager)) {
            player.sendSystemMessage(Component.translatable("text.internet_of_things.debug.villager_trade.not_villager"));
            return;
        }

        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) {
            player.sendSystemMessage(Component.translatable("text.internet_of_things.debug.villager_trade.empty"));
            return;
        }

        player.sendSystemMessage(Component.translatable(
                "text.internet_of_things.debug.villager_trade.title",
                villager.getDisplayName(),
                villager.getId(),
                offers.size()
        ));

        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            player.sendSystemMessage(Component.translatable(
                    "text.internet_of_things.debug.villager_trade.entry",
                    i + 1,
                    stackText(offer.getCostA()),
                    stackText(offer.getCostB()),
                    stackText(offer.getResult()),
                    offer.getUses(),
                    offer.getMaxUses()
            ));
        }
    }

    private static String stackText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }
}
