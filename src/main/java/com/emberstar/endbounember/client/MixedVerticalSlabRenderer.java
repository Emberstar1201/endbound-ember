package com.emberstar.endbounember.client;

import com.emberstar.endbounember.block.MixedVerticalSlabBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 混合竖半砖渲染器：读取方块实体中保存的两片半砖状态，
 * 分别按完整方块状态渲染一次，拼成左右（或前后）各半的完整方块。
 */
public class MixedVerticalSlabRenderer implements BlockEntityRenderer<MixedVerticalSlabBlockEntity> {
    private final BlockRenderDispatcher dispatcher;

    public MixedVerticalSlabRenderer(BlockEntityRendererProvider.Context context) {
        dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MixedVerticalSlabBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        dispatcher.renderSingleBlock(entity.getPrimary(), poseStack, bufferSource, packedLight, packedOverlay);
        dispatcher.renderSingleBlock(entity.getSecondary(), poseStack, bufferSource, packedLight, packedOverlay);
    }
}
