package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.NetherMeteor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 下界流星的岩块模型：一块核心岩石，外围再挂几块朝向错开的碎块，
 * 让整体轮廓不规则，远看也像一颗燃烧的石头而不是一个方块。
 */
public class NetherMeteorModel extends EntityModel<NetherMeteor> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "nether_meteor"), "main");

    private final ModelPart root;

    public NetherMeteorModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 核心岩块
        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F),
                PartPose.ZERO);
        // 外围碎块：位置与旋转都刻意错开
        root.addOrReplaceChild("chunk_up",
                CubeListBuilder.create().texOffs(0, 40)
                        .addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(2.0F, -7.0F, 1.0F, 0.35F, 0.55F, 0.2F));
        root.addOrReplaceChild("chunk_left",
                CubeListBuilder.create().texOffs(26, 40)
                        .addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F),
                PartPose.offsetAndRotation(-7.0F, 1.5F, -2.0F, 0.5F, 0.15F, 0.4F));
        root.addOrReplaceChild("chunk_right",
                CubeListBuilder.create().texOffs(48, 40)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(6.0F, 3.0F, 5.0F, 0.25F, 0.8F, 0.3F));
        root.addOrReplaceChild("chunk_down",
                CubeListBuilder.create().texOffs(26, 40)
                        .addBox(-2.5F, -2.5F, -2.5F, 5.0F, 5.0F, 5.0F),
                PartPose.offsetAndRotation(-4.0F, 7.0F, 4.0F, 0.2F, 0.35F, 0.45F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(NetherMeteor entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 旋转与缩放由渲染器根据坠落进度驱动
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight,
            int packedOverlay, int color) {
        this.root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
