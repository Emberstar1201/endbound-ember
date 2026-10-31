package com.emberstar.endbounember.compat.ysm;

import org.joml.Matrix4f;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.HaloLayers;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.minecraft.util.Mth;

/**
 * 在 YSM 渲染管线内部直接绘制光环。
 * <p>
 * YSM 用自己的渲染器接管了玩家模型，原版 {@code PlayerRenderer} 的 {@code RenderLayer}
 * 列表在 YSM 模型生效时不会被执行，因此不能再依赖「给玩家渲染器加层」的方式。
 * 这里改为在 YSM 渲染接口的默认实现退出点直接补画光环：此时 {@code pose} 仍停留在
 * 实体根变换上，正好可以叠加 {@link YsmHeadCapture} 合成的 Head locator 矩阵。
 * <p>
 * 本类不能放在 {@code compat.ysm.mixin} 包下：Mixin 会把该包视为专属命名空间，
 * 包内的非 Mixin 类不允许被外部直接引用。
 */
public final class YsmHaloRenderer {
    /**
     * 原版头部锚点下的垂直偏移（格）。
     * <p>模型空间的 Y 轴被实体渲染器整体翻转，因此负值才是向上。
     * 头部模型顶端位于 -0.5，这里取 -0.85，让光环悬浮在头顶上方约 0.35 格。
     */
    private static final double VANILLA_HEAD_OFFSET = -0.85D;
    /**
     * 相对 Head locator pivot 的头顶偏移（格）。
     * <p>
     * pivot 是脖子处的旋转轴心，头顶在其上方约 8 个模型像素，即 0.5 格；
     * 再抬高 0.4 格让光环悬浮在头顶上方，避免俯仰时边缘扫到脸部。YSM 骨骼空间 Y 轴朝上，因此取正值。
     */
    private static final double YSM_HEAD_OFFSET = 0.90D;
    /**
     * 潜行时头顶锚点的额外下沉量（格）。
     * <p>
     * YSM 模型潜行时会下蹲，但 Head locator 的 pivot 是静态模型坐标，不会跟着下移，
     * 光环会留在原高度、看着离头越来越远。这里在潜行时同步压低，解除后恢复。
     */
    private static final double YSM_CROUCH_DROP = 0.35D;
    /** 原版模型空间（Y 轴朝下）的光环前倾角度（度） */
    private static final float VANILLA_TILT_DEGREES = 12.0F;
    /** YSM 模型空间（Y 轴朝上）的光环前倾角度（度），符号与原版相反才能得到相同的视觉效果 */
    private static final float YSM_TILT_DEGREES = -12.0F;

    /** 是否启用 YSM 光环渲染，可由客户端指令切换；关闭后光环完全交给原版路径或干脆不画 */
    private static volatile boolean enabled = true;

    private YsmHaloRenderer() {
    }

    /** 当前是否启用 YSM 光环渲染 */
    public static boolean isEnabled() {
        return enabled;
    }

    /** 切换 YSM 光环渲染开关，返回切换后的状态 */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /**
     * 在 YSM 渲染完模型后补画光环。
     *
     * @param entity  正在渲染的实体，由 YSM 渲染方法的 animatable 参数取得
     * @param pose    YSM 渲染该模型时的变换栈
     * @param buffers 渲染缓冲源
     * @param light   打包光照值
     * @param overlay 打包覆盖层值
     */
    public static void render(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!enabled) {
            return;
        }
        try {
            renderHalo(player, pose, buffers);
        } finally {
            // 每次渲染后立即丢弃捕获结果，保证下次渲染重新读取头部姿态
            YsmHeadCapture.remove(player.getUUID());
        }
    }

    private static void renderHalo(Player player, PoseStack pose, MultiBufferSource buffers) {
        boolean wearing = wearingHalo(player);
        Matrix4f ysmHead = YsmHeadCapture.get(player.getUUID());
        if (!wearing || player.isInvisible() || player.isSpectator()) {
            return;
        }

        pose.pushPose();
        try {
            if (ysmHead != null) {
                // ysmHead 是 Head locator 骨骼链相对实体原点的局部矩阵，原点落在 Head 的
                // pivot 上，朝向也与模型头部一致。渲染入口的 pose 栈顶已经带着根变换，
                // 这里直接叠加即可（不能再乘一次根矩阵，否则光环会被推到脚底）。
                pose.last().pose().mul(ysmHead);
                pose.translate(0.0D, YSM_HEAD_OFFSET, 0.0D);
                if (player.isCrouching()) {
                    pose.translate(0.0D, -YSM_CROUCH_DROP, 0.0D);
                }
                pose.translate(0.0D, 0.0D, -0.10D);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(YSM_TILT_DEGREES));
            } else {
                pose.translate(0.0D, VANILLA_HEAD_OFFSET, -0.10D);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(VANILLA_TILT_DEGREES));
            }
            HaloLayers.render(pose, buffers, player.tickCount + Mth.frac(player.getYRot()), 1.0F, 255);
        } finally {
            pose.popPose();
        }
    }

    /** 头盔栏或 Curios 光环饰品栏佩戴均渲染 */
    private static boolean wearingHalo(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.STARDUST_WITCH_HALO.get())) {
            return true;
        }
        // Curios 为运行时软依赖，仅在已加载时检查饰品栏，避免未安装时链接错误
        return ModList.get().isLoaded("curios")
                && CuriosCompat.isEquipped(player, EndboundEmber.STARDUST_WITCH_HALO.get());
    }
}
