package com.emberstar.endbounember;

import com.emberstar.endbounember.client.HaloLayers;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EndboundEmber.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public class EndboundEmberClient {
    public EndboundEmberClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        EndboundEmber.LOGGER.info("HELLO FROM CLIENT SETUP");
        EndboundEmber.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkins().forEach(skin -> {
            PlayerRenderer renderer = event.getSkin(skin);
            renderer.addLayer(new HaloLayer(renderer));
        });
    }

    /**
     * 玩家渲染器层：在 PlayerModel.head 的局部矩阵上绘制光环。
     * 这样会同步头部俯仰、转头以及睡眠、骑乘等由模型应用的姿态。
     * <p>
     * 头部锚点与跟随方式参考 Halo Mod（作者 Azusa_Ke，MIT License，
     * https://github.com/AzusaKe/Halo），许可证全文见 THIRD-PARTY-NOTICES.md。
     */
    private static final class HaloLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private HaloLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                float headPitch) {
            if (!wearingHalo(player) || player.isInvisible() || player.isSpectator()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                return;
            }

            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            // 头部模型原点在头部中心，向上移动到皮肤上方并保留原有视觉偏移。
            pose.translate(0.0D, -0.55D, -0.10D);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-12.0F));
            HaloLayers.render(pose, buffers, ageInTicks, 1.0F, 255);
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
