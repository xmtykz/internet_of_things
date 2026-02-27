package com.ykz.iot.compat.exposure;

import net.neoforged.fml.ModList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExposureCompat {
    public static final String MOD_ID = "exposure";
    public static final String MIN_VERSION = "1.9.13";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private ExposureCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static String getLoadedVersion() {
        return ModList.get()
                .getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    public static boolean isCompatible() {
        if (!isLoaded()) {
            return false;
        }
        return atLeast(getLoadedVersion(), 1, 9, 13);
    }

    public static boolean startPhoneCameraSession() {
        if (!isCompatible()) {
            return false;
        }
        try {
            Class<?> runtime = Class.forName("com.ykz.iot.compat.exposure.ExposureRuntime");
            Object result = runtime.getMethod("startSession").invoke(null);
            return result instanceof Boolean ok && ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean atLeast(String version, int minMajor, int minMinor, int minPatch) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            return false;
        }

        int major = parse(matcher.group(1));
        int minor = parse(matcher.group(2));
        int patch = parse(matcher.group(3));

        if (major != minMajor) {
            return major > minMajor;
        }
        if (minor != minMinor) {
            return minor > minMinor;
        }
        return patch >= minPatch;
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }
}

