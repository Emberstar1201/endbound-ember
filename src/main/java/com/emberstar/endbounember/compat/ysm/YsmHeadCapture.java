package com.emberstar.endbounember.compat.ysm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import com.emberstar.endbounember.EndboundEmber;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.LivingEntity;

/**
 * YSM 模型渲染期的 Head locator 捕获。
 * <p>
 * YSM 用自己的渲染管线绘制模型，原版 {@code PlayerModel.head} 不再参与渲染，因此不能再用
 * 原版头部矩阵定位光环。这里在 YSM 渲染模型的入口处抓取「模型实例 + 根矩阵」，反射读出
 * Head 骨骼层级并合成头部矩阵，按实体 UUID 缓存一帧；光环层渲染时直接取用。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmHeadCapture}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmHeadCapture {
    /** 当前帧捕获结果 */
    private static final Map<UUID, CapturedHead> CURRENT = new ConcurrentHashMap<>();

    /** 符号解析失败后本会话不再尝试，避免每帧抛异常 */
    private static volatile boolean adapterBroken;

    /** 临时诊断：钩子入口日志，只在首次执行时打印一次，与实体判断无关 */
    private static volatile boolean hookDiagnosed;

    private YsmHeadCapture() {
    }

    /**
     * 由 YSM 渲染钩子调用：抓取当前渲染实体的头部矩阵。
     *
     * @param entity     正在渲染的实体
     * @param animatedModel YSM 的动画模型实例
     * @param pose       渲染该模型时的变换栈（根矩阵取自栈顶）
     */
    public static void capture(LivingEntity entity, Object animatedModel, PoseStack pose) {
        // 临时诊断：确认 HEAD 注入点拿到了实体与模型
        if (!hookDiagnosed) {
            hookDiagnosed = true;
            EndboundEmber.LOGGER.info("[光环诊断] HEAD 捕获钩子已执行, 实体={}, 模型={}",
                    entity == null ? "null" : entity.getClass().getName(),
                    animatedModel == null ? "null" : animatedModel.getClass().getName());
        }
        if (entity == null || animatedModel == null || pose == null || adapterBroken) {
            return;
        }
        UUID uuid = entity.getUUID();
        if (CURRENT.containsKey(uuid)) {
            return;
        }
        try {
            // 根矩阵是「实体 → 相机」的完整变换；渲染时 pose 栈顶仍是它，
            // 直接叠加会把根变换乘两遍，光环飞到世界坐标外。
            // 因此这里预合成「头部相对根部」的局部矩阵 root⁻¹ × head，
            // 渲染时叠上去正好抵消，得到正确的绝对位置。
            Matrix4f rootMatrix = new Matrix4f(pose.last().pose());
            Matrix4f headMatrix = YsmAdapter.captureHeadMatrix(animatedModel, new Matrix4f(rootMatrix));
            if (headMatrix == null || !YsmBoneMath.isFinite(headMatrix)) {
                return;
            }
            Matrix4f local = rootMatrix.invert(new Matrix4f()).mul(headMatrix);
            if (!YsmBoneMath.isFinite(local)) {
                return;
            }
            CURRENT.put(uuid, new CapturedHead(local));
        } catch (Throwable error) {
            adapterBroken = true;
            EndboundEmber.LOGGER.warn("[YSM Compat] 无法读取 YSM 头部骨骼，光环回退到原版头部锚点：{}: {}",
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    /** 取当前帧该实体的头部矩阵；没有捕获到时返回 {@code null} */
    public static Matrix4f get(UUID uuid) {
        CapturedHead captured = CURRENT.get(uuid);
        return captured == null ? null : captured.headMatrix();
    }

    /** 每帧实体渲染前清空，保证取到的是本帧数据 */
    public static void clearFrame() {
        CURRENT.clear();
    }

    /** 一帧内某个实体的头部捕获结果 */
    public record CapturedHead(Matrix4f headMatrix) {
    }
}
