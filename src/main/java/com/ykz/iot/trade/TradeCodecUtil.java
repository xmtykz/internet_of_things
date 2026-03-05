package com.ykz.iot.trade;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TradeCodecUtil {
    private TradeCodecUtil() {
    }

    public static CompoundTag encodeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
        return encoded.result()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .orElseGet(CompoundTag::new);
    }

    public static CompoundTag encodeStack(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag tag = stack.saveOptional(lookup);
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        return new CompoundTag();
    }

    public static ItemStack decodeStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY);
    }

    public static ItemStack decodeStack(HolderLookup.Provider lookup, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(lookup, tag);
    }

    public static String tradeId(MerchantOffer offer) {
        return tradeId(offer.getBaseCostA(), offer.getCostB(), offer.getResult());
    }

    public static String tradeId(ItemStack costA, ItemStack costB, ItemStack result) {
        String raw = stackKey(costA) + "|" + stackKey(costB) + "|" + stackKey(result);
        return sha1Hex(raw);
    }

    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        return encodeStack(stack).toString();
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
