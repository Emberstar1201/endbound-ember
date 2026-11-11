package com.emberstar.endbounember;

import com.emberstar.endbounember.client.BossMusicConfigScreen;
import com.emberstar.endbounember.client.HaloLayers;
import com.emberstar.endbounember.client.TimeHaloLayers;
import com.emberstar.endbounember.client.TimeTurbulenceSkyEffects;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.emberstar.endbounember.compat.ysm.YsmAdapter;
import com.emberstar.endbounember.compat.ysm.YsmHeadCapture;
import com.emberstar.endbounember.compat.ysm.YsmHaloRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (minecraft, parent) -> new ConfigurationScreen(container, parent,
                        (screen, type, config, title) -> new BossMusicConfigScreen(screen, type, config, title)));
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation inGui = EndboundEmber.id("in_gui");
            for (Item spear : new Item[] {
                    EndboundEmber.WOODEN_SPEAR.get(), EndboundEmber.STONE_SPEAR.get(),
                    EndboundEmber.COPPER_SPEAR.get(), EndboundEmber.IRON_SPEAR.get(),
                    EndboundEmber.GOLDEN_SPEAR.get(), EndboundEmber.DIAMOND_SPEAR.get(),
                    EndboundEmber.NETHERITE_SPEAR.get()
            }) {
                ItemProperties.register(spear, inGui, (stack, level, entity, seed) ->
                        entity == null ? 0.0F : 1.0F);
            }
            for (var block : EndboundEmber.SEAMLESS_GLASS_BLOCKS) {
                // 与原版保持一致：普通玻璃 cutout，染色与遮光玻璃 translucent
                RenderType renderType = block == EndboundEmber.SEAMLESS_GLASS
                        ? RenderType.cutout()
                        : RenderType.translucent();
                ItemBlockRenderTypes.setRenderLayer(block.get(), renderType);
            }
            for (var block : EndboundEmber.SEAMLESS_GLASS_PANE_BLOCKS) {
                // 玻璃板与原版规则一致
                RenderType renderType = block == EndboundEmber.SEAMLESS_GLASS_PANE
                        ? RenderType.cutout()
                        : RenderType.translucent();
                ItemBlockRenderTypes.setRenderLayer(block.get(), renderType);
            }
            for (var block : EndboundEmber.GLASS_SHAPE_CUTOUT_BLOCKS) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
            }
            for (var block : EndboundEmber.GLASS_SHAPE_TRANSLUCENT_BLOCKS) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.translucent());
            }
        });
        EndboundEmber.LOGGER.info("HELLO FROM CLIENT SETUP");
        EndboundEmber.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    /** 为时间乱流维度类型注册纯白天空与巨型时钟的天空特效 */
    @SubscribeEvent
    static void registerDimensionEffects(
            net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(EndboundEmber.id("time_turbulence"), new TimeTurbulenceSkyEffects());
    }

    @SubscribeEvent
    static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        // YSM 用自建渲染器接管玩家模型，原版 PlayerRenderer 的层不会被执行，
        // 光环改由 YsmGeoRendererMixin 在 YSM 渲染管线内直接绘制，这里不再重复添加
        if (YsmAdapter.isLoaded()) {
            EndboundEmber.LOGGER.info("[光环诊断] 检测到 YSM，光环改由 YSM 渲染管线绘制，跳过原版渲染层");
            return;
        }
        event.getSkins().forEach(skin -> {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                EndboundEmber.LOGGER.warn("[光环诊断] AddLayers: skin={} 的渲染器为 null", skin);
                return;
            }
            renderer.addLayer(new HaloLayer(renderer));
            EndboundEmber.LOGGER.info("[光环诊断] AddLayers: skin={}, 渲染器={}, 当前层数={}",
                    skin, renderer.getClass().getName(), countLayers(renderer));
        });
    }

    /** 临时诊断：反射读取渲染器的层数量（{@code layers} 为 protected 字段） */
    private static int countLayers(PlayerRenderer renderer) {
        try {
            java.lang.reflect.Field field = net.minecraft.client.renderer.entity.LivingEntityRenderer.class
                    .getDeclaredField("layers");
            field.setAccessible(true);
            return ((java.util.List<?>) field.get(renderer)).size();
        } catch (ReflectiveOperationException error) {
            return -1;
        }
    }

    /**
     * 客户端指令：切换 YSM 光环渲染。
     * <p>
     * 注册在客户端指令分发器上，不需要 OP 权限，也不会发送到服务器。
     */
    @SubscribeEvent
    static void onRegisterClientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("endbound_ember")
                .then(Commands.literal("ysm_halo")
                        .executes(context -> {
                            boolean enabled = YsmHaloRenderer.toggle();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    enabled
                                            ? "commands.endbound_ember.ysm_halo.enabled"
                                            : "commands.endbound_ember.ysm_halo.disabled"), false);
                            return enabled ? 1 : 0;
                        })));
    }

    /**
     * 每帧实体渲染结束后清空 YSM 头部捕获，保证光环只用本帧的头部矩阵。
     */
    @SubscribeEvent
    static void clearYsmCaptures(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            YsmHeadCapture.clearFrame();
        }
    }

    /**
     * 玩家渲染器层：在 PlayerModel.head 的局部矩阵上绘制光环。
     * 这样会同步头部俯仰、转头以及睡眠、骑乘等由模型应用的姿态。
     * <p>
     * 头部锚点与跟随方式参考 Halo Mod（作者 Azusa_Ke，MIT License，
     * https://github.com/AzusaKe/Halo），许可证全文见 THIRD-PARTY-NOTICES.md。
     */
    private static final class HaloLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        /**
         * 原版头部锚点下的垂直偏移（格）。
         * <p>模型空间的 Y 轴被实体渲染器整体翻转，因此负值才是向上。
         * 头部模型顶端位于 -0.5，这里取 -0.85，让光环悬浮在头顶上方约 0.35 格。
         */
        private static final double VANILLA_HEAD_OFFSET = -0.85D;

        /**
         * 时光光环悬浮在头顶的高度（格）。
         * <p>头部模型顶端位于 -0.5，这里取 -0.72，让光环压在头顶上方约 0.22 格。
         */
        private static final double TIME_HALO_HEAD_OFFSET = -0.72D;
        /**
         * 头顶时钟的后仰角度（度）。
         * <p>环面默认平躺、正面朝上。模型空间的 X 轴旋转会被实体渲染器的 Y 轴翻转取反，
         * 因此这里取负值，世界空间里就变成让环面后仰，第三人称镜头从斜后上方看过去能看到完整表盘与指针。
         */
        private static final float TIME_HALO_TILT = -30.0F;

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
            if (wearingTimeHalo(player)) {
                // 时光光环戴在头顶：跟随头部姿态，环面后仰一点方便第三人称看清表盘
                getParentModel().head.translateAndRotate(pose);
                pose.translate(0.0D, TIME_HALO_HEAD_OFFSET, 0.0D);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(TIME_HALO_TILT));
                TimeHaloLayers.render(pose, buffers, player, partialTick);
            } else {
                getParentModel().head.translateAndRotate(pose);
                // 头部模型原点在头部中心，向上移动到皮肤上方并保留原有视觉偏移。
                pose.translate(0.0D, VANILLA_HEAD_OFFSET, -0.10D);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-12.0F));
                HaloLayers.render(pose, buffers, ageInTicks, 1.0F, 255);
            }
            pose.popPose();
        }
    }

    /** 头盔栏或 Curios 光环饰品栏佩戴均渲染 */
    private static boolean wearingHalo(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.STARDUST_WITCH_HALO.get())
                || player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.TIME_HALO.get())) {
            return true;
        }
        // Curios 为运行时软依赖，仅在已加载时检查饰品栏，避免未安装时链接错误
        return ModList.get().isLoaded("curios")
                && (CuriosCompat.isEquipped(player, EndboundEmber.STARDUST_WITCH_HALO.get())
                        || CuriosCompat.isEquipped(player, EndboundEmber.TIME_HALO.get()));
    }

    /** 判定玩家当前佩戴的是时光光环（区别于星尘光环，渲染成时钟） */
    private static boolean wearingTimeHalo(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.TIME_HALO.get())) {
            return true;
        }
        return ModList.get().isLoaded("curios")
                && CuriosCompat.isEquipped(player, EndboundEmber.TIME_HALO.get());
    }
}
