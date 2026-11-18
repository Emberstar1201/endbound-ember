package com.emberstar.endbounember.client;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.ChainMiningEvents;
import com.emberstar.endbounember.world.ChainMiningState;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * 连锁挖掘白框预览：准星指向某个方块时，把它这次会被一起连锁掉的方块全部用白框标出来。
 * 白框关闭了深度测试，所以即使目标藏在其他方块后面也能看见。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ChainMiningPreview {
    /**
     * 穿墙白框渲染类型：与线条渲染一致，但关闭深度测试（NO_DEPTH_TEST），
     * 写入颜色、不写深度，因此可以叠加显示在任意方块之上。
     */
    private static final RenderType VISIBLE_LINES = createLineType("endbound_ember_chain_preview_visible",
            OptionalDouble.of(2.0D), RenderStateShard.LEQUAL_DEPTH_TEST);
    private static final RenderType OCCLUDED_LINES = createLineType("endbound_ember_chain_preview_occluded",
            OptionalDouble.of(1.5D), RenderStateShard.NO_DEPTH_TEST);

    private static RenderType createLineType(String name, OptionalDouble width, RenderStateShard.DepthTestStateShard depthTest) {
        return RenderType.create(name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(width))
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(depthTest)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false));
    }

    private ChainMiningPreview() {
    }

    private static void drawFaceEdges(PoseStack poseStack, VertexConsumer visible, VertexConsumer occluded,
            ClientLevel level, BlockPos pos, Direction face, Vec3 camera) {
        double x = pos.getX() - camera.x;
        double y = pos.getY() - camera.y;
        double z = pos.getZ() - camera.z;
        double x2 = x + 1.0D;
        double y2 = y + 1.0D;
        double z2 = z + 1.0D;
        double epsilon = 0.002D;

        switch (face) {
            case DOWN -> y += epsilon;
            case UP -> y2 -= epsilon;
            case NORTH -> z += epsilon;
            case SOUTH -> z2 -= epsilon;
            case WEST -> x += epsilon;
            case EAST -> x2 -= epsilon;
        }

        float alpha = level.getBlockState(pos.relative(face)).isAir() ? 1.0F : 0.32F;
        VertexConsumer consumer = alpha == 1.0F ? visible : occluded;
        if (face.getAxis().isVertical()) {
            double faceY = face == Direction.UP ? y2 : y;
            emitLine(poseStack, consumer, x, faceY, z, x2, faceY, z, alpha);
            emitLine(poseStack, consumer, x2, faceY, z, x2, faceY, z2, alpha);
            emitLine(poseStack, consumer, x2, faceY, z2, x, faceY, z2, alpha);
            emitLine(poseStack, consumer, x, faceY, z2, x, faceY, z, alpha);
        } else if (face.getAxis() == Direction.Axis.X) {
            double faceX = face == Direction.EAST ? x2 : x;
            emitLine(poseStack, consumer, faceX, y, z, faceX, y2, z, alpha);
            emitLine(poseStack, consumer, faceX, y2, z, faceX, y2, z2, alpha);
            emitLine(poseStack, consumer, faceX, y2, z2, faceX, y, z2, alpha);
            emitLine(poseStack, consumer, faceX, y, z2, faceX, y, z, alpha);
        } else {
            double faceZ = face == Direction.SOUTH ? z2 : z;
            emitLine(poseStack, consumer, x, y, faceZ, x2, y, faceZ, alpha);
            emitLine(poseStack, consumer, x2, y, faceZ, x2, y2, faceZ, alpha);
            emitLine(poseStack, consumer, x2, y2, faceZ, x, y2, faceZ, alpha);
            emitLine(poseStack, consumer, x, y2, faceZ, x, y, faceZ, alpha);
        }
    }

    private static void emitLine(PoseStack poseStack, VertexConsumer consumer,
            double x1, double y1, double z1, double x2, double y2, double z2, float alpha) {
        var pose = poseStack.last().pose();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(240.0F / 255.0F, 248.0F / 255.0F, 1.0F, alpha)
                .setNormal((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(240.0F / 255.0F, 248.0F / 255.0F, 1.0F, alpha)
                .setNormal((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
    }

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (!ChainMiningClientState.enabled()) return;
        ChainMiningState.Mode mode = ChainMiningClientState.mode();
        // 耕种模式破坏不连锁，没有可预览的方块
        if (mode == ChainMiningState.Mode.FARMING) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || player.isSpectator() || !player.getAbilities().mayBuild) return;
        // 与服务端一致：生存模式空手不会触发连锁，也就不显示白框
        if (!player.isCreative() && player.getMainHandItem().isEmpty()) return;

        BlockPos origin = event.getTarget().getBlockPos();
        BlockState target = level.getBlockState(origin);
        if (!ChainMiningEvents.isEligible(target, level, origin)) return;

        // 与真正破坏时调用的是同一个方法，预览结果 = 实际结果
        List<BlockPos> candidates = ChainMiningEvents.findCandidates(level, player, origin, target, mode);
        if (candidates.isEmpty()) return;

        Set<BlockPos> selected = new HashSet<>(candidates.size() + 1);
        selected.add(origin.immutable());
        selected.addAll(candidates);

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        ByteBufferBuilder visibleStorage = new ByteBufferBuilder(4096);
        ByteBufferBuilder occludedStorage = new ByteBufferBuilder(4096);
        try {
            BufferBuilder visibleBuilder = new BufferBuilder(visibleStorage, VertexFormat.Mode.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
            BufferBuilder occludedBuilder = new BufferBuilder(occludedStorage, VertexFormat.Mode.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
            for (BlockPos pos : selected) {
                for (Direction direction : Direction.values()) {
                    if (selected.contains(pos.relative(direction))) continue;
                    drawFaceEdges(poseStack, visibleBuilder, occludedBuilder, level, pos, direction, camera);
                }
            }
            var visibleMesh = visibleBuilder.build();
            if (visibleMesh != null) {
                VISIBLE_LINES.draw(visibleMesh);
            }
            var occludedMesh = occludedBuilder.build();
            if (occludedMesh != null) {
                OCCLUDED_LINES.draw(occludedMesh);
            }
        } finally {
            visibleStorage.close();
            occludedStorage.close();
        }
    }
}
