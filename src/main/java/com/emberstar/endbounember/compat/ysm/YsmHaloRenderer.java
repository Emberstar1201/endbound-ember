package com.emberstar.endbounember.compat.ysm;

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
 * 实体根变换上，正好可以叠加头顶锚点。
 * <p>
 * 头顶位置取自 Head locator 的 pivot（静态模型坐标，准确可靠）；头部姿态无法从 YSM 取得
 * ——实测 2.6.5 的骨骼旋转字段恒为 0、渲染前后 {@code PoseStack} 完全不变，骨骼矩阵全部在
 * C++ 侧计算——因此改用玩家视角的俯仰与朝向近似，还原「低头时光环转到前方」的观感。
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
     * 俯仰角上限（度），抬头与低头双向生效。
     * <p>
     * 光环会随头顶面一起翻转：抬头时头顶面朝后倾，光环绕过去后会出现在脸部附近，
     * 这是最容易遮脸的情况；低头时则朝前方翻。这里把参与旋转的俯仰角限制在该范围内，
     * 让光环最多停在头顶前 / 后上方，不靠近脸部。
     */
    private static final float MAX_PITCH_DEGREES = 30.0F;
    /**
     * 潜行时头顶锚点的额外下沉量（格）。
     * <p>
     * YSM 模型潜行时会下蹲，但 Head locator 的 pivot 是静态模型坐标，不会跟着下移，
     * 光环会留在原高度、看着离头越来越远。这里在潜行时同步压低，解除后恢复。
     */
    private static final double YSM_CROUCH_DROP = 0.35D;
    /**
     * 抬头到最大角度时，光环沿头部局部 Z 轴向脑后偏移的距离（格）。
     * <p>
     * 抬头时光环倾斜后边缘会垂到脸前，转动视角时容易扫进第一人称视野；
     * 按抬头幅度线性插值到该值，把光环推离脸部。
     */
    private static final double YSM_LOOK_UP_BACK = 0.30D;
    /** 原版模型空间（Y 轴朝下）的光环前倾角度（度） */
    private static final float VANILLA_TILT_DEGREES = 12.0F;
    /** YSM 模型空间（Y 轴朝上）的光环前倾角度（度），符号与原版相反才能得到相同的视觉效果 */
    private static final float YSM_TILT_DEGREES = -12.0F;

    private YsmHaloRenderer() {
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
        try {
            renderHalo(player, pose, buffers);
        } finally {
            // 每次渲染后立即丢弃捕获结果，保证下次渲染重新读取头部姿态
            YsmHeadCapture.remove(player.getUUID());
        }
    }

    private static void renderHalo(Player player, PoseStack pose, MultiBufferSource buffers) {
        boolean wearing = wearingHalo(player);
        YsmHeadCapture.CapturedHead ysmHead = YsmHeadCapture.get(player.getUUID());
        if (!wearing || player.isInvisible() || player.isSpectator()) {
            return;
        }

        pose.pushPose();
        try {
            if (ysmHead != null) {
                // YSM 的头部姿态全部在 C++ 侧计算，Java 层拿不到，只能用玩家视角近似：
                // 1. 平移到 Head locator 的 pivot（脖子处的旋转轴心）；
                // 2. 叠加视角俯仰与朝向，使抬升方向跟随头部朝向；
                // 3. 沿头部局部 Y 轴上抬到头顶。
                // 这样低头时顶面转到前方，光环也跟着转到前方；抬头则转到脑后。
                pose.translate(ysmHead.pivot().x(), ysmHead.pivot().y(), ysmHead.pivot().z());
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(player.getYRot()));
                // 俯仰角做上限限制，避免低头时光环翻到脸前
                float pitch = Mth.clamp(player.getXRot(), -MAX_PITCH_DEGREES, MAX_PITCH_DEGREES);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                pose.translate(0.0D, YSM_HEAD_OFFSET, 0.0D);
                if (player.isCrouching()) {
                    pose.translate(0.0D, -YSM_CROUCH_DROP, 0.0D);
                }
                pose.translate(0.0D, 0.0D, -0.10D);
                // 抬头时光环倾斜后边缘会垂到脸前，转视角时容易扫进第一人称视野。
                // 这里按抬头幅度把光环整体往脑后推，抬头越多推得越远。
                if (pitch < 0.0F) {
                    pose.translate(0.0D, 0.0D, YSM_LOOK_UP_BACK * (-pitch / MAX_PITCH_DEGREES));
                }
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(YSM_TILT_DEGREES));
            } else {
                pose.translate(0.0D, VANILLA_HEAD_OFFSET, -0.10D);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(VANILLA_TILT_DEGREES));
            }
            // 低头时整个坐标系绕 X 轴倾斜，光环平面被翻过去，绕局部 Y 轴的自转在屏幕上
            // 会投成相反方向；这里按俯仰符号翻转自转，保证视觉上始终同向旋转。
            float spinSign = player.getXRot() > 0.0F ? -1.0F : 1.0F;
            HaloLayers.render(pose, buffers, player.tickCount + Mth.frac(player.getYRot()), 1.0F, 255, spinSign);
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
