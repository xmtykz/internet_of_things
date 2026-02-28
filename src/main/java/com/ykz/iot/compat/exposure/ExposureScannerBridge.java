package com.ykz.iot.compat.exposure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ExposureScannerBridge {
    private ExposureScannerBridge() {
    }

    public static boolean isDevelopedFilm(ItemStack stack) {
        if (!ExposureCompat.isCompatible() || stack.isEmpty()) {
            return false;
        }
        try {
            return Class.forName("io.github.mortuusars.exposure.world.item.DevelopedFilmItem")
                    .isInstance(stack.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getStoredFrames(ItemStack stack) {
        if (!isDevelopedFilm(stack)) {
            return Collections.emptyList();
        }
        try {
            Object frames = stack.getItem().getClass()
                    .getMethod("getStoredFrames", ItemStack.class)
                    .invoke(stack.getItem(), stack);
            if (frames instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    public static int getStoredFramesCount(ItemStack stack) {
        if (!isDevelopedFilm(stack)) {
            return 0;
        }
        try {
            Object count = stack.getItem().getClass()
                    .getMethod("getStoredFramesCount", ItemStack.class)
                    .invoke(stack.getItem(), stack);
            if (count instanceof Number n) {
                return Math.max(0, n.intValue());
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static Optional<Object> getFrame(ItemStack stack, int index) {
        List<Object> frames = getStoredFrames(stack);
        if (index < 0 || index >= frames.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(frames.get(index));
    }

    public static Optional<String> getFrameId(Object frame) {
        if (frame == null) {
            return Optional.empty();
        }
        try {
            Object identifier = frame.getClass().getMethod("identifier").invoke(frame);
            if (identifier == null) {
                return Optional.empty();
            }

            Object optionalId = identifier.getClass().getMethod("getId").invoke(identifier);
            if (optionalId instanceof Optional<?> id && id.isPresent() && id.get() instanceof String str && !str.isBlank()) {
                return Optional.of(str);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Optional<String> getFilmTypeName(ItemStack stack) {
        if (!isDevelopedFilm(stack)) {
            return Optional.empty();
        }
        try {
            Object exposureType = stack.getItem().getClass().getMethod("getType").invoke(stack.getItem());
            if (exposureType == null) {
                return Optional.empty();
            }
            Object serialized = exposureType.getClass().getMethod("getSerializedName").invoke(exposureType);
            if (serialized instanceof String name && !name.isBlank()) {
                return Optional.of(name);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static boolean isPhotograph(ItemStack stack) {
        if (!ExposureCompat.isCompatible() || stack.isEmpty()) {
            return false;
        }
        try {
            return Class.forName("io.github.mortuusars.exposure.world.item.PhotographItem")
                    .isInstance(stack.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Optional<Object> getPhotographFrame(ItemStack stack) {
        if (!isPhotograph(stack)) {
            return Optional.empty();
        }
        try {
            Class<?> dataComponentsClass = Class.forName("io.github.mortuusars.exposure.Exposure$DataComponents");
            Object frameComponent = dataComponentsClass.getField("PHOTOGRAPH_FRAME").get(null);
            Object frame = stack.getClass().getMethod("get", Class.forName("net.minecraft.core.component.DataComponentType"))
                    .invoke(stack, frameComponent);
            if (frame == null) {
                return Optional.empty();
            }
            return Optional.of(frame);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static int getSourceFramesCount(ItemStack stack, boolean flatbed) {
        if (flatbed) {
            return getPhotographFrame(stack).isPresent() ? 1 : 0;
        }
        return getStoredFramesCount(stack);
    }

    public static Optional<Object> getSourceFrame(ItemStack stack, int index, boolean flatbed) {
        if (flatbed) {
            if (index != 0) {
                return Optional.empty();
            }
            return getPhotographFrame(stack);
        }
        return getFrame(stack, index);
    }

    public static Optional<ExportedExposureData> readExposureDataById(String frameId) {
        if (!ExposureCompat.isCompatible() || frameId == null || frameId.isBlank()) {
            return Optional.empty();
        }

        try {
            Object repository = Class.forName("io.github.mortuusars.exposure.ExposureServer")
                    .getMethod("exposureRepository")
                    .invoke(null);
            Object requested = repository.getClass()
                    .getMethod("load", String.class)
                    .invoke(repository, frameId);
            Object dataOptional = requested.getClass().getMethod("getData").invoke(requested);
            if (!(dataOptional instanceof Optional<?> optionalData) || optionalData.isEmpty()) {
                return Optional.empty();
            }

            Object exposureData = optionalData.get();
            int width = ((Number) exposureData.getClass().getMethod("getWidth").invoke(exposureData)).intValue();
            int height = ((Number) exposureData.getClass().getMethod("getHeight").invoke(exposureData)).intValue();
            byte[] pixels = ((byte[]) exposureData.getClass().getMethod("getPixels").invoke(exposureData)).clone();

            Object paletteObj = exposureData.getClass().getMethod("getPaletteId").invoke(exposureData);
            String paletteId = paletteObj instanceof ResourceLocation id ? id.toString() : "exposure:map_colors_plus";

            long unixTimestamp = 0L;
            Object tagObj = exposureData.getClass().getMethod("getTag").invoke(exposureData);
            if (tagObj != null) {
                Object ts = tagObj.getClass().getMethod("unixTimestamp").invoke(tagObj);
                if (ts instanceof Number n) {
                    unixTimestamp = n.longValue();
                }
            }
            return Optional.of(new ExportedExposureData(width, height, pixels, paletteId, unixTimestamp));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public record ExportedExposureData(int width, int height, byte[] pixels, String paletteId, long unixTimestamp) {
    }
}
