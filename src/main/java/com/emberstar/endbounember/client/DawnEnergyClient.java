package com.emberstar.endbounember.client;

/**
 * 客户端保存的拂晓能量与成长等级，由 {@code DawnEnergyPayload} 更新。
 */
public final class DawnEnergyClient {
    private static int energy;
    private static int level;

    private DawnEnergyClient() {
    }

    public static void update(int energyValue, int levelValue) {
        energy = energyValue;
        level = levelValue;
    }

    public static int energy() {
        return energy;
    }

    public static int level() {
        return level;
    }

    public static void reset() {
        energy = 0;
        level = 0;
    }
}
