package com.emberstar.endbounember.compat.ysm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import com.emberstar.endbounember.EndboundEmber;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.LivingEntity;

/**
 * YSM 模型渲染期的 Head locator 矩阵捕获。
 * <p>
 * YSM 用自己的渲染管线绘制模型，原版 {@code PlayerModel.head} 不再参与渲染，因此不能再用
 * 原版头部矩阵定位光环。
 * <p>
 * YSM 的逐骨骼变换由 native 方法完成，Java 侧既拿不到渲染中的 {@code PoseStack} 变化，
 * 也拿不到渲染器里骨骼对象的动画数值。但模型上有一个由 YSM 自己解析好的
 * <b>Head locator 骨骼链</b>（{@link YsmSymbols#HEAD_BONES_GETTER}），链上每节骨骼都带有
 * 当前帧的旋转、位移与缩放。这里读取这条链的 12 个数值，按 YSM 内部
 * {@code prepMatrixForLocator} 的规则在 Java 侧合成矩阵，就得到与 YSM 一致的头部锚点。
 * <p>
 * 因为锚点来自模型自己声明的 {@code Head} locator，任何第三方模型只要定义了该 locator
 * 就能被正确定位，不需要按骨骼名做匹配。
 * <p>
 * 保存的矩阵是<b>相对实体原点</b>的 Head locator 结果：绘制时光环是在 YSM 渲染入口同一个
 * {@code PoseStack} 上叠加，栈顶已含根变换，若这里再乘一次根矩阵就会重复变换，
 * 光环会被推到脚底。因此捕获时从单位矩阵开始合成，不引入任何根变换。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmV265Adapter} 与 {@code YsmHeadCapture}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmHeadCapture {
    /** 当前帧捕获结果 */
    private static final Map<UUID, CapturedHead> CURRENT = new ConcurrentHashMap<>();

    /** 反射句柄；{@code null} 表示尚未解析 */
    private static volatile Accessors accessors;
    /** 符号解析失败后本会话不再尝试，避免每帧抛异常 */
    private static volatile boolean adapterBroken;
    /** 临时诊断：只在首次捕获时打印一次 */
    private static volatile boolean diagnosed;

    private YsmHeadCapture() {
    }

    /**
     * 在 YSM 渲染入口记录该实体的 Head locator 矩阵。
     *
     * @param entity 正在渲染的实体
     * @param model  YSM 的动画模型实例，用于读取 Head locator 骨骼链
     * @param pose   YSM 渲染该模型时的变换栈，进入时栈顶即实体根变换
     */
    public static void begin(LivingEntity entity, Object model, PoseStack pose) {
        if (entity == null || model == null || pose == null) {
            return;
        }
        UUID uuid = entity.getUUID();
        CURRENT.remove(uuid);
        // 只保存 Head locator 骨骼链本身（相对实体原点）的结果，不含任何根变换：
        // 绘制时光环是在 YSM 渲染入口同一个 PoseStack 上叠加，栈顶已经带着根变换，
        // 这里再乘一次就会重复，光环会被推到脚底。
        Matrix4f head = captureHeadMatrix(model, new Matrix4f());
        if (head != null) {
            CURRENT.put(uuid, new CapturedHead(head));
            diagnose(uuid, pose, head);
        }
    }

    /** 临时诊断：首次捕获时输出根矩阵与头部矩阵，便于核对坐标系是否一致 */
    private static void diagnose(UUID uuid, PoseStack pose, Matrix4f head) {
        if (diagnosed) {
            return;
        }
        diagnosed = true;
        Matrix4f root = pose.last().pose();
        EndboundEmber.LOGGER.info("[光环诊断] 根矩阵平移=({}, {}, {}), 头部矩阵平移=({}, {}, {})",
                String.format("%.3f", root.m30()), String.format("%.3f", root.m31()),
                String.format("%.3f", root.m32()),
                String.format("%.3f", head.m30()), String.format("%.3f", head.m31()),
                String.format("%.3f", head.m32()));
    }

    /**
     * 读取 Head locator 骨骼链并合成头部矩阵。
     *
     * @param model YSM 动画模型实例
     * @param root  合成起点矩阵
     * @return 头部矩阵；模型没有可用 Head locator 时返回 {@code null}
     */
    private static Matrix4f captureHeadMatrix(Object model, Matrix4f root) {
        if (adapterBroken) {
            return null;
        }
        try {
            Accessors a = accessorsFor(model);
            Object value = a.headBones.invoke(model);
            if (!(value instanceof List<?> bones) || bones.isEmpty()) {
                return null;
            }
            List<YsmBoneMath.BonePose> poses = new ArrayList<>(bones.size());
            for (Object bone : bones) {
                if (bone == null || !a.boneClass.isInstance(bone)) {
                    return null;
                }
                poses.add(new YsmBoneMath.BonePose(
                        floatValue(a.rotationX.invoke(bone)),
                        floatValue(a.rotationY.invoke(bone)),
                        floatValue(a.rotationZ.invoke(bone)),
                        floatValue(a.positionX.invoke(bone)),
                        floatValue(a.positionY.invoke(bone)),
                        floatValue(a.positionZ.invoke(bone)),
                        floatValue(a.scaleX.invoke(bone)),
                        floatValue(a.scaleY.invoke(bone)),
                        floatValue(a.scaleZ.invoke(bone)),
                        floatValue(a.pivotX.invoke(bone)),
                        floatValue(a.pivotY.invoke(bone)),
                        floatValue(a.pivotZ.invoke(bone))));
            }
            return YsmBoneMath.composeHeadMatrix(root, poses);
        } catch (Throwable error) {
            adapterBroken = true;
            EndboundEmber.LOGGER.warn("[YSM Compat] 无法读取 YSM Head locator，光环回退到原版头部锚点：{}: {}",
                    error.getClass().getSimpleName(), error.getMessage());
            return null;
        }
    }

    /** 解析并缓存反射句柄；模型类不一致时重新解析 */
    private static Accessors accessorsFor(Object model) throws ReflectiveOperationException {
        Accessors current = accessors;
        if (current != null && current.modelClass.isInstance(model)) {
            return current;
        }
        synchronized (YsmHeadCapture.class) {
            if (accessors == null || !accessors.modelClass.isInstance(model)) {
                accessors = Accessors.resolve(model.getClass().getClassLoader());
                EndboundEmber.LOGGER.info("[YSM Compat] 已解析 YSM Head locator 符号，模型类={}",
                        accessors.modelClass.getName());
            }
            return accessors;
        }
    }

    private static float floatValue(Object value) {
        return ((Number) value).floatValue();
    }

    /** 取当前帧该实体的头部矩阵；没有捕获到时返回 {@code null} */
    public static Matrix4f get(UUID uuid) {
        CapturedHead captured = CURRENT.get(uuid);
        return captured == null ? null : new Matrix4f(captured.headMatrix());
    }

    /** 丢弃某个实体的捕获结果 */
    public static void remove(UUID uuid) {
        CURRENT.remove(uuid);
    }

    /** 兜底清空：渲染阶段结束后调用，避免异常路径残留数据 */
    public static void clearFrame() {
        CURRENT.clear();
    }

    /**
     * 一帧内某个实体的头部锚点。
     *
     * @param headMatrix 相对实体原点的 Head locator 矩阵
     */
    public record CapturedHead(Matrix4f headMatrix) {
    }

    /** 反射句柄集合，一次性解析后复用 */
    private record Accessors(
            Class<?> modelClass,
            Class<?> boneClass,
            Method headBones,
            Method rotationX,
            Method rotationY,
            Method rotationZ,
            Method positionX,
            Method positionY,
            Method positionZ,
            Method scaleX,
            Method scaleY,
            Method scaleZ,
            Method pivotX,
            Method pivotY,
            Method pivotZ) {

        private static Accessors resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> modelClass = Class.forName(YsmSymbols.ANIMATED_GEO_MODEL, false, loader);
            Class<?> boneClass = Class.forName(YsmSymbols.BONE, false, loader);
            return new Accessors(
                    modelClass,
                    boneClass,
                    modelClass.getMethod(YsmSymbols.HEAD_BONES_GETTER),
                    boneClass.getMethod(YsmSymbols.ROTATION_X),
                    boneClass.getMethod(YsmSymbols.ROTATION_Y),
                    boneClass.getMethod(YsmSymbols.ROTATION_Z),
                    boneClass.getMethod(YsmSymbols.POSITION_X),
                    boneClass.getMethod(YsmSymbols.POSITION_Y),
                    boneClass.getMethod(YsmSymbols.POSITION_Z),
                    boneClass.getMethod(YsmSymbols.SCALE_X),
                    boneClass.getMethod(YsmSymbols.SCALE_Y),
                    boneClass.getMethod(YsmSymbols.SCALE_Z),
                    boneClass.getMethod(YsmSymbols.PIVOT_X),
                    boneClass.getMethod(YsmSymbols.PIVOT_Y),
                    boneClass.getMethod(YsmSymbols.PIVOT_Z));
        }
    }
}
