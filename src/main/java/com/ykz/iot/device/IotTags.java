package com.ykz.iot.device;

import com.ykz.iot.InternetofThings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class IotTags {
    private IotTags() {}

    public static final TagKey<Block> NETWORKABLE_DOORS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "networkable_doors"));
}
