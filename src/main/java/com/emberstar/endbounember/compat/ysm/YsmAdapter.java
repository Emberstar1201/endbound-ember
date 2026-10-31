package com.emberstar.endbounember.compat.ysm;

// 感谢酒石酸菌大佬的包容与支持，为了兼容这堆混淆代码，我的头发真的辛苦了。

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
