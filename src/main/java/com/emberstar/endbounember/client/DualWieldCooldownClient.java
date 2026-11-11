package com.emberstar.endbounember.client;

import net.minecraft.Util;

public final class DualWieldCooldownClient {
    private static int remainingTicks;
    private static int totalTicks;
    private static long lastUpdateMillis;

    private DualWieldCooldownClient() {
    }

    public static void update(int remaining, int total) {
        if (total > 0 && totalTicks == 0) {
            com.emberstar.endbounember.EndboundEmber.LOGGER.info(
                    "[DualWield] 客户端开始填充：total={}tick({}ms)", total, total * 50);
        }
        remainingTicks = Math.max(0, remaining);
        totalTicks = Math.max(0, total);
        lastUpdateMillis = Util.getMillis();
        if (remainingTicks == 0) reset();
    }

    public static float progress() {
        if (totalTicks <= 0) return 0.0F;
        float elapsedTicks = Math.max(0.0F, (Util.getMillis() - lastUpdateMillis) / 50.0F);
        float remaining = Math.max(0.0F, remainingTicks - elapsedTicks);
        return Math.max(0.0F, Math.min(1.0F, 1.0F - remaining / totalTicks));
    }

    public static boolean isCoolingDown() {
        return totalTicks > 0 && progress() < 1.0F;
    }

    public static void reset() {
        remainingTicks = 0;
        totalTicks = 0;
        lastUpdateMillis = 0L;
    }
}
