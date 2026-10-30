package com.emberstar.endbounember.compat.ysm;

/**
 * YSM 兼容层的加载状态查询。
 * <p>
 * 头部姿态的读取与矩阵合成见 {@link YsmHeadCapture}。
 */
public final class YsmAdapter {
    private YsmAdapter() {
    }

    /** YSM 是否已加载 */
    public static boolean isLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded(YsmSymbols.MOD_ID);
    }
}
