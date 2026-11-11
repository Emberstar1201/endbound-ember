package com.emberstar.endbounember.client;

import com.emberstar.endbounember.block.DisenchantmentTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class DisenchantmentTableRenderer implements BlockEntityRenderer<DisenchantmentTableBlockEntity> {
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/entity/enchanting_table_book.png");
    private final BookModel bookModel;

    public DisenchantmentTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(DisenchantmentTableBlockEntity table, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float open = table.oOpen + (table.open - table.oOpen) * partialTick;
        float rot = table.oRot + (table.rot - table.oRot) * partialTick;
        float flip = table.oFlip + (table.flip - table.oFlip) * partialTick;
        float page = table.oFlipT + (table.flipT - table.oFlipT) * partialTick;
        float leftPage = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float rightPage = Mth.clamp(Mth.frac(page + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.75D + 3.0D / 16.0D, 0.5D);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(-rot));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(80.0F));
        this.bookModel.setupAnim(0.0F, leftPage, rightPage, open);
        this.bookModel.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(BOOK_TEXTURE)),
                packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DisenchantmentTableBlockEntity table) {
        return new AABB(table.getBlockPos()).inflate(0.25D, 0.75D, 0.25D);
    }
}
