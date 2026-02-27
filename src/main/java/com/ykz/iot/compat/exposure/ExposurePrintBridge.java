package com.ykz.iot.compat.exposure;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.time.Instant;

public final class ExposurePrintBridge {
    private static final ResourceLocation PHOTOGRAPH_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("exposure", "photograph");
    private static final ResourceLocation DEFAULT_PALETTE =
            ResourceLocation.fromNamespaceAndPath("exposure", "map_colors_plus");

    private ExposurePrintBridge() {
    }

    public static ItemStack createPrintedPhotograph(ServerPlayer player,
                                                    String exposureId,
                                                    int width,
                                                    int height,
                                                    byte[] pixels,
                                                    String paletteId,
                                                    long unixTimestamp) {
        try {
            if (!ExposureCompat.isCompatible()) {
                return ItemStack.EMPTY;
            }

            Class<?> exposureTypeClass = Class.forName("io.github.mortuusars.exposure.world.camera.ExposureType");
            Object colorType = exposureTypeClass.getField("COLOR").get(null);

            Class<?> tagClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureData$Tag");
            Object tag = tagClass
                    .getConstructor(exposureTypeClass, String.class, long.class, boolean.class, boolean.class)
                    .newInstance(colorType, player.getScoreboardName(), normalizeTimestamp(unixTimestamp), false, true);

            Class<?> exposureDataClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureData");
            ResourceLocation palette = parsePaletteOrDefault(paletteId);
            Object exposureData = exposureDataClass
                    .getConstructor(int.class, int.class, byte[].class, ResourceLocation.class, tagClass)
                    .newInstance(width, height, pixels, palette, tag);

            Object repository = Class.forName("io.github.mortuusars.exposure.ExposureServer")
                    .getMethod("exposureRepository")
                    .invoke(null);
            repository.getClass().getMethod("save", String.class, exposureDataClass)
                    .invoke(repository, exposureId, exposureData);

            Item photographItem = BuiltInRegistries.ITEM.get(PHOTOGRAPH_ITEM_ID);
            if (photographItem == null || photographItem == BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
                return ItemStack.EMPTY;
            }

            ItemStack output = new ItemStack(photographItem);

            Class<?> identifierClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier");
            Object identifier = identifierClass.getMethod("id", String.class).invoke(null, exposureId);

            Class<?> frameClass = Class.forName("io.github.mortuusars.exposure.world.camera.frame.Frame");
            Object mutableFrame = frameClass.getMethod("toMutable").invoke(frameClass.getField("EMPTY").get(null));
            invokeCompatible(mutableFrame, "setIdentifier", identifier);
            invokeCompatible(mutableFrame, "setType", colorType);
            Object frame = invokeCompatible(mutableFrame, "toImmutable");

            Class<?> dataComponentsClass = Class.forName("io.github.mortuusars.exposure.Exposure$DataComponents");
            Object frameComponent = dataComponentsClass.getField("PHOTOGRAPH_FRAME").get(null);
            Object typeComponent = dataComponentsClass.getField("PHOTOGRAPH_TYPE").get(null);

            Method setMethod = ItemStack.class.getMethod("set", DataComponentType.class, Object.class);
            setMethod.invoke(output, frameComponent, frame);
            setMethod.invoke(output, typeComponent, colorType);
            return output;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ResourceLocation parsePaletteOrDefault(String raw) {
        try {
            if (raw != null && !raw.isBlank()) {
                return ResourceLocation.parse(raw);
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_PALETTE;
    }

    private static long normalizeTimestamp(long unixTimestamp) {
        if (unixTimestamp > 0) {
            return unixTimestamp;
        }
        return Instant.now().getEpochSecond();
    }

    private static Object invokeCompatible(Object target, String methodName, Object... args) throws Exception {
        Method compatible = findCompatibleMethod(target.getClass(), methodName, args);
        if (compatible == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
        }
        compatible.setAccessible(true);
        return compatible.invoke(target, args);
    }

    private static Method findCompatibleMethod(Class<?> owner, String name, Object... args) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < params.length; i++) {
                if (!isAssignable(params[i], args[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }

    private static boolean isAssignable(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        Class<?> argClass = arg.getClass();
        if (parameterType.isPrimitive()) {
            if (parameterType == boolean.class) return argClass == Boolean.class;
            if (parameterType == byte.class) return argClass == Byte.class;
            if (parameterType == short.class) return argClass == Short.class || argClass == Byte.class;
            if (parameterType == int.class) return argClass == Integer.class || argClass == Short.class || argClass == Byte.class;
            if (parameterType == long.class) return argClass == Long.class || argClass == Integer.class || argClass == Short.class || argClass == Byte.class;
            if (parameterType == float.class) return argClass == Float.class || argClass == Integer.class || argClass == Short.class || argClass == Byte.class;
            if (parameterType == double.class) return Number.class.isAssignableFrom(argClass);
            if (parameterType == char.class) return argClass == Character.class;
            return false;
        }
        return parameterType.isAssignableFrom(argClass);
    }
}

