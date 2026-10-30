package com.emberstar.endbounember.compat.ysm;

/**
 * YSM 骨骼数值的换算常量。
 * <p>
 * 骨骼数值与 YSM 内部 locator 的语义一致：pivot 与 position 以模型像素为单位，
 * 旋转以角度为单位。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的同名实现，
 * 许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmBoneMath {
    /** 模型像素到格子的换算 */
    public static final float PIXEL = 1.0F / 16.0F;

    private YsmBoneMath() {
    }
}
