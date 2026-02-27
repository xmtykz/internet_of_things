package com.ykz.iot.compat.jade;

import com.ykz.iot.InternetofThings;
import com.ykz.iot.device.DoorPosHelper;
import com.ykz.iot.device.IotTags;
import com.ykz.iot.device.SmartDeviceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(InternetofThings.MODID)
public final class IotJadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(InternetofThings.MODID, "owned_device_name");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DoorOwnedNameProvider.INSTANCE, DoorBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DoorOwnedNameProvider.INSTANCE, DoorBlock.class);
    }

    private enum DoorOwnedNameProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final String TAG_OWNED_NAME = "OwnedName";

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            BlockPos basePos = DoorPosHelper.normalizeDoorBasePos(level, accessor.getPosition());
            if (!level.getBlockState(basePos).is(IotTags.NETWORKABLE_DOORS)) {
                return;
            }

            SmartDeviceSavedData.get(level).findByPos(basePos).ifPresent(device -> {
                String fallbackOwner = accessor.getPlayer() == null ? "" : accessor.getPlayer().getScoreboardName();
                String ownedName = SmartDeviceSavedData.formatOwnedName(device.ownerName, device.name, fallbackOwner);
                data.putString(TAG_OWNED_NAME, ownedName);
            });
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(TAG_OWNED_NAME)) {
                return;
            }
            tooltip.add(Component.literal(data.getString(TAG_OWNED_NAME)));
        }
    }
}
