package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.entity.DrownedGirl;
import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.entity.ZombieGirl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 丧尸娘的客户端模型和渲染器注册。 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public class ModClientEvents {
    public static final ModelLayerLocation ZOMBIE_GIRL_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "zombie_girl"), "main");
    public static final ModelLayerLocation DROWNED_GIRL_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "drowned_girl"), "main");
    public static final ModelLayerLocation HUMAN_GIRL_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "human_girl"), "main");

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ZOMBIE_GIRL_LAYER, ZombieGirlModel::createBodyLayer);
        event.registerLayerDefinition(DROWNED_GIRL_LAYER, ZombieGirlModel::createBodyLayer);
        event.registerLayerDefinition(HUMAN_GIRL_LAYER, ZombieGirlModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ZOMBIE_GIRL.get(), ZombieGirlRenderer::new);
        event.registerEntityRenderer(ModEntities.DROWNED_GIRL.get(), DrownedGirlRenderer::new);
        event.registerEntityRenderer(ModEntities.HUMAN_GIRL.get(), HumanGirlRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(EndboundEmber.COMPANION_MENU.get(), CompanionScreen::new);
    }

    /**
     * 独立的 Alex 细手模型。
     *
     * 根部 hat 只作为 HumanoidModel 的空占位；真正的头发外层挂在 head 下，
     * 这样 AgeableListModel 的幼年头部渲染会把基础头和头发一起放大，不会错位。
     */
    public static class ZombieGirlModel<T extends ZombieGirl> extends HumanoidModel<T> {
        private final ModelPart hair;
        private final ModelPart jacket;
        private final ModelPart rightSleeve;
        private final ModelPart leftSleeve;
        private final ModelPart rightPants;
        private final ModelPart leftPants;

        public ZombieGirlModel(ModelPart root) {
            super(root);
            this.hair = this.head.getChild("hair");
            this.jacket = this.body.getChild("jacket");
            this.rightSleeve = this.rightArm.getChild("right_sleeve");
            this.leftSleeve = this.leftArm.getChild("left_sleeve");
            this.rightPants = this.rightLeg.getChild("right_pants");
            this.leftPants = this.leftLeg.getChild("left_pants");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
            PartDefinition root = mesh.getRoot();
            PartDefinition head = root.getChild("head");
            PartDefinition body = root.getChild("body");

            // AgeableListModel 会把根 hat 当作身体部件，因此根 hat 必须是空占位。
            root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
            head.addOrReplaceChild("hair",
                    CubeListBuilder.create().texOffs(32, 0)
                            .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,
                                    CubeDeformation.NONE.extend(0.5F)),
                    PartPose.ZERO);

            PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                    CubeListBuilder.create().texOffs(40, 16)
                            .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    PartPose.offset(-5.0F, 2.5F, 0.0F));
            PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                    CubeListBuilder.create().texOffs(32, 48)
                            .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    PartPose.offset(5.0F, 2.5F, 0.0F));

            PartDefinition rightLeg = root.getChild("right_leg");
            PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                    CubeListBuilder.create().texOffs(16, 48)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                    PartPose.offset(1.9F, 12.0F, 0.0F));

            CubeDeformation overlay = CubeDeformation.NONE.extend(0.25F);
            body.addOrReplaceChild("jacket",
                    CubeListBuilder.create().texOffs(16, 32)
                            .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, overlay),
                    PartPose.ZERO);
            rightArm.addOrReplaceChild("right_sleeve",
                    CubeListBuilder.create().texOffs(40, 32)
                            .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, overlay),
                    PartPose.ZERO);
            leftArm.addOrReplaceChild("left_sleeve",
                    CubeListBuilder.create().texOffs(48, 48)
                            .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, overlay),
                    PartPose.ZERO);
            rightLeg.addOrReplaceChild("right_pants",
                    CubeListBuilder.create().texOffs(0, 32)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, overlay),
                    PartPose.ZERO);
            leftLeg.addOrReplaceChild("left_pants",
                    CubeListBuilder.create().texOffs(0, 48)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, overlay),
                    PartPose.ZERO);

            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void prepareMobModel(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
            this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            var stack = entity.getItemInHand(InteractionHand.MAIN_HAND);
            HumanoidArm arm = entity.getMainArm();
            if (stack.is(Items.BOW) && entity.isAggressive()) {
                if (arm == HumanoidArm.RIGHT) this.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                else this.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else if (stack.getItem() instanceof CrossbowItem) {
                if (entity.isUsingItem()) {
                    if (arm == HumanoidArm.RIGHT) this.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                    else this.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                } else if (CrossbowItem.isCharged(stack)) {
                    if (arm == HumanoidArm.RIGHT) this.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                    else this.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                }
            } else if (stack.is(Items.TRIDENT) && entity.isAggressive()) {
                if (arm == HumanoidArm.RIGHT) this.rightArmPose = HumanoidModel.ArmPose.THROW_SPEAR;
                else this.leftArmPose = HumanoidModel.ArmPose.THROW_SPEAR;
            }
            super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                float ageInTicks, float netHeadYaw, float headPitch) {
            this.young = entity.isBaby();
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (!(entity instanceof HumanGirl)
                    && this.leftArmPose == HumanoidModel.ArmPose.EMPTY
                    && this.rightArmPose == HumanoidModel.ArmPose.EMPTY) {
                AnimationUtils.animateZombieArms(this.leftArm, this.rightArm,
                        entity.isAggressive(), this.attackTime, ageInTicks);
            }

            // 只有溺尸娘使用水下分层渲染；幸存者少女和丧尸娘始终显示完整双层皮肤。
            if (entity instanceof DrownedGirl) {
                boolean fullySubmerged = entity.isInWater() && entity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
                boolean headAboveWater = entity.isInWater() && !entity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
                this.hair.visible = !fullySubmerged;
                this.jacket.visible = !fullySubmerged && !headAboveWater;
                this.rightSleeve.visible = !fullySubmerged && !headAboveWater;
                this.leftSleeve.visible = !fullySubmerged && !headAboveWater;
                this.rightPants.visible = !fullySubmerged && !headAboveWater;
                this.leftPants.visible = !fullySubmerged && !headAboveWater;
            } else {
                this.hair.visible = true;
                this.jacket.visible = true;
                this.rightSleeve.visible = true;
                this.leftSleeve.visible = true;
                this.rightPants.visible = true;
                this.leftPants.visible = true;
            }
        }
    }

    public static class DrownedGirlRenderer extends MobRenderer<DrownedGirl, ZombieGirlModel<DrownedGirl>> {
        public DrownedGirlRenderer(EntityRendererProvider.Context context) {
            super(context, new ZombieGirlModel<DrownedGirl>(context.bakeLayer(DROWNED_GIRL_LAYER)), 0.5F);
            this.addLayer(new ItemInHandLayer<DrownedGirl, ZombieGirlModel<DrownedGirl>>(this, context.getItemInHandRenderer()));
        }
        @Override
        public ResourceLocation getTextureLocation(DrownedGirl entity) {
            return variantTexture(entity.getSkinVariant());
        }
    }

    public static class HumanGirlRenderer extends MobRenderer<HumanGirl, ZombieGirlModel<HumanGirl>> {
        public HumanGirlRenderer(EntityRendererProvider.Context context) {
            super(context, new ZombieGirlModel<HumanGirl>(context.bakeLayer(HUMAN_GIRL_LAYER)), 0.5F);
            this.addLayer(new ItemInHandLayer<HumanGirl, ZombieGirlModel<HumanGirl>>(this, context.getItemInHandRenderer()));
        }
        @Override
        public ResourceLocation getTextureLocation(HumanGirl entity) {
            String name = entity.getSkinVariant() == 0 ? "human_girl.png" : "human_girl_" + entity.getSkinVariant() + ".png";
            return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "textures/entity/" + name);
        }
    }

    public static class ZombieGirlRenderer extends MobRenderer<ZombieGirl, ZombieGirlModel<ZombieGirl>> {

        public ZombieGirlRenderer(EntityRendererProvider.Context context) {
            super(context, new ZombieGirlModel<ZombieGirl>(context.bakeLayer(ZOMBIE_GIRL_LAYER)), 0.5F);
            this.addLayer(new ItemInHandLayer<ZombieGirl, ZombieGirlModel<ZombieGirl>>(this, context.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(ZombieGirl entity) {
            return variantTexture(entity.getSkinVariant());
        }
    }

    private static ResourceLocation variantTexture(int variant) {
        String name = variant == 0 ? "zombie_girl.png" : "zombie_girl_" + variant + ".png";
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID,
                "textures/entity/" + name);
    }

    public static class CompanionScreen extends AbstractContainerScreen<ZombieGirl.CompanionMenu> {
        private static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace(
                "textures/gui/container/inventory.png");

        public CompanionScreen(ZombieGirl.CompanionMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.imageWidth = 176;
            this.imageHeight = 302;
            this.inventoryLabelY = 195;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            int left = (this.width - this.imageWidth) / 2;
            int top = (this.height - this.imageHeight) / 2;

            graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, 0xFF8B8B8B);
            graphics.fill(left + 1, top + 1, left + this.imageWidth - 1, top + this.imageHeight - 1, 0xFFC6C6C6);
            graphics.fill(left + 2, top + 2, left + this.imageWidth - 2, top + this.imageHeight - 2, 0xFF373737);
            graphics.fill(left + 3, top + 3, left + this.imageWidth - 3, top + this.imageHeight - 3, 0xFF8B8B8B);

            // 原版风格的装备栏分区背景，避免与伙伴背包第一行挤在一起。
            graphics.fill(left + 5, top + 14, left + 171, top + 43, 0xFF707070);
            graphics.fill(left + 5, top + 45, left + 171, top + 195, 0xFF707070);
            graphics.fill(left + 5, top + 201, left + 171, top + 295, 0xFF707070);

            // 原版容器槽位背景：使用 inventory.png 中的标准 18×18 槽位贴图。
            for (int i = 0; i < this.menu.slots.size(); i++) {
                var slot = this.menu.slots.get(i);
                graphics.blit(INVENTORY_TEXTURE,
                        left + slot.x - 1, top + slot.y - 1,
                        7, 83, 18, 18, 256, 256);
            }

            int[] equipmentIconY = new int[] { 7, 25, 43, 61 };
            for (int i = 0; i < 4; i++) {
                if (!this.menu.slots.get(i).hasItem()) {
                    graphics.blit(INVENTORY_TEXTURE,
                            left + this.menu.slots.get(i).x,
                            top + this.menu.slots.get(i).y,
                            7, equipmentIconY[i], 16, 16, 256, 256);
                }
            }
        }

        @Override
        protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
            graphics.drawString(this.font, Component.translatable("container.endbound_ember.companion_equipment"), 8, 6, 0x404040, false);
            graphics.drawString(this.font, Component.translatable("container.endbound_ember.companion_inventory"), 8, 35, 0x404040, false);
            graphics.drawString(this.font, Component.translatable("container.inventory"), 8, this.inventoryLabelY, 0x404040, false);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }
}
