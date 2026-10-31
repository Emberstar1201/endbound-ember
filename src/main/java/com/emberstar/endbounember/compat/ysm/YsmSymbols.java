package com.emberstar.endbounember.compat.ysm;

// YSM 的底层混淆代码堪称赛博迷宫，为了保护模型资产，它成功做到了连开发者的头发都一起保护没了（指被报错逼疯）。致敬酒石酸菌，你干的混淆确实是一绝。

/**
 * Yes Steve Model（YSM）内部符号表。
 * <p>
 * YSM 的模型渲染核心没有公开的 Java API，其类名与方法名在发布 jar 中被混淆，因此只能按
 * 「精确版本 + 精确符号」的方式反射访问。所有混淆名称集中放在这里，便于升级时逐条核对，
 * 也避免 YSM 的内部结构扩散到项目其它位置。
 * <p>
 * 符号取自 YSM {@code 2.6.5} 的发布 jar；参考 Halo Mod（作者 Azusa_Ke，MIT License，
 * https://github.com/AzusaKe/Halo）的 YSM 兼容实现，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmSymbols {
    /** YSM 的模组 id */
    public static final String MOD_ID = "yes_steve_model";

    /** 已验证符号对应的 YSM 版本 */
    public static final String VERIFIED_VERSION = "2.6.5";

    /** 承载模型渲染的类（继承 {@code LivingEntityRenderer}，混淆名） */
    public static final String GEO_RENDERER = "com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo";

    /** 动画模型基类（混淆名），Head 骨骼层级由它提供 */
    public static final String ANIMATED_GEO_MODEL = "com.elfmcys.yesstevemodel.o0ooO0ooO00oo0o00Oo00000";

    /**
     * 动画对象基类中「取回所属实体」的方法名（混淆名），
     * 描述符 {@code ()Lnet/minecraft/world/entity/Entity;}。
     * <p>
     * YSM 渲染方法的第二个参数是玩家动画状态对象而非实体本身，该对象继承自泛型基类
     * {@code OoO0oo0o0o0oOoo0oOOO0Ooo<TEntity>}，其中 {@code public final TEntity ooo00OoO00OOOO0oOooOo0Oo()}
     * 就是实体访问器。
     */
    public static final String ANIMATABLE_ENTITY_GETTER = "ooo00OoO00OOOO0oOooOo0Oo";

    /** 模型骨骼接口（混淆名），全部骨骼数值都通过它读取 */
    public static final String BONE = "com.elfmcys.yesstevemodel.ooOO0OoOoO0o0o00oO0oo00o";

    /**
     * YSM 的模型渲染接口（混淆名），{@link #GEO_RENDERER} 实现了它。
     * <p>
     * 渲染器本身只负责实体级变换，真正逐骨骼绘制顶点的是这个接口的默认实现，
     * 因此注入接口的默认方法即可覆盖所有 YSM 模型渲染。
     */
    public static final String RENDER_INTERFACE = "com.elfmcys.yesstevemodel.ooo0000oO0O0ooOO0ooOO0o0";

    /**
     * 接口上「带模型实例」的渲染入口（混淆名）。
     * <p>
     * 第 1 个参数就是动画模型，进入时 {@code pose} 已含实体根变换、尚未叠加骨骼变换，
     * 栈顶矩阵即实体根变换。
     */
    public static final String RENDER_MODEL_ENTRY = "oOo0OO0O0o000OO0O000oo0o";

    /**
     * {@link #RENDER_MODEL_ENTRY} 带 {@code RenderType} 重载的完整描述符。
     * <p>
     * 这是 YSM 玩家模型实际调用的逐骨骼渲染入口；{@code remap = false} 下按运行时名匹配。
     */
    public static final String RENDER_MODEL_ENTRY_DESCRIPTOR =
            "(Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;"
                    + "Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    /**
     * 模型上返回 Head locator 骨骼链的方法（混淆名），返回 {@code List<骨骼接口>}。
     * <p>
     * 这条链由 YSM 解析模型 JSON 的 {@code Head} locator 得到，链上每节骨骼都带有当前帧的
     * 旋转、位移与缩放。第三方模型只要定义了 Head locator 就能被正确定位，
     * 不需要按骨骼名做匹配。
     */
    public static final String HEAD_BONES_GETTER = "oO0O000o0oooOOO0O0oooOO0";
    public static final String HEAD_BONES_DESCRIPTOR = "()Ljava/util/List;";

    /*
     * 骨骼数值 getter（混淆名），描述符均为 ()F，单位见各常量注释。
     * 这 12 个值正是 YSM 内部 prepMatrixForLocator 合成 locator 矩阵所用的全部输入。
     */

    /** 绕 X 轴旋转（度） */
    public static final String ROTATION_X = "oOo0OO0O0o000OO0O000oo0o";
    /** 绕 Y 轴旋转（度） */
    public static final String ROTATION_Y = "oOoo00O0o0oO0o0oO00OO0O0";
    /** 绕 Z 轴旋转（度） */
    public static final String ROTATION_Z = "OO000o0ooOooooOOOOO0Ooo0";
    /** 位移 X（模型像素） */
    public static final String POSITION_X = "OOo0o0000Ooo0o00OO0oOOoO";
    /** 位移 Y（模型像素） */
    public static final String POSITION_Y = "Oo0O0OoOo0O0oOoo0000O0oO";
    /** 位移 Z（模型像素） */
    public static final String POSITION_Z = "O0o0OoOOooOo0O0OOoo0Oo00";
    /** 缩放 X */
    public static final String SCALE_X = "OoooO0OO0000O00oo0Oo00OO";
    /** 缩放 Y */
    public static final String SCALE_Y = "oOOO00ooO0oOOoOOo0OoOOOo";
    /** 缩放 Z */
    public static final String SCALE_Z = "ooOooOO0oO00o00o0o0oOOoO";
    /** 旋转轴心 X（模型像素） */
    public static final String PIVOT_X = "OOo0O00Ooo00O0Ooo0OoOo0o";
    /** 旋转轴心 Y（模型像素） */
    public static final String PIVOT_Y = "OO0Oo0O00OOOo0oOo0oooooO";
    /** 旋转轴心 Z（模型像素） */
    public static final String PIVOT_Z = "O0O0Oo0Oooo0OOoOOO0ooo0O";

    private YsmSymbols() {
    }
}
