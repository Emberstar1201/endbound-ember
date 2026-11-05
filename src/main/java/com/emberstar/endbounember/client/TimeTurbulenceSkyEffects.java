package com.emberstar.endbounember.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 时间乱流的天空：绝对纯白（#FFFFFF）背景上挂着一座巨大的时钟。
 * <p>
 * 时钟复用时光光环的贴图与指针逻辑（{@link TimeHaloLayers}），只是放大后固定在
 * 天空的固定方位：正北方向、仰角 {@link #CLOCK_ELEVATION} 度、距离 {@link #CLOCK_DISTANCE} 格。
 * 因为维度类型里 {@code effects} 指向 {@code endbound_ember:time_turbulence}，
 * 原版的日月、星辰、云层都不会出现，天空里只有这一座钟。
 */
public class TimeTurbulenceSkyEffects extends DimensionSpecialEffects {
    /** 时钟到相机的距离（格），落在远裁剪面之内 */
    private static final double CLOCK_DISTANCE = 80.0D;
    /** 时钟中心的仰角（度） */
    private static final float CLOCK_ELEVATION = 35.0F;
    /** 时钟整体的放大倍率：光环外环边长 1.30，放大后约为 92 格宽（角直径约 60 度） */
    private static final float CLOCK_SCALE = 71.0F;
    /**
     * 钟面各层之间的深度间隔（缩放前的本地单位）。
     * <p>
     * 外环、表盘与三根指针原本共面，深度测试会在重叠处随机判定谁在前，
     * 动画时表现为一小块抽搐。按绘制顺序每层朝相机方向抬高一点点
     * （70 倍缩放后约 0.14 格），即可稳定分层且肉眼无感。
     */
    private static final float CLOCK_LAYER_GAP = 0.002F;

    public TimeTurbulenceSkyEffects() {
        // 云层高度 NaN 关闭云渲染；天空类型 NONE 表示完全由本类接管
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // 雾气恒为纯白
        return new Vec3(1.0D, 1.0D, 1.0D);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f frustumMatrix,
            Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        // 背景强制清成绝对纯白（原版天空色来自生物群系，不会是纯白）
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        if (player == null) {
            return true;
        }

        // 时钟在天上，不能被雾吃掉
        RenderSystem.setShaderFogStart(1.0E6F);
        RenderSystem.setShaderFogEnd(2.0E6F);

        double elevation = Math.toRadians(CLOCK_ELEVATION);
        PoseStack pose = new PoseStack();
        pose.mulPose(frustumMatrix);
        // 把钟面摆到相机正前方偏上的位置，并让钟面正对相机、贴图顶端朝上
        pose.translate(0.0D, CLOCK_DISTANCE * Math.sin(elevation), -CLOCK_DISTANCE * Math.cos(elevation));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F + CLOCK_ELEVATION));
        pose.scale(CLOCK_SCALE, CLOCK_SCALE, CLOCK_SCALE);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        minecraft.gameRenderer.lightTexture().turnOnLightLayer();
        TimeHaloLayers.render(pose, buffers, player, CLOCK_LAYER_GAP, partialTick);
        buffers.endBatch();
        minecraft.gameRenderer.lightTexture().turnOffLightLayer();

        return true;
    }
}
