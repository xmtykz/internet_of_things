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

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class ExposureRuntime {
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
        mc.setScreen(null);
        return true;
    }

    private static void ensureListeners() {
        if (listenersRegistered) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ExposureRuntime::onUseKeyTriggered);
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

        // Best-effort: ask Exposure client to close viewfinder and reset camera.
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
        insertedSlot = -1;
        previousStack = ItemStack.EMPTY;
        cameraStack = ItemStack.EMPTY;
        cameraItem = null;
    }

    private static void onUseKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!sessionActive || pendingAction != PendingAction.NONE) {
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);

        long now = System.currentTimeMillis();
        if (now - lastCaptureAt < CAPTURE_COOLDOWN_MS) {
            return;
        }
        lastCaptureAt = now;
        capturePhotoReflective();
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
        if (pendingAction == PendingAction.NONE) {
            return;
        }

        PendingAction action = pendingAction;
        cleanup();
        if (action == PendingAction.RETURN_TO_PHONE) {
            mc.setScreen(new PhoneScreen());
        } else {
            mc.setScreen(null);
        }
    }

    private static void capturePhotoReflective() {
        try {
            String worldFolder = sanitizeWorldFolder(worldName());
            String fileName = FILE_NAME_FORMATTER.format(LocalDateTime.now());

            Class<?> taskClass = Class.forName("io.github.mortuusars.exposure.util.cycles.task.Task");
            Class<?> captureActionClass = Class.forName("io.github.mortuusars.exposure.client.capture.action.CaptureAction");
            Class<?> captureClass = Class.forName("io.github.mortuusars.exposure.client.capture.Capture");
            Class<?> exposureClientClass = Class.forName("io.github.mortuusars.exposure.ExposureClient");

            Object screenshotTask = captureClass.getMethod("screenshot").invoke(null);
            Object hideGuiAction = captureActionClass.getMethod("hideGui").invoke(null);

            Object actions = Array.newInstance(captureActionClass, 1);
            Array.set(actions, 0, hideGuiAction);

            Object capture = captureClass
                    .getMethod("of", taskClass, actions.getClass())
                    .invoke(null, screenshotTask, actions);

            Object taskAfterHandle = capture.getClass()
                    .getMethod("handleErrorAndGetResult", Consumer.class)
                    .invoke(capture, (Consumer<Object>) err ->
                            showToast(Component.translatable("text.internet_of_things.phone.camera.capture_failed")));

            Object exportTask = taskAfterHandle.getClass()
                    .getMethod("acceptAsync", Consumer.class)
                    .invoke(taskAfterHandle, (Consumer<Object>) image ->
                            exportImageReflective(image, fileName, worldFolder));

            Object cycles = exposureClientClass.getMethod("cycles").invoke(null);
            cycles.getClass().getMethod("enqueueTask", taskClass).invoke(cycles, exportTask);
        } catch (Throwable ignored) {
            showToast(Component.translatable("text.internet_of_things.phone.camera.capture_failed"));
        }
    }

    private static void exportImageReflective(Object image, String fileName, String worldFolder) {
        try {
            Class<?> imageClass = Class.forName("io.github.mortuusars.exposure.client.image.Image");
            Class<?> imageExporterClass = Class.forName("io.github.mortuusars.exposure.client.export.ImageExporter");

            Object exporter = imageExporterClass
                    .getConstructor(imageClass, String.class)
                    .newInstance(imageClass.cast(image), fileName);

            exporter = imageExporterClass.getMethod("withFolder", String.class).invoke(exporter, EXPORT_FOLDER);
            exporter = imageExporterClass.getMethod("organizeByWorld", String.class).invoke(exporter, worldFolder);
            exporter = imageExporterClass.getMethod("onExport", Consumer.class).invoke(exporter, (Consumer<File>) file ->
                    showToast(Component.translatable("text.internet_of_things.phone.camera.saved", file.getName())));

            imageExporterClass.getMethod("export").invoke(exporter);
        } catch (Throwable ignored) {
            showToast(Component.translatable("text.internet_of_things.phone.camera.capture_failed"));
        }
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

    private static String sanitizeWorldFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void showToast(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(msg, true);
            }
        });
    }

    private enum PendingAction {
        NONE,
        RETURN_TO_PHONE,
        CLOSE_ALL
    }
}
