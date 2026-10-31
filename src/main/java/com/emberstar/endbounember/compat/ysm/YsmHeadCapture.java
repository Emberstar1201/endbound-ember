package com.emberstar.endbounember.compat.ysm;

// 感谢酒石酸菌大佬的包容与支持，为了兼容这堆混淆代码，我的头发真的辛苦了。

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.world.entity.LivingEntity;

/**
 * YSM 模型渲染期的头顶锚点捕获。
 * <p>
 * YSM 用自己的渲染管线绘制模型，原版 {@code PlayerModel.head} 不再参与渲染，因此不能再用
 * 原版头部矩阵定位光环。
 * <p>
 * 实测（YSM 2.6.5）确认：逐骨骼的矩阵变换全部由 C++ 侧的 native 方法完成，Java 层既拿不到
 * 骨骼的动画旋转（骨骼对象的旋转字段恒为 0），也拿不到 {@code PoseStack} 的变化
 * （渲染入口与返回时的栈顶矩阵完全一致）。因此这里只从骨骼链读取 <b>pivot</b>：
 * 它是模型定义里的静态坐标，能准确给出头顶高度；头部姿态则改用玩家视角近似，
 * 见 {@link YsmHaloRenderer}。
 * <p>
 * 注意：YSM 的混淆器会在同一个类里复用方法名，例如模型类上的
 * {@code oO0O000o0oooOOO0O0oooOO0()} 返回的是嵌套的 {@code List<List<骨骼>>} 而非骨骼链，
 * 因此这里不按固定符号名取链，而是按「链末骨骼名」动态识别，避免被同名方法误导。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmHeadCapture}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmHeadCapture {
    /** 当前帧捕获结果 */
    private static final Map<UUID, CapturedHead> CURRENT = new ConcurrentHashMap<>();

    /** 模型类上的 Head 骨骼链 getter；{@code null} 表示尚未解析 */
    private static volatile Method headBonesGetter;
    /** 符号解析失败后本会话不再尝试，避免每帧抛异常 */
    private static volatile boolean adapterBroken;

    private YsmHeadCapture() {
    }

    /**
     * 在 YSM 渲染入口记录该实体的头顶锚点。
     *
     * @param entity 正在渲染的实体
     * @param model  YSM 的动画模型实例，用于读取 Head 骨骼链
     */
    public static void begin(LivingEntity entity, Object model) {
        if (entity == null) {
            return;
        }
        UUID uuid = entity.getUUID();
        CURRENT.remove(uuid);
        CapturedHead captured = captureHeadAnchor(model);
        if (captured != null) {
            CURRENT.put(uuid, captured);
        }
    }

    /** 从模型的 Head 骨骼链读取头顶锚点 */
    private static CapturedHead captureHeadAnchor(Object model) {
        if (model == null || adapterBroken) {
            return null;
        }
        try {
            Method getter = resolveHeadBonesGetter(model.getClass(), model);
            if (getter == null) {
                return null;
            }
            Object raw = getter.invoke(model);
            if (!(raw instanceof List<?> bones) || bones.isEmpty()) {
                return null;
            }
            Object tail = bones.get(bones.size() - 1);
            Vector3f pivot = new Vector3f(
                    readFloat(tail, YsmSymbols.PIVOT_X) * YsmBoneMath.PIXEL,
                    readFloat(tail, YsmSymbols.PIVOT_Y) * YsmBoneMath.PIXEL,
                    readFloat(tail, YsmSymbols.PIVOT_Z) * YsmBoneMath.PIXEL);
            if (!Float.isFinite(pivot.x) || !Float.isFinite(pivot.y) || !Float.isFinite(pivot.z)) {
                return null;
            }
            return new CapturedHead(pivot);
        } catch (Throwable error) {
            adapterBroken = true;
            EndboundEmber.LOGGER.warn("[YSM Compat] 无法读取 YSM Head 骨骼链，光环回退到原版头部锚点：{}: {}",
                    error.getClass().getSimpleName(), error.getMessage());
            return null;
        }
    }

    /**
     * 在模型类上找到返回 Head 骨骼链的方法。
     * <p>
     * 逐个尝试无参、返回 {@code List} 的公开方法，调用后检查列表元素是否为 YSM 骨骼、
     * 且链末骨骼名包含 {@code head}。命中后缓存，后续帧不再遍历。
     */
    private static Method resolveHeadBonesGetter(Class<?> modelClass, Object model) {
        Method cached = headBonesGetter;
        if (cached != null) {
            return cached;
        }
        for (Method method : modelClass.getMethods()) {
            if (method.getParameterCount() != 0 || !List.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object raw = method.invoke(model);
                if (!(raw instanceof List<?> bones) || bones.isEmpty()) {
                    continue;
                }
                if (!boneName(bones.get(bones.size() - 1)).toLowerCase(Locale.ROOT).contains("head")) {
                    continue;
                }
                headBonesGetter = method;
                EndboundEmber.LOGGER.info("[YSM Compat] 已定位 Head 骨骼链 getter：{}（{} 节骨骼）",
                        method.getName(), bones.size());
                return method;
            } catch (Throwable ignored) {
                // 该候选方法不可调用，继续尝试下一个
            }
        }
        adapterBroken = true;
        EndboundEmber.LOGGER.warn("[YSM Compat] 未能在 {} 上找到 Head 骨骼链，光环回退到原版头部锚点",
                modelClass.getName());
        return null;
    }

    /** 读取骨骼名称；读取失败时返回空串 */
    private static String boneName(Object bone) {
        if (bone == null) {
            return "";
        }
        try {
            Object name = bone.getClass().getMethod(YsmSymbols.BONE_NAME_GETTER).invoke(bone);
            return name instanceof String value ? value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 反射调用无参 float getter */
    private static float readFloat(Object bone, String name) throws ReflectiveOperationException {
        Method method = bone.getClass().getMethod(name);
        return ((Number) method.invoke(bone)).floatValue();
    }

    /** 取当前帧该实体的头顶锚点；没有捕获到时返回 {@code null} */
    public static CapturedHead get(UUID uuid) {
        return CURRENT.get(uuid);
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
     * 一帧内某个实体的头顶锚点。
     *
     * @param pivot Head locator 的 pivot 位置（格），即头部旋转轴心
     */
    public record CapturedHead(Vector3f pivot) {
    }
}
