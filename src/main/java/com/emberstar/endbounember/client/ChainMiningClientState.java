package com.emberstar.endbounember.client;

import com.emberstar.endbounember.world.ChainMiningState;

public final class ChainMiningClientState {
    private static volatile boolean enabled;
    private static volatile int mode;

    private ChainMiningClientState() {
    }

    public static void set(boolean active, int currentMode) {
        enabled = active;
        mode = Math.floorMod(currentMode, ChainMiningState.Mode.values().length);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static ChainMiningState.Mode mode() {
        return ChainMiningState.Mode.values()[mode];
    }
}
