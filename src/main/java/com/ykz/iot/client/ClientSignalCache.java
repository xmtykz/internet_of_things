package com.ykz.iot.client;

public final class ClientSignalCache {
    private static volatile int signalLevel = 0;

    private ClientSignalCache() {}

    public static int getSignalLevel() {
        return signalLevel;
    }

    public static void setSignalLevel(int level) {
        signalLevel = level;
    }
}