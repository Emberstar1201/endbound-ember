package com.emberstar.endbounember.compat.ysm;

import org.joml.Matrix4f;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.HaloLayers;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
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
 * 实体根变换上，正好可以叠加已合成的 Head locator 矩阵。
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
     * YSM 头部锚点下的垂直偏移（格）。
     * <p>YSM 的 Head locator 原点落在脚部附近，需要向上抬到头顶上方；
     * 该路径下正值才是向上（与 {@link #VANILLA_HEAD_OFFSET} 方向相反）。
     */
    private static final double YSM_HEAD_OFFSET = 1.00D;
    /** 光环前倾角度（度） */
    private static final float TILT_DEGREES = -12.0F;

    /** 临时诊断：只在首次渲染时打印一次 */
    private static boolean diagnosed;

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
        Minecraft mc = Minecraft.getInstance();
        boolean wearing = wearingHalo(player);
        Matrix4f ysmHead = YsmHeadCapture.get(player.getUUID());
        if (!diagnosed && player == mc.player) {
            diagnosed = true;
            EndboundEmber.LOGGER.info("[光环诊断] YSM 渲染钩子已执行, 佩戴={}, 第一人称={}, YSM矩阵={}, 即将绘制={}",
                    wearing, mc.options.getCameraType().isFirstPerson(),
                    ysmHead == null ? "无" : YsmBoneMath.translation(ysmHead), wearing);
        }
        if (!wearing || player.isInvisible() || player.isSpectator()) {
            return;
        }
        // TODO: 临时允许第一人称渲染以便调试，确认管线通后恢复
        // if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
        //     return;
        // }

        pose.pushPose();
        double verticalOffset;
        if (ysmHead != null) {
            // YSM 模型：直接叠加 YSM 合成出的头部矩阵，光环跟随其 Head locator
            pose.last().pose().mul(ysmHead);
            verticalOffset = YSM_HEAD_OFFSET;
        } else {
            verticalOffset = VANILLA_HEAD_OFFSET;
        }
        // 头部模型原点在头部中心，向上移动到皮肤上方并保留原有视觉偏移
        pose.translate(0.0D, verticalOffset, -0.10D);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(TILT_DEGREES));
        HaloLayers.render(pose, buffers, player.tickCount + Mth.frac(player.getYRot()), 1.0F, 255);
        pose.popPose();
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
