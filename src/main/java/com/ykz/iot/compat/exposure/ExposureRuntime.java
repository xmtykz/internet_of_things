package com.ykz.iot.compat.exposure;

import com.ykz.iot.client.PhoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ExposureRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation EXPOSURE_CAMERA_ID =
            ResourceLocation.fromNamespaceAndPath("exposure", "camera");
    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String EXPORT_FOLDER = "screenshots/phone_photo";
    private static final long CAPTURE_COOLDOWN_MS = 300L;

    private static boolean listenersRegistered = false;
    private static boolean sessionActive = false;
    private static PendingAction pendingAction = PendingAction.NONE;
    private static long lastCaptureAt = 0L;
    private static boolean useKeyWasDown = false;
    private static int missingViewfinderTicks = 0;
    private static boolean captureQueuedByTimer = false;
    private static long captureExecuteAtTick = -1L;
    private static int lastCountdownSecond = -1;

    private static int insertedSlot = -1;
    private static ItemStack previousStack = ItemStack.EMPTY;
    private static ItemStack cameraStack = ItemStack.EMPTY;
    private static Item cameraItem;

    private ExposureRuntime() {}

    public static boolean startSession() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }

        cameraItem = BuiltInRegistries.ITEM.get(EXPOSURE_CAMERA_ID);
        if (cameraItem == null) {
            return false;
        }

        ensureListeners();

        if (!insertTempCameraIntoMainHand(mc.player, cameraItem)) {
            cleanup();
            return false;
        }

        if (!activateInHand(cameraItem, mc.player, cameraStack)) {
            cleanup();
            return false;
        }

        sessionActive = true;
        pendingAction = PendingAction.NONE;
        useKeyWasDown = false;
        missingViewfinderTicks = 0;
        mc.setScreen(null);
        return true;
    }

    private static void ensureListeners() {
        if (listenersRegistered) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ExposureRuntime::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ExposureRuntime::onClientTick);
        NeoForge.EVENT_BUS.addListener(ExposureRuntime::onScreenOpening);
        listenersRegistered = true;
    }

    private static boolean insertTempCameraIntoMainHand(Player player, Item item) {
        int slot = player.getInventory().selected;
        ItemStack old = player.getInventory().getItem(slot).copy();
        ItemStack temp = new ItemStack(item);

        player.getInventory().setItem(slot, temp);
        attachDefaultFlashIfPresent(temp);
        insertedSlot = slot;
        previousStack = old;
        cameraStack = temp;
        return true;
    }

    private static boolean activateInHand(Item item, Player player, ItemStack stack) {
        try {
            Method activate = item.getClass().getMethod("activateInHand", Player.class, ItemStack.class, InteractionHand.class);
            activate.invoke(item, player, stack, InteractionHand.MAIN_HAND);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void deactivateIfPossible(Player player) {
        try {
            if (cameraItem != null && !cameraStack.isEmpty()) {
                Method deactivate = cameraItem.getClass().getMethod("deactivate", net.minecraft.world.entity.Entity.class, ItemStack.class);
                deactivate.invoke(cameraItem, player, cameraStack);
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> cameraClient = Class.forName("io.github.mortuusars.exposure.client.camera.CameraClient");
            cameraClient.getMethod("removeViewfinder").invoke(null);
            cameraClient.getMethod("resetCameraEntity").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static void restoreMainHand(Player player) {
        if (insertedSlot < 0) {
            return;
        }
        player.getInventory().setItem(insertedSlot, previousStack.copy());
    }

    private static void cleanup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            deactivateIfPossible(mc.player);
            restoreMainHand(mc.player);
        }
        sessionActive = false;
        pendingAction = PendingAction.NONE;
        useKeyWasDown = false;
        missingViewfinderTicks = 0;
        captureQueuedByTimer = false;
        captureExecuteAtTick = -1L;
        lastCountdownSecond = -1;
        insertedSlot = -1;
        previousStack = ItemStack.EMPTY;
        cameraStack = ItemStack.EMPTY;
        cameraItem = null;
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (!sessionActive || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            pendingAction = PendingAction.RETURN_TO_PHONE;
        } else if (event.getKey() == GLFW.GLFW_KEY_E) {
            pendingAction = PendingAction.CLOSE_ALL;
        }
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (sessionActive && pendingAction != PendingAction.NONE) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!sessionActive) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            cleanup();
            return;
        }
        if (pendingAction != PendingAction.NONE) {
            PendingAction action = pendingAction;
            cleanup();
            if (action == PendingAction.RETURN_TO_PHONE) {
                mc.setScreen(new PhoneScreen());
            } else {
                mc.setScreen(null);
            }
            return;
        }

        // If our temporary camera item is no longer in the expected slot, session is over.
        if (!isSessionInventoryStateValid(mc.player)) {
            cleanup();
            return;
        }

        if (isExposureViewfinderPresentReflective()) {
            missingViewfinderTicks = 0;
        } else {
            missingViewfinderTicks++;
            if (missingViewfinderTicks > 2) {
                cleanup();
                return;
            }
        }

        boolean useDown = mc.options.keyUse.isDown();
        if (useDown && !useKeyWasDown && !captureQueuedByTimer) {
            long now = System.currentTimeMillis();
            if (now - lastCaptureAt >= CAPTURE_COOLDOWN_MS) {
                lastCaptureAt = now;
                queueOrCaptureByTimer();
            }
        }
        useKeyWasDown = useDown;

        if (captureQueuedByTimer) {
            long nowTick = mc.level.getGameTime();
            long remainingTicks = captureExecuteAtTick - nowTick;
            if (remainingTicks <= 0) {
                captureQueuedByTimer = false;
                captureExecuteAtTick = -1L;
                lastCountdownSecond = -1;
                capturePhotoReflective();
                return;
            }

            int seconds = (int) Math.ceil(remainingTicks / 20.0);
            if (seconds != lastCountdownSecond) {
                lastCountdownSecond = seconds;
                showToast(Component.literal("Timer: " + seconds + "s"));
            }
        }
    }

    private static void queueOrCaptureByTimer() {
        int timerSeconds = readSelfTimerSecondsReflective();
        if (timerSeconds <= 0) {
            capturePhotoReflective();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            capturePhotoReflective();
            return;
        }

        captureQueuedByTimer = true;
        captureExecuteAtTick = mc.level.getGameTime() + timerSeconds * 20L;
        lastCountdownSecond = -1;
    }

    private static void capturePhotoReflective() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || cameraItem == null || cameraStack.isEmpty()) {
                showCaptureFailedToast();
                return;
            }

            String worldFolder = sanitizeWorldFolder(worldName());
            String fileName = FILE_NAME_FORMATTER.format(LocalDateTime.now());
            long captureUnixTimestamp = System.currentTimeMillis() / 1000L;
            CaptureProfile profile = readCaptureProfileReflective();
            triggerShutterAnimationReflective(profile.shutterSpeed);

            Class<?> taskClass = Class.forName("io.github.mortuusars.exposure.util.cycles.task.Task");
            Class<?> captureClass = Class.forName("io.github.mortuusars.exposure.client.capture.Capture");
            Class<?> exposureClientClass = Class.forName("io.github.mortuusars.exposure.ExposureClient");

            Object captureTask = buildCaptureTaskReflective(taskClass, captureClass, profile, mc.player);

            Object taskAfterHandle = invokeCompatible(captureTask, "handleErrorAndGetResult",
                    (Consumer<Object>) err -> showCaptureFailedToast());

            Object processedTask = invokeCompatible(taskAfterHandle, "thenAsync",
                    (java.util.function.Function<Object, Object>) image -> {
                        try {
                            return processCapturedImageReflective(image, profile, captureUnixTimestamp);
                        } catch (Throwable t) {
                            showCaptureFailedToast();
                            throw new RuntimeException(t);
                        }
                    });

            Object exportTask = invokeCompatible(processedTask, "acceptAsync",
                    (Consumer<Object>) exposureData ->
                            exportExposureDataReflective(exposureData, fileName, worldFolder,
                                    captureUnixTimestamp, profile.exportMultiplier));

            // Ensure async failures also surface to player chat.
            exportTask = invokeCompatible(exportTask, "onError",
                    (Consumer<Object>) err -> showCaptureFailedToast());

            Object cycles = invokeCompatibleStatic(exposureClientClass, "cycles");
            invokeCompatible(cycles, "enqueueTask", exportTask);
        } catch (Throwable ignored) {
            LOGGER.error("Phone camera pipeline failed.", ignored);
            showCaptureFailedToast();
        }
    }

    private static Object buildCaptureTaskReflective(Class<?> taskClass, Class<?> captureClass,
                                                     CaptureProfile profile, Player player) throws Exception {
        Class<?> captureActionClass = Class.forName("io.github.mortuusars.exposure.client.capture.action.CaptureAction");
        Object screenshotTask = captureClass.getMethod("screenshot").invoke(null);

        List<Object> actions = new ArrayList<>();

        actions.add(invokeCompatibleStatic(captureActionClass, "setCameraEntity", player));

        try {
            Class<?> cameraHolderClass = Class.forName("io.github.mortuusars.exposure.world.entity.CameraHolder");
            Object holder = cameraHolderClass.isInstance(player) ? player : null;
            Method forceMethod = findMethod(captureActionClass, "forceRegularOrSelfieCamera", 1);
            if (forceMethod != null) {
                actions.add(forceMethod.invoke(null, holder));
            }
        } catch (Throwable ignored) {
        }

        actions.add(invokeCompatibleStatic(captureActionClass, "setFov", profile.fov));

        actions.add(invokeCompatibleStatic(captureActionClass, "hideGui"));

        if (!profile.keepPostEffect) {
            actions.add(invokeCompatibleStatic(captureActionClass, "disablePostEffect"));
        }

        actions.add(invokeCompatibleStatic(captureActionClass, "setFilter", profile.filterShader));

        if (profile.shutterSpeed != null) {
            Method modifyGamma = findMethod(captureActionClass, "modifyGamma", 1);
            if (modifyGamma != null) {
                actions.add(modifyGamma.invoke(null, profile.shutterSpeed));
            }
        }
        if (profile.shouldUseFlash) {
            actions.add(invokeCompatibleStatic(captureActionClass, "flash", player));
        }

        Object actionArray = Array.newInstance(captureActionClass, actions.size());
        for (int i = 0; i < actions.size(); i++) {
            Array.set(actionArray, i, actions.get(i));
        }

        return invokeCompatibleStatic(captureClass, "of", screenshotTask, actionArray);
    }

    private static Object processCapturedImageReflective(Object image, CaptureProfile profile, long captureUnixTimestamp) {
        try {
            Object transformedImage = image;
            Object modifier = buildCaptureModifierReflective(profile);
            if (modifier != null) {
                transformedImage = invokeCompatible(modifier, "apply", transformedImage);
            }

            Object paletteHolder = resolvePaletteHolderReflective(profile.colorPaletteKey);
            Object palette = invokeCompatible(paletteHolder, "value");

            Class<?> palettizerClass = Class.forName("io.github.mortuusars.exposure.client.capture.palettizer.Palettizer");
            Object palettizer = invokeCompatibleStatic(palettizerClass, "fromDitherMode", profile.ditherMode);
            Object palettizeAndClose = invokeCompatible(palettizer, "palettizeAndClose", palette);
            Object palettedImage = invokeCompatible(palettizeAndClose, "apply", transformedImage);

            ResourceLocation paletteId = extractPaletteIdReflective(paletteHolder);
            return createExposureDataReflective(palettedImage, paletteId, profile.exposureType, captureUnixTimestamp);
        } catch (Throwable ignored) {
            LOGGER.error("Failed to process image through Exposure pipeline.", ignored);
            throw new RuntimeException("Failed to process image through Exposure pipeline.", ignored);
        }
    }

    private static Object buildCaptureModifierReflective(CaptureProfile profile) {
        try {
            Class<?> imageEffectClass = Class.forName("io.github.mortuusars.exposure.client.image.modifier.ImageEffect");
            Class<?> cropClass = Class.forName("io.github.mortuusars.exposure.client.image.modifier.ImageEffect$Crop");
            Class<?> resizeClass = Class.forName("io.github.mortuusars.exposure.client.image.modifier.ImageEffect$Resize");
            Class<?> levelsClass = Class.forName("io.github.mortuusars.exposure.world.camera.film.properties.Levels");
            Class<?> hsbClass = Class.forName("io.github.mortuusars.exposure.world.camera.film.properties.HSB");

            List<Object> effects = new ArrayList<>();
            effects.add(cropClass.getField("SQUARE_CENTER").get(null));
            effects.add(cropClass.getMethod("factor", float.class).invoke(null, profile.cropFactor));
            effects.add(resizeClass.getMethod("to", int.class).invoke(null, profile.frameSize));
            effects.add(imageEffectClass.getMethod("exposure", float.class).invoke(null, profile.exposureStrength));
            effects.add(imageEffectClass.getMethod("contrast", float.class).invoke(null, profile.contrast));
            effects.add(imageEffectClass.getMethod("levels", levelsClass).invoke(null, profile.levels));
            effects.add(imageEffectClass.getMethod("hsb", hsbClass).invoke(null, profile.hsb));
            effects.add(imageEffectClass.getMethod("noise", float.class).invoke(null, profile.noise));
            effects.add(imageEffectClass.getMethod("optional", boolean.class, imageEffectClass)
                    .invoke(null, profile.blackAndWhite, imageEffectClass.getField("BLACK_AND_WHITE").get(null)));
            if (profile.exportMultiplier > 1) {
                effects.add(resizeClass.getMethod("multiplier", int.class).invoke(null, profile.exportMultiplier));
            }

            Object effectArray = Array.newInstance(imageEffectClass, effects.size());
            for (int i = 0; i < effects.size(); i++) {
                Array.set(effectArray, i, effects.get(i));
            }
            return imageEffectClass.getMethod("chain", effectArray.getClass()).invoke(null, effectArray);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CaptureProfile readCaptureProfileReflective() {
        try {
            Class<?> levelsClass = Class.forName("io.github.mortuusars.exposure.world.camera.film.properties.Levels");
            Class<?> hsbClass = Class.forName("io.github.mortuusars.exposure.world.camera.film.properties.HSB");
            Class<?> filmPropertiesClass = Class.forName("io.github.mortuusars.exposure.world.camera.film.properties.FilmProperties");

            Object filmEmpty = filmPropertiesClass.getField("EMPTY").get(null);

            float cropFactor = 1.0f;
            int frameSize = (int) invokeOrDefault(filmEmpty, "getSize", 320);
            float sensitivity = 0.0f;
            float contrast = 0.0f;
            float noise = 0.0f;
            Object levels = levelsClass.getField("EMPTY").get(null);
            Object hsb = hsbClass.getField("EMPTY").get(null);
            Object exposureType = filmEmpty.getClass().getMethod("type").invoke(filmEmpty);
            Object ditherMode = filmEmpty.getClass().getMethod("ditherMode").invoke(filmEmpty);
            Object colorPaletteKey = filmEmpty.getClass().getMethod("colorPalette").invoke(filmEmpty);
            boolean blackAndWhite = false;
            double fov = 70.0;
            Object shutterSpeed = null;
            Optional<?> filterShader = Optional.empty();
            boolean keepPostEffect = readKeepPostEffectReflective();
            int exportMultiplier = readExportMultiplierReflective();
            boolean shouldUseFlash = false;
            ItemStack activeCameraStack = getSessionCameraStackOrFallback();

            if (cameraItem != null && !activeCameraStack.isEmpty()) {
                try {
                    Object filmProperties = cameraItem.getClass()
                            .getMethod("getFilmProperties", ItemStack.class)
                            .invoke(cameraItem, activeCameraStack);

                    frameSize = ((Number) filmProperties.getClass().getMethod("getSize").invoke(filmProperties)).intValue();
                    exposureType = filmProperties.getClass().getMethod("type").invoke(filmProperties);
                    ditherMode = filmProperties.getClass().getMethod("ditherMode").invoke(filmProperties);
                    colorPaletteKey = filmProperties.getClass().getMethod("colorPalette").invoke(filmProperties);

                    String typeName;
                    try {
                        typeName = String.valueOf(exposureType.getClass().getMethod("getSerializedName").invoke(exposureType));
                    } catch (Throwable ignored) {
                        typeName = String.valueOf(exposureType);
                    }
                    blackAndWhite = "black_and_white".equalsIgnoreCase(typeName)
                            || "BLACK_AND_WHITE".equalsIgnoreCase(typeName);

                    Object style = filmProperties.getClass().getMethod("style").invoke(filmProperties);
                    sensitivity = ((Number) style.getClass().getMethod("sensitivity").invoke(style)).floatValue();
                    contrast = ((Number) style.getClass().getMethod("contrast").invoke(style)).floatValue();
                    noise = ((Number) style.getClass().getMethod("noise").invoke(style)).floatValue();
                    levels = style.getClass().getMethod("levels").invoke(style);
                    hsb = style.getClass().getMethod("hsb").invoke(style);
                } catch (Throwable ignored) {
                }

                try {
                    cropFactor = ((Number) cameraItem.getClass().getMethod("getCropFactor").invoke(cameraItem)).floatValue();
                } catch (Throwable ignored) {
                }

                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        fov = ((Number) cameraItem.getClass()
                                .getMethod("getFov", net.minecraft.world.level.Level.class, ItemStack.class)
                                .invoke(cameraItem, mc.player.level(), activeCameraStack)).doubleValue();

                        filterShader = (Optional<?>) cameraItem.getClass()
                                .getMethod("getFilterShaderLocation", net.minecraft.core.RegistryAccess.class, ItemStack.class)
                                .invoke(cameraItem, mc.player.level().registryAccess(), activeCameraStack);
                    }
                } catch (Throwable ignored) {
                    filterShader = Optional.empty();
                }

                try {
                    Class<?> cameraSettingsClass = Class.forName("io.github.mortuusars.exposure.world.item.camera.CameraSettings");
                    Object shutterSetting = cameraSettingsClass.getField("SHUTTER_SPEED").get(null);
                    shutterSpeed = shutterSetting.getClass()
                            .getMethod("getOrDefault", ItemStack.class)
                            .invoke(shutterSetting, activeCameraStack);
                } catch (Throwable ignored) {
                }

                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && mc.player != null) {
                        Object flash = invokeCompatible(cameraItem, "getFlash");
                        Object available = invokeCompatible(flash, "isAvailable", activeCameraStack);
                        if (available instanceof Boolean isAvailable && isAvailable) {
                            int lightLevel = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition());
                            Object should = invokeCompatible(flash, "shouldFire", activeCameraStack, lightLevel);
                            if (should instanceof Boolean b) {
                                shouldUseFlash = b;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            float shutterBrightness = 1.0f;
            if (shutterSpeed != null) {
                try {
                    shutterBrightness = ((Number) shutterSpeed.getClass().getMethod("getBrightness").invoke(shutterSpeed)).floatValue();
                } catch (Throwable ignored) {
                }
            }

            float exposureStrength = shutterBrightness * (sensitivity + 1.0f);

            return new CaptureProfile(cropFactor, frameSize, exposureStrength, contrast, noise,
                    levels, hsb, blackAndWhite, ditherMode, colorPaletteKey, exposureType,
                    fov, filterShader, shutterSpeed, keepPostEffect, exportMultiplier, shouldUseFlash);
        } catch (Throwable ignored) {
            throw new RuntimeException("Failed to read Exposure capture profile.");
        }
    }

    private static Object createExposureDataReflective(Object palettedImage, ResourceLocation paletteId,
                                                       Object exposureType, long captureUnixTimestamp) throws Exception {
        Class<?> exposureDataClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureData");
        Class<?> tagClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureData$Tag");

        int width = ((Number) palettedImage.getClass().getMethod("width").invoke(palettedImage)).intValue();
        int height = ((Number) palettedImage.getClass().getMethod("height").invoke(palettedImage)).intValue();
        byte[] pixels = (byte[]) palettedImage.getClass().getMethod("pixels").invoke(palettedImage);

        Object tag = tagClass.getConstructors()[0].newInstance(
                exposureType,
                Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "",
                captureUnixTimestamp,
                false,
                false);

        return exposureDataClass.getConstructor(int.class, int.class, byte[].class, ResourceLocation.class, tagClass)
                .newInstance(width, height, pixels, paletteId, tag);
    }

    private static Object resolvePaletteHolderReflective(Object colorPaletteKey) throws Exception {
        Class<?> colorPalettesClass = Class.forName("io.github.mortuusars.exposure.data.ColorPalettes");
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            throw new IllegalStateException("Client level is not available while resolving palette.");
        }

        Object registryAccess = mc.level.registryAccess();

        if (colorPaletteKey != null) {
            for (Method method : colorPalettesClass.getMethods()) {
                if (!method.getName().equals("get") || method.getParameterCount() != 2) {
                    continue;
                }
                try {
                    return method.invoke(null, registryAccess, colorPaletteKey);
                } catch (Throwable ignored) {
                }
            }
        }

        return colorPalettesClass.getMethod("getDefault", net.minecraft.core.RegistryAccess.class)
                .invoke(null, registryAccess);
    }

    private static ResourceLocation extractPaletteIdReflective(Object paletteHolder) {
        try {
            Optional<?> keyOptional = (Optional<?>) invokeCompatible(paletteHolder, "unwrapKey");
            if (keyOptional.isPresent()) {
                Object key = keyOptional.get();
                return (ResourceLocation) invokeCompatible(key, "location");
            }
        } catch (Throwable ignored) {
        }
        return ResourceLocation.fromNamespaceAndPath("exposure", "map_colors_plus");
    }

    private static void exportExposureDataReflective(Object exposureData, String fileName,
                                                     String worldFolder, long creationUnixTimestamp,
                                                     int exportMultiplier) {
        try {
            Class<?> exposureDataClass = Class.forName("io.github.mortuusars.exposure.world.level.storage.ExposureData");
            Class<?> imageExporterClass = Class.forName("io.github.mortuusars.exposure.client.export.ImageExporter");
            File baseFolder = new File(Minecraft.getInstance().gameDirectory, EXPORT_FOLDER);
            String baseFolderPath = baseFolder.getAbsolutePath();
            File expectedFile = new File(new File(baseFolder, worldFolder), fileName + ".png");
            AtomicBoolean exported = new AtomicBoolean(false);
            AtomicReference<File> exportedFile = new AtomicReference<>();

            Object exporter = imageExporterClass
                    .getConstructor(exposureDataClass, String.class)
                    .newInstance(exposureDataClass.cast(exposureData), fileName);

            exporter = invokeCompatible(exporter, "withFolder", baseFolderPath);
            exporter = invokeCompatible(exporter, "organizeByWorld", worldFolder);
            exporter = invokeCompatible(exporter, "setCreationDate", creationUnixTimestamp);
            exporter = invokeCompatible(exporter, "onExport", (Consumer<File>) file -> {
                exported.set(true);
                exportedFile.set(file);
            });

            invokeCompatible(exporter, "export");
            if (exported.get()) {
                return;
            }

            if (expectedFile.exists()) {
                return;
            }

            LOGGER.error("Export finished without file callback and file not found. expected='{}'", expectedFile.getAbsolutePath());
            showCaptureFailedToast();
        } catch (Throwable ignored) {
            LOGGER.error("Failed to export phone photo.", ignored);
            showCaptureFailedToast();
        }
    }

    private static boolean isSessionInventoryStateValid(Player player) {
        if (insertedSlot < 0 || insertedSlot >= player.getInventory().getContainerSize()) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(insertedSlot);
        if (current.isEmpty() || cameraItem == null || current.getItem() != cameraItem) {
            return false;
        }
        return true;
    }

    private static boolean isExposureViewfinderPresentReflective() {
        try {
            Class<?> cameraClient = Class.forName("io.github.mortuusars.exposure.client.camera.CameraClient");
            Object viewfinder = invokeCompatibleStatic(cameraClient, "viewfinder");
            return viewfinder != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void attachDefaultFlashIfPresent(ItemStack cameraStack) {
        try {
            Item flashItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("exposure", "flash"));
            Item air = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"));
            if (flashItem == null || flashItem == air) {
                // Exposure does not register a dedicated flash item in all builds. Any non-empty item works for attachment presence.
                flashItem = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("torch"));
            }
            if (flashItem == null || flashItem == air) return;

            Class<?> attachmentClass = Class.forName("io.github.mortuusars.exposure.world.item.camera.Attachment");
            Object flashAttachment = attachmentClass.getField("FLASH").get(null);
            invokeCompatible(flashAttachment, "set", cameraStack, new ItemStack(flashItem));
        } catch (Throwable ignored) {
        }
    }

    private static void triggerShutterAnimationReflective(Object shutterSpeed) {
        try {
            Class<?> clientPacketsHandler = Class.forName("io.github.mortuusars.exposure.network.handler.ClientPacketsHandler");
            invokeCompatibleStatic(clientPacketsHandler, "shutterOpened");

            if (shutterSpeed == null) {
                return;
            }

            int durationTicks = 2;
            try {
                Object ticks = invokeCompatible(shutterSpeed, "getDurationTicks");
                if (ticks instanceof Number n) {
                    durationTicks = Math.max(2, n.intValue());
                }
            } catch (Throwable ignored) {
            }

            Class<?> cameraClient = Class.forName("io.github.mortuusars.exposure.client.camera.CameraClient");
            Object viewfinder = invokeCompatibleStatic(cameraClient, "viewfinder");
            if (viewfinder == null) {
                return;
            }
            Object overlay = invokeCompatible(viewfinder, "overlay");
            if (overlay == null) {
                return;
            }

            // Keep shutter overlay for shutter-speed duration, same visual expectation as Exposure behavior.
            java.lang.reflect.Field until = overlay.getClass().getDeclaredField("forceDrawShutterUntil");
            until.setAccessible(true);
            until.setLong(overlay, System.currentTimeMillis() + durationTicks * 50L);

            java.lang.reflect.Field nextFrame = overlay.getClass().getDeclaredField("forceDrawShutterOnNextFrame");
            nextFrame.setAccessible(true);
            nextFrame.setBoolean(overlay, true);
        } catch (Throwable ignored) {
        }
    }

    private static int readExportMultiplierReflective() {
        int fallback = 2;
        try {
            Class<?> configClientClass = Class.forName("io.github.mortuusars.exposure.Config$Client");
            Object value = configClientClass.getField("EXPORT_SIZE_MULTIPLIER").get(null);
            int raw = ((Number) value.getClass().getMethod("get").invoke(value)).intValue();
            return Math.max(1, raw);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean readKeepPostEffectReflective() {
        try {
            Class<?> configClientClass = Class.forName("io.github.mortuusars.exposure.Config$Client");
            Object value = configClientClass.getField("KEEP_POST_EFFECT").get(null);
            return (Boolean) value.getClass().getMethod("get").invoke(value);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static Object invokeOrDefault(Object target, String methodName, Object fallback) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static int readSelfTimerSecondsReflective() {
        try {
            ItemStack activeCameraStack = getSessionCameraStackOrFallback();
            Class<?> cameraSettingsClass = Class.forName("io.github.mortuusars.exposure.world.item.camera.CameraSettings");
            Object setting = cameraSettingsClass.getField("SELF_TIMER").get(null);
            Object timerEnum = invokeCompatible(setting, "getOrDefault", activeCameraStack);
            Object seconds = invokeCompatible(timerEnum, "getSeconds");
            if (seconds instanceof Number n) {
                return Math.max(0, n.intValue());
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static ItemStack getSessionCameraStackOrFallback() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && insertedSlot >= 0 && insertedSlot < mc.player.getInventory().getContainerSize()) {
            ItemStack inSlot = mc.player.getInventory().getItem(insertedSlot);
            if (!inSlot.isEmpty() && cameraItem != null && inSlot.getItem() == cameraItem) {
                cameraStack = inSlot;
                return inSlot;
            }
        }
        return cameraStack;
    }

    private static Object invokeCompatibleStatic(Class<?> owner, String method, Object... args) throws Exception {
        Method compatible = findCompatibleMethod(owner, method, args);
        if (compatible == null) {
            throw new NoSuchMethodException(owner.getName() + "#" + method);
        }
        compatible.setAccessible(true);
        return compatible.invoke(null, args);
    }

    private static Object invokeCompatible(Object target, String method, Object... args) throws Exception {
        Method compatible = findCompatibleMethod(target.getClass(), method, args);
        if (compatible == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
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

    private static String worldName() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.isSingleplayer()) {
                if (mc.getSingleplayerServer() != null) {
                    return mc.getSingleplayerServer().getWorldData().getLevelName();
                }
            } else if (mc.getCurrentServer() != null) {
                return mc.getCurrentServer().name;
            }
        } catch (Throwable ignored) {
        }
        return "Unknown";
    }

    public static String currentWorldFolder() {
        return sanitizeWorldFolder(worldName());
    }

    private static String sanitizeWorldFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void showCaptureFailedToast() {
        showToast(Component.translatable("text.internet_of_things.phone.camera.capture_failed"));
    }

    private static void showToast(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(msg, true);
            }
        });
    }

    private record CaptureProfile(float cropFactor,
                                  int frameSize,
                                  float exposureStrength,
                                  float contrast,
                                  float noise,
                                  Object levels,
                                  Object hsb,
                                  boolean blackAndWhite,
                                  Object ditherMode,
                                  Object colorPaletteKey,
                                  Object exposureType,
                                  double fov,
                                  Optional<?> filterShader,
                                  Object shutterSpeed,
                                  boolean keepPostEffect,
                                  int exportMultiplier,
                                  boolean shouldUseFlash) {
    }

    private enum PendingAction {
        NONE,
        RETURN_TO_PHONE,
        CLOSE_ALL
    }
}
