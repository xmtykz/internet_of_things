package com.ykz.iot.client.exposure;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ExposurePrintPayloadEncoder {
    private static final ResourceLocation DEFAULT_PALETTE =
            ResourceLocation.fromNamespaceAndPath("exposure", "map_colors_plus");

    private ExposurePrintPayloadEncoder() {
    }

    public static EncodedPhoto encode(Path path) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || path == null || !Files.isRegularFile(path)) {
                return null;
            }

            Class<?> colorPalettesClass = Class.forName("io.github.mortuusars.exposure.data.ColorPalettes");
            Object holder = colorPalettesClass.getMethod("getDefault", net.minecraft.core.RegistryAccess.class)
                    .invoke(null, mc.level.registryAccess());
            Object palette = invoke(holder, "value");
            ResourceLocation paletteId = extractPaletteId(holder);

            Class<?> colorClass = Class.forName("io.github.mortuusars.exposure.util.color.Color");
            Method argbFactory = colorClass.getMethod("argb", int.class);
            Method closestTo = palette.getClass().getMethod("closestTo", colorClass);

            try (InputStream in = Files.newInputStream(path);
                 NativeImage image = NativeImage.read(in)) {
                int width = image.getWidth();
                int height = image.getHeight();
                if (width <= 0 || height <= 0) {
                    return null;
                }

                byte[] pixels = new byte[width * height];
                Map<Integer, Integer> cache = new HashMap<>();

                int i = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = abgrToArgb(image.getPixelRGBA(x, y));
                        Integer cached = cache.get(argb);
                        int id;
                        if (cached != null) {
                            id = cached;
                        } else {
                            Object color = argbFactory.invoke(null, argb);
                            id = ((Number) closestTo.invoke(palette, color)).intValue() & 0xFF;
                            cache.put(argb, id);
                        }
                        pixels[i++] = (byte) id;
                    }
                }

                return new EncodedPhoto(width, height, pixels, paletteId.toString(), readTimestamp(path));
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static ResourceLocation extractPaletteId(Object holder) {
        try {
            Object optional = invoke(holder, "unwrapKey");
            if (optional instanceof Optional<?> keyOptional && keyOptional.isPresent()) {
                Object key = keyOptional.get();
                Object location = invoke(key, "location");
                if (location instanceof ResourceLocation id) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_PALETTE;
    }

    private static int abgrToArgb(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static long readTimestamp(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return modified.toInstant().getEpochSecond();
        } catch (Exception ignored) {
            return Instant.now().getEpochSecond();
        }
    }

    public record EncodedPhoto(int width, int height, byte[] pixels, String paletteId, long unixTimestamp) {
    }
}

