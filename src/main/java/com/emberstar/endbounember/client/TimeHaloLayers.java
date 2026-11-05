package com.emberstar.endbounember.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.effect.ModEffects;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 时光光环的时钟渲染：表盘贴图静止、三根指针按时间转动。
 * <p>
 * 贴图分层（assets/endbound_ember/textures/halo/）：
 * <ul>
 * <li>0 号：外环与四角星饰，缓慢自转</li>
 * <li>1 号：罗马数字表盘，保持静止与指针方向对齐</li>
 * <li>2/3/4 号：时针 / 分针 / 秒针，绕盘心在光环平面内转动</li>
 * </ul>
 * 时间来源由配置 {@code timeHaloMode} 决定：{@code real} 使用现实时间，
 * {@code game} 使用世界内时间（一天 24000 tick 折算 24 小时）。
 * <p>
 * 几何绘制方式与 {@link HaloLayers} 保持一致（参考 Halo Mod，作者 Azusa_Ke，
 * MIT License，见 THIRD-PARTY-NOTICES.md）。
 */
public final class TimeHaloLayers {
    private static final ResourceLocation TEX_RING = loc("time_halo_0");
    private static final ResourceLocation TEX_DIAL = loc("time_halo_1");
    private static final ResourceLocation TEX_HOUR = loc("time_halo_2");
    private static final ResourceLocation TEX_MINUTE = loc("time_halo_3");
    private static final ResourceLocation TEX_SECOND = loc("time_halo_4");

    private static ResourceLocation loc(String name) {
        return ResourceLocation.fromNamespaceAndPath("endbound_ember", "textures/halo/" + name + ".png");
    }

    /** 外环边长与自转速度（度/ tick） */
    private static final float RING_SIZE = 1.30F;
    private static final float RING_SPIN = 0.15F;
    /** 分针与秒针按游戏秒离散跳动；现实时间仍逐帧平滑 */
    private static final boolean GAME_CLOCK_STEP_SECONDS = true;
    /** 表盘边长（静止，与指针角度基准对齐） */
    private static final float DIAL_SIZE = 1.10F;
    /** 三根指针的贴图边长（即指针长度）与相对盘心的支点偏移比例 */
    private static final float HOUR_SIZE = 0.42F;
    private static final float MINUTE_SIZE = 0.56F;
    private static final float SECOND_SIZE = 0.63F;
    /** 指针根部相对盘心的偏移（贴图边长的比例），让指针根部落在盘心附近 */
    private static final float HAND_PIVOT_RATIO = 0.30F;

    /** 游戏刻时间源下每个佩戴者各自的走秒缓存（暂停 / 变慢时只影响自己看到的秒针） */
    private static final Map<UUID, Clock> GAME_CLOCKS = new HashMap<>();

    private TimeHaloLayers() {
    }

    /**
     * 绘制时光光环。
     *
     * @param pose       已定位到时钟中心、且已完成背部朝向的变换栈
     *                   （时钟平面为竖直平面，正面朝后，调用方负责定位与倾角）
     * @param buffers    渲染缓冲源
     * @param player     佩戴者，用于读取世界时间与动画刻
     * @param partialTick 当前帧的部分刻，用于平滑指针与外环动画
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Player player, float partialTick) {
        render(pose, buffers, player, 0.0F, partialTick);
    }

    /**
     * 绘制时光光环，并可按层错开极小的深度。
     *
     * @param pose    已定位到时钟中心、且已完成背部朝向的变换栈
     *                （时钟平面为竖直平面，正面朝后，调用方负责定位与倾角）
     * @param buffers 渲染缓冲源
     * @param player  佩戴者，用于读取世界时间与动画刻
     * @param layerGap 每层沿钟面法线朝相机方向依次抬高的距离（本地单位）
     * @param partialTick 当前帧的部分刻，用于平滑指针与外环动画
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Player player, float layerGap,
            float partialTick) {
        float time = (float) (player.level().getGameTime() + partialTick);
        float brightness = 1.0F;
        int alpha = 255;

        // 0 号外环：缓慢自转（最里层）
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * RING_SPIN));
        drawQuad(pose, buffers.getBuffer(RenderType.entityTranslucent(TEX_RING)), RING_SIZE, brightness, alpha);
        pose.popPose();

        // 1 号表盘：静止，保证刻度与指针角度基准对齐
        pose.pushPose();
        pose.translate(0.0D, layerGap, 0.0D);
        drawQuad(pose, buffers.getBuffer(RenderType.entityTranslucent(TEX_DIAL)), DIAL_SIZE, brightness, alpha);
        pose.popPose();

        // 2/3/4 号指针：按时间源绕盘心顺时针转动（12 点方向对应贴图 -Z）
        float secondsInDay = secondsInDay(player, partialTick);
        if (GAME_CLOCK_STEP_SECONDS && Config.TIME_HALO_MODE.get() == Config.TimeHaloMode.GAME
                && !TimeTurbulenceLevels.isTimeTurbulence(player.level())) {
            secondsInDay = (float) Math.floor(secondsInDay);
        }
        float hourAngle = secondsInDay / 43200.0F * 360.0F;
        float minuteAngle = secondsInDay % 3600.0F / 3600.0F * 360.0F;
        float secondAngle = secondsInDay % 60.0F / 60.0F * 360.0F;
        float handGap = Math.max(layerGap, 0.0015F);

        drawHand(pose, buffers, TEX_HOUR, HOUR_SIZE, -hourAngle, handGap, brightness, alpha);
        drawHand(pose, buffers, TEX_MINUTE, MINUTE_SIZE, -minuteAngle, handGap * 2.0F, brightness, alpha);
        drawHand(pose, buffers, TEX_SECOND, SECOND_SIZE, -secondAngle, handGap * 3.0F, brightness, alpha);
    }

    /** 当前一天内的秒数（含小数），由配置决定使用现实时间或世界内时间 */
    public static float secondsInDay(Player player) {
        return secondsInDay(player, 0.0F);
    }

