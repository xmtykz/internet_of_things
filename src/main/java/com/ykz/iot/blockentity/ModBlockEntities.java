package com.ykz.iot.blockentity;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InternetofThings.MODID);

    public static final Supplier<BlockEntityType<SignalBaseStationBlockEntity>> SIGNAL_BASE_STATION =
            BLOCK_ENTITY_TYPES.register(
                    "signal_base_station",
                    () -> BlockEntityType.Builder.of(
                            SignalBaseStationBlockEntity::new,
                            ModBlocks.SIGNAL_BASE_STATION.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<PrinterBlockEntity>> PRINTER;
    public static final Supplier<BlockEntityType<ScannerBlockEntity>> SCANNER;

    static {
        if (ModBlocks.PRINTER != null) {
            PRINTER = BLOCK_ENTITY_TYPES.register(
                    "printer",
                    () -> BlockEntityType.Builder.of(
                            PrinterBlockEntity::new,
                            ModBlocks.PRINTER.get()
                    ).build(null)
            );
        } else {
            PRINTER = null;
        }

        if (ModBlocks.SCANNER != null) {
            SCANNER = BLOCK_ENTITY_TYPES.register(
                    "scanner",
                    () -> BlockEntityType.Builder.of(
                            ScannerBlockEntity::new,
                            ModBlocks.SCANNER.get()
                    ).build(null)
            );
        } else {
            SCANNER = null;
        }
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
