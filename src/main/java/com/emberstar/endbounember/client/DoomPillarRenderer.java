package com.emberstar.endbounember.client;

import com.emberstar.endbounember.block.DoomPillarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class DoomPillarRenderer implements BlockEntityRenderer<DoomPillarBlockEntity> {
    private static final double PIXEL = 1.0D / 16.0D;
    private final BlockRenderDispatcher dispatcher;

    public DoomPillarRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(DoomPillarBlockEntity pillar, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderCuboid(Blocks.OBSIDIAN.defaultBlockState(), 2, 0, 2, 14, 4, 14,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.OBSIDIAN.defaultBlockState(), 5, 4, 5, 11, 38, 11,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3, 38, 3, 13, 48, 13,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 6, 34, 0, 10, 42, 4,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 6, 34, 12, 10, 42, 16,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 0, 34, 6, 4, 42, 10,
                poseStack, bufferSource, packedLight, packedOverlay);
        renderCuboid(Blocks.CRYING_OBSIDIAN.defaultBlockState(), 12, 34, 6, 16, 42, 10,
                poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderCuboid(net.minecraft.world.level.block.state.BlockState state,
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(fromX * PIXEL, fromY * PIXEL, fromZ * PIXEL);
        poseStack.scale((toX - fromX) / 16.0F, (toY - fromY) / 16.0F, (toZ - fromZ) / 16.0F);
        dispatcher.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DoomPillarBlockEntity pillar) {
        return new AABB(pillar.getBlockPos()).inflate(0.25D, 2.0D, 0.25D);
    }
}