    /** 当前渲染帧的一天内秒数，可用部分刻平滑插值 */
    public static float secondsInDay(Player player, float partialTick) {
        if (Config.TIME_HALO_MODE.get() == Config.TimeHaloMode.GAME) {
            return gameClock(player, partialTick);
        }
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int milli = calendar.get(Calendar.MILLISECOND);
        return (hour % 12) * 3600.0F + minute * 60.0F + second + milli / 1000.0F;
    }

    /**
     * 游戏刻时间源的本地走秒：平时按世界时间同步推进，
     * 被时间变慢 / 暂停影响时按倍率推进或直接停住，让秒针表现出对应的迟滞。
     * <p>原版一天 24000 tick 折算 24 小时（1 tick = 3.6 秒），
     * 且 dayTime 0 对应清晨 6 点，因此换算时先加 18000 tick 把清晨对齐到表盘 6 点方向。
     */
    private static float gameClock(Player player, float partialTick) {
        Clock clock = GAME_CLOCKS.computeIfAbsent(player.getUUID(), id -> new Clock());
        long dayTime = player.level().getDayTime();
        if (clock.lastDayTime == Long.MIN_VALUE) {
            clock.seconds = ((dayTime + 18000L) % 24000L) * 3.6F;
            clock.lastDayTime = dayTime;
            return clock.seconds;
        }
        long delta = dayTime - clock.lastDayTime;
        if (delta != 0L) {
            float scale = clockScale(player);
            float next = clock.seconds + delta * 3.6F * scale;
            clock.seconds = ((next % 86400.0F) + 86400.0F) % 86400.0F;
            clock.lastDayTime = dayTime;
        }
        return clock.seconds + partialTick * 3.6F * clockScale(player);
    }

    /** 秒针速度倍率：暂停 0，变慢取配置倍率，平时 1 */
    private static float clockScale(Player player) {
        if (player.hasEffect(ModEffects.TIME_STOP)) return 0.0F;
        if (player.hasEffect(ModEffects.TIME_SLOW)) return (float) (double) Config.TIME_HALO_SLOW_FACTOR.get();
        return 1.0F;
    }

    /** 每个玩家的游戏刻走秒缓存 */
    private static final class Clock {
        private long lastDayTime = Long.MIN_VALUE;
        private float seconds;
    }

    /**
     * 绘制一根指针：先按层序抬高一点避免与表盘共面，再旋转到目标角度，
     * 随后沿贴图 -Z（12 点方向）平移，让指针根部位于盘心、尖端指向对应刻度。
     */
    private static void drawHand(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
            float size, float angleDegrees, float depthOffset, float brightness, int alpha) {
        pose.pushPose();
        pose.translate(0.0D, depthOffset, 0.0D);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleDegrees));
        // 四边形中心在原点，把「距根部 0.3 倍边长」的支点移到盘心（根部在 +Z）
        pose.translate(0.0D, 0.0D, -(size / 2.0F - size * HAND_PIVOT_RATIO));
        drawQuad(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), size, brightness, alpha);
        pose.popPose();
    }

    /** 绘制一张居中于原点的贴图四边形（水平平面，法线朝上），与 HaloLayers 一致 */
    private static void drawQuad(PoseStack pose, VertexConsumer buffer, float size, float brightness, int alpha) {
        Matrix4f matrix = pose.last().pose();
        float half = size / 2.0F;
        int light = 0xF000F0;   // 自发光：不受环境光影响
        int a = Mth.clamp(alpha, 0, 255);
        int c = Mth.clamp((int) (255 * brightness), 0, 255);
        buffer.addVertex(matrix, -half, 0.0F, -half).setColor(c, c, c, a).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, half, 0.0F, -half).setColor(c, c, c, a).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, half, 0.0F, half).setColor(c, c, c, a).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, -half, 0.0F, half).setColor(c, c, c, a).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }
}
