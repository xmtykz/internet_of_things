package com.ykz.iot.compat.exposure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ykz.iot.network.payload.scanner.ScannerExportDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExposureScannerClientBridge {
    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private ExposureScannerClientBridge() {
    }

    public static List<Object> getStoredFrames(ItemStack stack) {
        return ExposureScannerBridge.getStoredFrames(stack);
    }

    public static @Nullable Object getPhotographFrame(ItemStack stack) {
        return ExposureScannerBridge.getPhotographFrame(stack).orElse(null);
    }

    public static float[] getFilmColor(ItemStack stack) {
        if (!ExposureScannerBridge.isDevelopedFilm(stack)) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        try {
            Object exposureType = stack.getItem().getClass().getMethod("getType").invoke(stack.getItem());
            if (exposureType == null) {
                return new float[]{1f, 1f, 1f, 1f};
            }
            Object filmColor = exposureType.getClass().getMethod("getFilmColor").invoke(exposureType);
            if (filmColor == null) {
                return new float[]{1f, 1f, 1f, 1f};
            }
            float r = ((Number) filmColor.getClass().getMethod("r").invoke(filmColor)).floatValue();
            float g = ((Number) filmColor.getClass().getMethod("g").invoke(filmColor)).floatValue();
            float b = ((Number) filmColor.getClass().getMethod("b").invoke(filmColor)).floatValue();
            float a = ((Number) filmColor.getClass().getMethod("a").invoke(filmColor)).floatValue();
            return new float[]{r, g, b, a};
        } catch (Throwable ignored) {
            return new float[]{1f, 1f, 1f, 1f};
        }
    }

    public static boolean renderNegativeFrame(@Nullable Object frame, PoseStack poseStack,
                                              float x, float y, float size, float alpha) {
        return renderFrame(frame, poseStack, x, y, size, alpha, true);
    }

    public static boolean renderPhotographFrame(@Nullable Object frame, PoseStack poseStack,
                                                float x, float y, float size, float alpha) {
        return renderFrame(frame, poseStack, x, y, size, alpha, false);
    }

    private static boolean renderFrame(@Nullable Object frame, PoseStack poseStack,
                                       float x, float y, float size, float alpha, boolean negative) {
        if (!ExposureCompat.isCompatible() || frame == null) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();

            Class<?> exposureClientClass = Class.forName("io.github.mortuusars.exposure.ExposureClient");
            Object renderedExposures = exposureClientClass.getMethod("renderedExposures").invoke(null);
            Object imageRenderer = exposureClientClass.getMethod("imageRenderer").invoke(null);

            Object renderable = renderedExposures.getClass().getMethod("getOrCreate", frame.getClass()).invoke(renderedExposures, frame);

            Class<?> imageEffectClass = Class.forName("io.github.mortuusars.exposure.client.image.modifier.ImageEffect");
            Object modified = renderable;
            if (negative) {
                Object negativeEffect = imageEffectClass.getField("NEGATIVE_FILM").get(null);
                modified = renderable.getClass().getMethod("modifyWith", imageEffectClass).invoke(renderable, negativeEffect);
            }

            Object frameType = frame.getClass().getMethod("type").invoke(frame);
            Object imageColor = frameType.getClass().getMethod("getImageColor").invoke(frameType);
            Object colorWithAlpha = imageColor.getClass().getMethod("withAlpha", int.class).invoke(imageColor, Math.max(0, Math.min(255, (int) (alpha * 255f))));

            Class<?> coordinatesClass = Class.forName("io.github.mortuusars.exposure.client.render.image.RenderCoordinates");
            Object coordinates = coordinatesClass.getConstructor(float.class, float.class, float.class, float.class)
                    .newInstance(x, y, size, size);

            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            imageRenderer.getClass().getMethod("render",
                            Class.forName("io.github.mortuusars.exposure.client.image.renderable.RenderableImage"),
                            PoseStack.class,
                            MultiBufferSource.class,
                            coordinatesClass,
                            colorWithAlpha.getClass())
                    .invoke(imageRenderer, modified, poseStack, bufferSource, coordinates, colorWithAlpha);
            bufferSource.endBatch();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void exportToPhoneAlbum(ScannerExportDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        long pixelCount = (long) payload.width() * payload.height();
        if (payload.width() <= 0 || payload.height() <= 0 || pixelCount <= 0
                || payload.pixels() == null || payload.pixels().length != pixelCount) {
            mc.player.displayClientMessage(Component.translatable("text.internet_of_things.scanner.export_failed"), true);
            return;
        }

        NativeImage image = null;
        try {
            Object palette = resolvePalette(payload.paletteId());
            if (palette == null) {
                mc.player.displayClientMessage(Component.translatable("text.internet_of_things.scanner.export_failed"), true);
                return;
            }

            image = new NativeImage(payload.width(), payload.height(), false);
            int i = 0;
            for (int y = 0; y < payload.height(); y++) {
                for (int x = 0; x < payload.width(); x++) {
                    int idx = payload.pixels()[i++] & 0xFF;
                    int argb = ((Number) palette.getClass().getMethod("byId", int.class).invoke(palette, idx)).intValue();
                    image.setPixelRGBA(x, y, argbToAbgr(argb));
                }
            }

            Path root = mc.gameDirectory.toPath()
                    .resolve("screenshots")
                    .resolve("phone_photo")
                    .resolve(ExposureRuntime.currentWorldFolder());
            Files.createDirectories(root);
            String baseName = FILE_NAME_FORMATTER.format(LocalDateTime.now());
            Path output = root.resolve(baseName + ".png");
            int suffix = 1;
            while (Files.exists(output)) {
                output = root.resolve(baseName + "_" + suffix + ".png");
                suffix++;
            }
            image.writeToFile(output);
            mc.player.displayClientMessage(Component.translatable("text.internet_of_things.phone.camera.saved", output.getFileName().toString()), true);
        } catch (Throwable ignored) {
            mc.player.displayClientMessage(Component.translatable("text.internet_of_things.scanner.export_failed"), true);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static @Nullable Object resolvePalette(String paletteId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Class<?> colorPalettes = Class.forName("io.github.mortuusars.exposure.data.ColorPalettes");
            ResourceLocation id = ResourceLocation.parse((paletteId == null || paletteId.isBlank())
                    ? "exposure:map_colors_plus"
                    : paletteId);
            Object holder = colorPalettes.getMethod("get", net.minecraft.core.RegistryAccess.class, ResourceLocation.class)
                    .invoke(null, mc.level.registryAccess(), id);
            return holder.getClass().getMethod("value").invoke(holder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
