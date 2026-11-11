package com.emberstar.endbounember;

import java.util.List;

import org.slf4j.Logger;

import com.emberstar.endbounember.enchantment.AllNatureReviveEnchantment;
import com.emberstar.endbounember.enchantment.AutoSmeltEnchantment;
import com.emberstar.endbounember.enchantment.CloudBowEnchantments;
import com.emberstar.endbounember.enchantment.CraftsmanshipEnchantment;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.DifficultyGiftEnchantment;
import com.emberstar.endbounember.enchantment.DurabilityBoostEnchantment;
import com.emberstar.endbounember.enchantment.ElegantCatwalkEnchantment;
import com.emberstar.endbounember.enchantment.EndApproachesEnchantment;
import com.emberstar.endbounember.enchantment.ExNihiloEnchantment;
import com.emberstar.endbounember.enchantment.FallingStarGuidanceEnchantment;
import com.emberstar.endbounember.enchantment.FertileBountyEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.enchantment.LightweightEnchantment;
import com.emberstar.endbounember.enchantment.MadeInChinaEnchantment;
import com.emberstar.endbounember.enchantment.QianpoQingMingSwordEnchantment;
import com.emberstar.endbounember.enchantment.ShockwaveEnchantment;
import com.emberstar.endbounember.enchantment.SkywardSlamEnchantment;
import com.emberstar.endbounember.enchantment.SpringHarvestEnchantment;
import com.emberstar.endbounember.enchantment.WindRippleEnchantment;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.item.EndStarItem;
import com.emberstar.endbounember.item.HomeBellItem;
import com.emberstar.endbounember.item.LifeStarItem;
import com.emberstar.endbounember.item.LuoHongyuItem;
import com.emberstar.endbounember.item.ModArmorMaterials;
import com.emberstar.endbounember.item.OceanStarItem;
import com.emberstar.endbounember.item.StardustCordBraceletItem;
import com.emberstar.endbounember.item.DawnAmuletItem;
import com.emberstar.endbounember.item.AnahitaFirstlightLiddellItem;
import com.emberstar.endbounember.item.StardustWitchHaloItem;
import com.emberstar.endbounember.item.TimeHaloItem;
import com.emberstar.endbounember.item.StarlightStarItem;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;
import com.emberstar.endbounember.item.SpearItem;
import com.emberstar.endbounember.item.UnownedStardustItem;
import com.emberstar.endbounember.item.VoidStarItem;
import com.emberstar.endbounember.block.SeamlessGlassBlock;
import com.emberstar.endbounember.block.SeamlessGlassPaneBlock;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.SimpleTier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EndboundEmber.MODID)
public class EndboundEmber {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "endbound_ember";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "endbound_ember" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "endbound_ember" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "endbound_ember" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    // 方块实体类型注册
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    /** 展示台爆炸抗性：与原版黑曜石同级，TNT、苦力怕、末地水晶均无法炸毁，台面存放的物品随之保留 */
    public static final float DISPLAY_PEDESTAL_BLAST_RESISTANCE = 1200.0F;

    /** 展示台方块：可放置任意物品并悬浮展示，后续作为仪式方块 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.DisplayPedestalBlock> DISPLAY_PEDESTAL =
            BLOCKS.registerBlock("display_pedestal",
                    com.emberstar.endbounember.block.DisplayPedestalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            // 徒手挖掘约 10 秒（无工具需求时破坏速度按 hardness × 30 tick 计算）
                            .strength(6.7F, DISPLAY_PEDESTAL_BLAST_RESISTANCE)
                            .noOcclusion()
                            // 使用完整方块的遮挡形状，避免空手/持物右键时被判定为「可穿过」而不触发交互
                            .isViewBlocking((state, level, pos) -> true)
                            .isSuffocating((state, level, pos) -> false));

    /** 展示台材质变种：与建筑分区现有竖半砖材质一致（混凝土除外） */
    public static final java.util.List<DeferredHolder<Block, com.emberstar.endbounember.block.DisplayPedestalBlock>>
            DISPLAY_PEDESTAL_VARIANTS = new java.util.ArrayList<>();
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>>
            DISPLAY_PEDESTAL_VARIANT_ITEMS = new java.util.ArrayList<>();

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.emberstar.endbounember.block.DisplayPedestalBlockEntity>>
            DISPLAY_PEDESTAL_BLOCK_ENTITY = BLOCK_ENTITIES.register("display_pedestal",
                    () -> {
                        java.util.List<Block> blocks = new java.util.ArrayList<>();
                        blocks.add(DISPLAY_PEDESTAL.get());
                        for (var variant : DISPLAY_PEDESTAL_VARIANTS) {
                            blocks.add(variant.get());
                        }
                        return net.minecraft.world.level.block.entity.BlockEntityType.Builder
                                .of(com.emberstar.endbounember.block.DisplayPedestalBlockEntity::new,
                                        blocks.toArray(new Block[0]))
                                .build(null);
                    });

    public static final DeferredHolder<Block, com.emberstar.endbounember.block.DisenchantmentTableBlock> DISENCHANTMENT_TABLE =
            BLOCKS.registerBlock("disenchantment_table", com.emberstar.endbounember.block.DisenchantmentTableBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4.0F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DISENCHANTMENT_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("disenchantment_table", DISENCHANTMENT_TABLE);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.emberstar.endbounember.block.DisenchantmentTableBlockEntity>>
            DISENCHANTMENT_TABLE_BLOCK_ENTITY = BLOCK_ENTITIES.register("disenchantment_table",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(com.emberstar.endbounember.block.DisenchantmentTableBlockEntity::new, DISENCHANTMENT_TABLE.get()).build(null));

    /** 亡者之柱：Herobrine 第二阶段召出的黑曜石柱，平摊本体伤害并有独立血条 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.DoomPillarBlock> DOOM_PILLAR =
            BLOCKS.registerBlock("doom_pillar",
                    com.emberstar.endbounember.block.DoomPillarBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .sound(SoundType.STONE)
                            // 硬度 -1：无法被挖掘或爆炸破坏，只能靠左键攻击削血
                            .strength(-1.0F, 3_600_000.0F)
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .noLootTable()
                            .pushReaction(PushReaction.BLOCK));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.emberstar.endbounember.block.DoomPillarBlockEntity>>
            DOOM_PILLAR_BLOCK_ENTITY = BLOCK_ENTITIES.register("doom_pillar",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(com.emberstar.endbounember.block.DoomPillarBlockEntity::new,
                                    DOOM_PILLAR.get())
                            .build(null));

    // ===== 无缝玻璃：原生六方向连接状态，资源侧使用 multipart 模型 =====
    public static final java.util.List<DeferredHolder<Block, SeamlessGlassBlock>> SEAMLESS_GLASS_BLOCKS =
            new java.util.ArrayList<>();
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> SEAMLESS_GLASS_ITEMS =
            new java.util.ArrayList<>();
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_GLASS = registerSeamlessGlass(
            "seamless_glass", MapColor.NONE, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_TINTED_GLASS = registerSeamlessGlass(
            "seamless_tinted_glass", MapColor.COLOR_GRAY, true);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_WHITE_STAINED_GLASS = registerSeamlessGlass(
            "seamless_white_stained_glass", MapColor.SNOW, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_ORANGE_STAINED_GLASS = registerSeamlessGlass(
            "seamless_orange_stained_glass", MapColor.COLOR_ORANGE, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_MAGENTA_STAINED_GLASS = registerSeamlessGlass(
            "seamless_magenta_stained_glass", MapColor.COLOR_MAGENTA, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_LIGHT_BLUE_STAINED_GLASS = registerSeamlessGlass(
            "seamless_light_blue_stained_glass", MapColor.COLOR_LIGHT_BLUE, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_YELLOW_STAINED_GLASS = registerSeamlessGlass(
            "seamless_yellow_stained_glass", MapColor.COLOR_YELLOW, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_LIME_STAINED_GLASS = registerSeamlessGlass(
            "seamless_lime_stained_glass", MapColor.COLOR_LIGHT_GREEN, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_PINK_STAINED_GLASS = registerSeamlessGlass(
            "seamless_pink_stained_glass", MapColor.COLOR_PINK, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_GRAY_STAINED_GLASS = registerSeamlessGlass(
            "seamless_gray_stained_glass", MapColor.COLOR_GRAY, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_LIGHT_GRAY_STAINED_GLASS = registerSeamlessGlass(
            "seamless_light_gray_stained_glass", MapColor.COLOR_LIGHT_GRAY, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_CYAN_STAINED_GLASS = registerSeamlessGlass(
            "seamless_cyan_stained_glass", MapColor.COLOR_CYAN, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_PURPLE_STAINED_GLASS = registerSeamlessGlass(
            "seamless_purple_stained_glass", MapColor.COLOR_PURPLE, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_BLUE_STAINED_GLASS = registerSeamlessGlass(
            "seamless_blue_stained_glass", MapColor.COLOR_BLUE, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_BROWN_STAINED_GLASS = registerSeamlessGlass(
            "seamless_brown_stained_glass", MapColor.COLOR_BROWN, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_GREEN_STAINED_GLASS = registerSeamlessGlass(
            "seamless_green_stained_glass", MapColor.COLOR_GREEN, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_RED_STAINED_GLASS = registerSeamlessGlass(
            "seamless_red_stained_glass", MapColor.COLOR_RED, false);
    public static final DeferredHolder<Block, SeamlessGlassBlock> SEAMLESS_BLACK_STAINED_GLASS = registerSeamlessGlass(
            "seamless_black_stained_glass", MapColor.COLOR_BLACK, false);

    private static DeferredHolder<Block, SeamlessGlassBlock> registerSeamlessGlass(
            String name, MapColor mapColor, boolean tinted) {
        DeferredHolder<Block, SeamlessGlassBlock> block = BLOCKS.registerBlock(name,
                properties -> new SeamlessGlassBlock(properties, tinted),
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HAT)
                        .strength(0.3F)
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .isValidSpawn((state, level, pos, entityType) -> false)
                        .isRedstoneConductor((state, level, pos) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false));
        SEAMLESS_GLASS_BLOCKS.add(block);
        SEAMLESS_GLASS_ITEMS.add(ITEMS.registerSimpleBlockItem(name, block));
        return block;
    }

    // ===== 无缝玻璃板：连接规则沿用铁栏杆，资源侧自定义模型去掉方块边界的竖线 =====
    public static final java.util.List<DeferredHolder<Block, SeamlessGlassPaneBlock>> SEAMLESS_GLASS_PANE_BLOCKS =
            new java.util.ArrayList<>();
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> SEAMLESS_GLASS_PANE_ITEMS =
            new java.util.ArrayList<>();
    /** 普通无缝玻璃板：唯一使用 cutout 渲染层的一个 */
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_glass", MapColor.NONE, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_TINTED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_tinted_glass", MapColor.COLOR_GRAY, true);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_WHITE_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_white_stained_glass", MapColor.SNOW, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_ORANGE_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_orange_stained_glass", MapColor.COLOR_ORANGE, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_MAGENTA_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_magenta_stained_glass", MapColor.COLOR_MAGENTA, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_LIGHT_BLUE_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_light_blue_stained_glass", MapColor.COLOR_LIGHT_BLUE, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_YELLOW_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_yellow_stained_glass", MapColor.COLOR_YELLOW, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_LIME_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_lime_stained_glass", MapColor.COLOR_LIGHT_GREEN, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_PINK_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_pink_stained_glass", MapColor.COLOR_PINK, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_GRAY_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_gray_stained_glass", MapColor.COLOR_GRAY, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_LIGHT_GRAY_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_light_gray_stained_glass", MapColor.COLOR_LIGHT_GRAY, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_CYAN_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_cyan_stained_glass", MapColor.COLOR_CYAN, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_PURPLE_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_purple_stained_glass", MapColor.COLOR_PURPLE, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_BLUE_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_blue_stained_glass", MapColor.COLOR_BLUE, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_BROWN_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_brown_stained_glass", MapColor.COLOR_BROWN, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_GREEN_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_green_stained_glass", MapColor.COLOR_GREEN, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_RED_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_red_stained_glass", MapColor.COLOR_RED, false);
    public static final DeferredHolder<Block, SeamlessGlassPaneBlock> SEAMLESS_BLACK_STAINED_GLASS_PANE = registerSeamlessGlassPane(
            "seamless_black_stained_glass", MapColor.COLOR_BLACK, false);

    private static DeferredHolder<Block, SeamlessGlassPaneBlock> registerSeamlessGlassPane(
            String name, MapColor mapColor, boolean tinted) {
        String paneName = name + "_pane";
        DeferredHolder<Block, SeamlessGlassPaneBlock> block = BLOCKS.registerBlock(paneName,
                properties -> new SeamlessGlassPaneBlock(properties, tinted),
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HAT)
                        .strength(0.3F)
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .isValidSpawn((state, level, pos, entityType) -> false)
                        .isRedstoneConductor((state, level, pos) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false));
        SEAMLESS_GLASS_PANE_BLOCKS.add(block);
        SEAMLESS_GLASS_PANE_ITEMS.add(ITEMS.registerSimpleBlockItem(paneName, block));
        return block;
    }

    // ===== 玻璃楼梯、半砖与竖半砖：原版玻璃家族与无缝玻璃家族各 18 种 =====

    /** 玻璃家族名称，顺序与原版创造分区一致，同时用于无缝玻璃家族（加 seamless_ 前缀） */
    private static final String[] GLASS_FAMILY_NAMES = {
            "glass", "tinted_glass",
            "white_stained_glass", "orange_stained_glass", "magenta_stained_glass",
            "light_blue_stained_glass", "yellow_stained_glass", "lime_stained_glass",
            "pink_stained_glass", "gray_stained_glass", "light_gray_stained_glass",
            "cyan_stained_glass", "purple_stained_glass", "blue_stained_glass",
            "brown_stained_glass", "green_stained_glass", "red_stained_glass",
            "black_stained_glass"
    };

    /** 与原版玻璃家族一一对应的原版方块，用于复制属性 */
    private static final Block[] GLASS_FAMILY_VANILLA = {
            Blocks.GLASS, Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS
    };

    /** 全部玻璃楼梯 / 半砖 / 竖半砖方块，供创造栏注入使用 */
    public static final java.util.List<DeferredHolder<Block, ? extends Block>> GLASS_SHAPE_BLOCKS =
            new java.util.ArrayList<>();

    /** 使用 cutout 渲染层的玻璃建筑形状（仅普通玻璃与普通无缝玻璃） */
    public static final java.util.List<DeferredHolder<Block, ? extends Block>> GLASS_SHAPE_CUTOUT_BLOCKS =
            new java.util.ArrayList<>();

    /** 使用 translucent 渲染层的玻璃建筑形状（遮光玻璃与 16 色染色玻璃） */
    public static final java.util.List<DeferredHolder<Block, ? extends Block>> GLASS_SHAPE_TRANSLUCENT_BLOCKS =
            new java.util.ArrayList<>();

    static {
        for (int i = 0; i < GLASS_FAMILY_NAMES.length; i++) {
            Block vanilla = GLASS_FAMILY_VANILLA[i];
            // 与原版渲染层保持一致：普通玻璃 cutout，遮光与染色玻璃 translucent
            boolean cutout = vanilla == Blocks.GLASS;

            registerGlassBuildingShapes(GLASS_FAMILY_NAMES[i], vanilla,
                    vanilla::defaultBlockState, cutout);

            DeferredHolder<Block, SeamlessGlassBlock> seamless = SEAMLESS_GLASS_BLOCKS.get(i);
            registerGlassBuildingShapes("seamless_" + GLASS_FAMILY_NAMES[i], vanilla,
                    () -> seamless.get().defaultBlockState(), cutout);
        }
    }

    /**
     * 为一种玻璃材质注册楼梯、半砖与竖半砖。
     *
     * @param name           基础名称，例如 glass 或 seamless_red_stained_glass
     * @param copySource     属性复制来源的原版玻璃方块
     * @param baseState      楼梯基底状态，用于判断楼梯能否与该方块连接
     * @param cutout         true 使用 cutout 渲染层，false 使用 translucent
     */
    private static void registerGlassBuildingShapes(String name, Block copySource,
            java.util.function.Supplier<BlockState> baseState, boolean cutout) {
        String stairsName = name + "_stairs";
        DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> stairs = BLOCKS.registerBlock(
                stairsName,
                properties -> new net.minecraft.world.level.block.StairBlock(baseState.get(), properties),
                BlockBehaviour.Properties.ofFullCopy(copySource));
        ITEMS.registerSimpleBlockItem(stairsName, stairs);
        GLASS_SHAPE_BLOCKS.add(stairs);

        String slabName = name + "_slab";
        DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> slab = BLOCKS.registerBlock(
                slabName,
                net.minecraft.world.level.block.SlabBlock::new,
                BlockBehaviour.Properties.ofFullCopy(copySource));
        ITEMS.registerSimpleBlockItem(slabName, slab);
        GLASS_SHAPE_BLOCKS.add(slab);

        String verticalSlabName = name + "_vertical_slab";
        DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> verticalSlab =
                registerVerticalSlab(verticalSlabName, BlockBehaviour.Properties.ofFullCopy(copySource));
        GLASS_SHAPE_BLOCKS.add(verticalSlab);

        var target = cutout ? GLASS_SHAPE_CUTOUT_BLOCKS : GLASS_SHAPE_TRANSLUCENT_BLOCKS;
        target.add(stairs);
        target.add(slab);
        target.add(verticalSlab);
    }

    // ===== 竖半砖方块：高度一格、宽度半格的非完整方块，适合立柱、薄墙与装饰边框 =====

    /** 平滑石英竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SMOOTH_QUARTZ_VERTICAL_SLAB =
            registerVerticalSlab("smooth_quartz_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.QUARTZ)
                            .sound(SoundType.STONE)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 石英块竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> QUARTZ_VERTICAL_SLAB =
            registerVerticalSlab("quartz_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.QUARTZ)
                            .sound(SoundType.STONE)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> STONE_BRICK_VERTICAL_SLAB =
            registerVerticalSlab("stone_brick_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 裂纹石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CRACKED_STONE_BRICK_VERTICAL_SLAB =
            registerVerticalSlab("cracked_stone_brick_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 苔石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> MOSSY_STONE_BRICK_VERTICAL_SLAB =
            registerVerticalSlab("mossy_stone_brick_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .strength(1.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 平滑石头竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SMOOTH_STONE_VERTICAL_SLAB =
            registerVerticalSlab("smooth_stone_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 深板岩砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> DEEPSLATE_BRICK_VERTICAL_SLAB =
            registerVerticalSlab("deepslate_brick_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DEEPSLATE)
                            .sound(SoundType.DEEPSLATE)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /** 磨制深板岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_DEEPSLATE_VERTICAL_SLAB =
            registerVerticalSlab("polished_deepslate_vertical_slab",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DEEPSLATE)
                            .sound(SoundType.DEEPSLATE)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    // ===== 木质竖半砖：11 种原版木板材质，斧可挖掘、无需工具掉落 =====

    /** 橡木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> OAK_VERTICAL_SLAB =
            registerVerticalSlab("oak_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 云杉木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SPRUCE_VERTICAL_SLAB =
            registerVerticalSlab("spruce_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 白桦木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> BIRCH_VERTICAL_SLAB =
            registerVerticalSlab("birch_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 丛林木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> JUNGLE_VERTICAL_SLAB =
            registerVerticalSlab("jungle_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 金合欢木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> ACACIA_VERTICAL_SLAB =
            registerVerticalSlab("acacia_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 深色橡木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> DARK_OAK_VERTICAL_SLAB =
            registerVerticalSlab("dark_oak_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 红木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> MANGROVE_VERTICAL_SLAB =
            registerVerticalSlab("mangrove_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 樱花木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CHERRY_VERTICAL_SLAB =
            registerVerticalSlab("cherry_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 竹竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> BAMBOO_VERTICAL_SLAB =
            registerVerticalSlab("bamboo_vertical_slab", woodVerticalSlabProperties(MapColor.WOOD));

    /** 绯红菌木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CRIMSON_VERTICAL_SLAB =
            registerVerticalSlab("crimson_vertical_slab", woodVerticalSlabProperties(MapColor.NETHER));

    /** 诡异菌木竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WARPED_VERTICAL_SLAB =
            registerVerticalSlab("warped_vertical_slab", woodVerticalSlabProperties(MapColor.NETHER));

    /** 石头竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> STONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("stone_vertical_slab", Blocks.STONE);
    /** 花岗岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> GRANITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("granite_vertical_slab", Blocks.GRANITE);
    /** 磨制花岗岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_GRANITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_granite_vertical_slab", Blocks.POLISHED_GRANITE);
    /** 闪长岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> DIORITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("diorite_vertical_slab", Blocks.DIORITE);
    /** 磨制闪长岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_DIORITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_diorite_vertical_slab", Blocks.POLISHED_DIORITE);
    /** 安山岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> ANDESITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("andesite_vertical_slab", Blocks.ANDESITE);
    /** 磨制安山岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_ANDESITE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_andesite_vertical_slab", Blocks.POLISHED_ANDESITE);
    /** 圆石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> COBBLESTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("cobblestone_vertical_slab", Blocks.COBBLESTONE);
    /** 苔石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> MOSSY_COBBLESTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("mossy_cobblestone_vertical_slab", Blocks.MOSSY_COBBLESTONE);
    /** 砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("sandstone_vertical_slab", Blocks.SANDSTONE);
    /** 平滑砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SMOOTH_SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("smooth_sandstone_vertical_slab", Blocks.SMOOTH_SANDSTONE);
    /** 切制砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CUT_SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("cut_sandstone_vertical_slab", Blocks.CUT_SANDSTONE);
    /** 红砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> RED_SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("red_sandstone_vertical_slab", Blocks.RED_SANDSTONE);
    /** 平滑红砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SMOOTH_RED_SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("smooth_red_sandstone_vertical_slab", Blocks.SMOOTH_RED_SANDSTONE);
    /** 切制红砂岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CUT_RED_SANDSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("cut_red_sandstone_vertical_slab", Blocks.CUT_RED_SANDSTONE);
    /** 红砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("brick_vertical_slab", Blocks.BRICKS);
    /** 泥砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> MUD_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("mud_brick_vertical_slab", Blocks.MUD_BRICKS);
    /** 深板岩圆石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> COBBLED_DEEPSLATE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("cobbled_deepslate_vertical_slab", Blocks.COBBLED_DEEPSLATE);
    /** 深板岩瓦竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> DEEPSLATE_TILE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("deepslate_tile_vertical_slab", Blocks.DEEPSLATE_TILES);
    /** 凝灰岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> TUFF_VERTICAL_SLAB =
            registerCopiedVerticalSlab("tuff_vertical_slab", Blocks.TUFF);
    /** 磨制凝灰岩竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_TUFF_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_tuff_vertical_slab", Blocks.POLISHED_TUFF);
    /** 凝灰岩砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> TUFF_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("tuff_brick_vertical_slab", Blocks.TUFF_BRICKS);
    /** 黑石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> BLACKSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("blackstone_vertical_slab", Blocks.BLACKSTONE);
    /** 磨制黑石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_BLACKSTONE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_blackstone_vertical_slab", Blocks.POLISHED_BLACKSTONE);
    /** 磨制黑石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> POLISHED_BLACKSTONE_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("polished_blackstone_brick_vertical_slab", Blocks.POLISHED_BLACKSTONE_BRICKS);
    /** 末地石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> END_STONE_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("end_stone_brick_vertical_slab", Blocks.END_STONE_BRICKS);
    /** 紫珀竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> PURPUR_VERTICAL_SLAB =
            registerCopiedVerticalSlab("purpur_vertical_slab", Blocks.PURPUR_BLOCK);
    /** 海晶石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> PRISMARINE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("prismarine_vertical_slab", Blocks.PRISMARINE);
    /** 海晶石砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> PRISMARINE_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("prismarine_brick_vertical_slab", Blocks.PRISMARINE_BRICKS);
    /** 暗海晶石竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> DARK_PRISMARINE_VERTICAL_SLAB =
            registerCopiedVerticalSlab("dark_prismarine_vertical_slab", Blocks.DARK_PRISMARINE);
    /** 下界砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> NETHER_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("nether_brick_vertical_slab", Blocks.NETHER_BRICKS);
    /** 红色下界砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> RED_NETHER_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("red_nether_brick_vertical_slab", Blocks.RED_NETHER_BRICKS);
    /** 竹马赛克竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> BAMBOO_MOSAIC_VERTICAL_SLAB =
            registerCopiedVerticalSlab("bamboo_mosaic_vertical_slab", Blocks.BAMBOO_MOSAIC);
    /** 切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("cut_copper_vertical_slab", Blocks.CUT_COPPER);
    /** 斑驳的切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> EXPOSED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("exposed_cut_copper_vertical_slab", Blocks.EXPOSED_CUT_COPPER);
    /** 锈蚀的切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WEATHERED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("weathered_cut_copper_vertical_slab", Blocks.WEATHERED_CUT_COPPER);
    /** 氧化的切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> OXIDIZED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("oxidized_cut_copper_vertical_slab", Blocks.OXIDIZED_CUT_COPPER);
    /** 涂蜡的切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WAXED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("waxed_cut_copper_vertical_slab", Blocks.WAXED_CUT_COPPER);
    /** 涂蜡的斑驳切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WAXED_EXPOSED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("waxed_exposed_cut_copper_vertical_slab", Blocks.WAXED_EXPOSED_CUT_COPPER);
    /** 涂蜡的锈蚀切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WAXED_WEATHERED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("waxed_weathered_cut_copper_vertical_slab", Blocks.WAXED_WEATHERED_CUT_COPPER);
    /** 涂蜡的氧化切制铜竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> WAXED_OXIDIZED_CUT_COPPER_VERTICAL_SLAB =
            registerCopiedVerticalSlab("waxed_oxidized_cut_copper_vertical_slab", Blocks.WAXED_OXIDIZED_CUT_COPPER);

    // ===== 26.4 版本新方块移植：冰砖与雪砖（含楼梯与竖半砖） =====

    /** 冰砖：属性与浮冰一致（低摩擦、玻璃音效，任意工具均可掉落） */
    public static final DeferredHolder<Block, Block> PACKED_ICE_BRICKS =
            BLOCKS.registerSimpleBlock("packed_ice_bricks",
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PACKED_ICE_BRICKS_ITEM =
            ITEMS.registerSimpleBlockItem("packed_ice_bricks", PACKED_ICE_BRICKS);

    /** 雪砖：属性与雪块一致（铲子挖掘才会掉落） */
    public static final DeferredHolder<Block, Block> SNOW_BRICKS =
            BLOCKS.registerSimpleBlock("snow_bricks",
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW_BLOCK));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SNOW_BRICKS_ITEM =
            ITEMS.registerSimpleBlockItem("snow_bricks", SNOW_BRICKS);

    /** 冰砖楼梯 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> PACKED_ICE_BRICK_STAIRS =
            BLOCKS.registerBlock("packed_ice_brick_stairs",
                    properties -> new net.minecraft.world.level.block.StairBlock(
                            PACKED_ICE_BRICKS.get().defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PACKED_ICE_BRICK_STAIRS_ITEM =
            ITEMS.registerSimpleBlockItem("packed_ice_brick_stairs", PACKED_ICE_BRICK_STAIRS);

    /** 冰砖半砖 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> PACKED_ICE_BRICK_SLAB =
            BLOCKS.registerBlock("packed_ice_brick_slab",
                    net.minecraft.world.level.block.SlabBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PACKED_ICE_BRICK_SLAB_ITEM =
            ITEMS.registerSimpleBlockItem("packed_ice_brick_slab", PACKED_ICE_BRICK_SLAB);

    /** 冰砖墙 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.WallBlock> PACKED_ICE_BRICK_WALL =
            BLOCKS.registerBlock("packed_ice_brick_wall",
                    net.minecraft.world.level.block.WallBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PACKED_ICE_BRICK_WALL_ITEM =
            ITEMS.registerSimpleBlockItem("packed_ice_brick_wall", PACKED_ICE_BRICK_WALL);

    /** 雕纹冰砖 */
    public static final DeferredHolder<Block, Block> CHISELED_PACKED_ICE =
            BLOCKS.registerSimpleBlock("chiseled_packed_ice",
                    BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CHISELED_PACKED_ICE_ITEM =
            ITEMS.registerSimpleBlockItem("chiseled_packed_ice", CHISELED_PACKED_ICE);

    /** 雪砖楼梯 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> SNOW_BRICK_STAIRS =
            BLOCKS.registerBlock("snow_brick_stairs",
                    properties -> new net.minecraft.world.level.block.StairBlock(
                            SNOW_BRICKS.get().defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW_BLOCK));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SNOW_BRICK_STAIRS_ITEM =
            ITEMS.registerSimpleBlockItem("snow_brick_stairs", SNOW_BRICK_STAIRS);

    /** 冰砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> PACKED_ICE_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("packed_ice_brick_vertical_slab", Blocks.PACKED_ICE);

    /** 雪砖竖半砖 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> SNOW_BRICK_VERTICAL_SLAB =
            registerCopiedVerticalSlab("snow_brick_vertical_slab", Blocks.SNOW_BLOCK);

    // ===== 高版本内容移植：16 色混凝土楼梯、混凝土半砖与混凝土竖半砖 =====

    /** 混凝土颜色，顺序与原版创造分区一致 */
    private static final String[] CONCRETE_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    /** 对应颜色的原版混凝土方块，用于复制属性并作为楼梯基底 */
    private static final Block[] CONCRETE_BLOCKS = {
            Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE,
            Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
            Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
            Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE,
            Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE,
            Blocks.BLACK_CONCRETE
    };

    /** 混凝土楼梯物品，按颜色顺序存放，供原版建筑分区使用 */
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> CONCRETE_STAIRS_ITEMS =
            new java.util.ArrayList<>();

    /** 混凝土半砖物品，按颜色顺序存放，供原版建筑分区使用 */
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> CONCRETE_SLAB_ITEMS =
            new java.util.ArrayList<>();

    /** 混凝土竖半砖方块，按颜色顺序存放，供模组建筑分区使用 */
    public static final java.util.List<DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock>> CONCRETE_VERTICAL_SLABS =
            new java.util.ArrayList<>();

    static {
        for (int i = 0; i < CONCRETE_COLORS.length; i++) {
            String color = CONCRETE_COLORS[i];
            Block concrete = CONCRETE_BLOCKS[i];

            String stairsName = color + "_concrete_stairs";
            DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> stairs =
                    BLOCKS.registerBlock(stairsName,
                            properties -> new net.minecraft.world.level.block.StairBlock(
                                    concrete.defaultBlockState(), properties),
                            BlockBehaviour.Properties.ofFullCopy(concrete));
            CONCRETE_STAIRS_ITEMS.add(ITEMS.registerSimpleBlockItem(stairsName, stairs));

            String slabName = color + "_concrete_slab";
            DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> slab =
                    BLOCKS.registerBlock(slabName,
                            net.minecraft.world.level.block.SlabBlock::new,
                            BlockBehaviour.Properties.ofFullCopy(concrete));
            CONCRETE_SLAB_ITEMS.add(ITEMS.registerSimpleBlockItem(slabName, slab));

            CONCRETE_VERTICAL_SLABS.add(
                    registerCopiedVerticalSlab(color + "_concrete_vertical_slab", concrete));
        }
    }

    // ===== 高版本内容移植：方解石家族（柱、楼梯、半砖、墙与磨制变体） =====

    /** 方解石柱 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.RotatedPillarBlock> CALCITE_PILLAR =
            BLOCKS.registerBlock("calcite_pillar",
                    net.minecraft.world.level.block.RotatedPillarBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 方解石楼梯 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> CALCITE_STAIRS =
            BLOCKS.registerBlock("calcite_stairs",
                    properties -> new net.minecraft.world.level.block.StairBlock(
                            Blocks.CALCITE.defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 方解石半砖 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> CALCITE_SLAB =
            BLOCKS.registerBlock("calcite_slab",
                    net.minecraft.world.level.block.SlabBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 方解石墙 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.WallBlock> CALCITE_WALL =
            BLOCKS.registerBlock("calcite_wall",
                    net.minecraft.world.level.block.WallBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石 */
    public static final DeferredHolder<Block, Block> POLISHED_CALCITE =
            BLOCKS.registerBlock("polished_calcite", Block::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石楼梯 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> POLISHED_CALCITE_STAIRS =
            BLOCKS.registerBlock("polished_calcite_stairs",
                    properties -> new net.minecraft.world.level.block.StairBlock(
                            POLISHED_CALCITE.get().defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石半砖 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> POLISHED_CALCITE_SLAB =
            BLOCKS.registerBlock("polished_calcite_slab",
                    net.minecraft.world.level.block.SlabBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石墙 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.WallBlock> POLISHED_CALCITE_WALL =
            BLOCKS.registerBlock("polished_calcite_wall",
                    net.minecraft.world.level.block.WallBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石砖 */
    public static final DeferredHolder<Block, Block> POLISHED_CALCITE_BRICKS =
            BLOCKS.registerBlock("polished_calcite_bricks", Block::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石砖楼梯 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> POLISHED_CALCITE_BRICKS_STAIRS =
            BLOCKS.registerBlock("polished_calcite_bricks_stairs",
                    properties -> new net.minecraft.world.level.block.StairBlock(
                            POLISHED_CALCITE_BRICKS.get().defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石砖半砖 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> POLISHED_CALCITE_BRICKS_SLAB =
            BLOCKS.registerBlock("polished_calcite_bricks_slab",
                    net.minecraft.world.level.block.SlabBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 磨制方解石砖墙 */
    public static final DeferredHolder<Block, net.minecraft.world.level.block.WallBlock> POLISHED_CALCITE_BRICKS_WALL =
            BLOCKS.registerBlock("polished_calcite_bricks_wall",
                    net.minecraft.world.level.block.WallBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE));

    /** 方解石家族物品，按方块家族顺序存放，供原版建筑分区使用 */
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> CALCITE_BUILDING_ITEMS =
            new java.util.ArrayList<>();

    /** 方解石家族竖半砖，供模组建筑分区使用 */
    public static final java.util.List<DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock>> CALCITE_VERTICAL_SLABS =
            new java.util.ArrayList<>();

    static {
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("calcite_pillar", CALCITE_PILLAR));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("calcite_stairs", CALCITE_STAIRS));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("calcite_slab", CALCITE_SLAB));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("calcite_wall", CALCITE_WALL));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite", POLISHED_CALCITE));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_stairs", POLISHED_CALCITE_STAIRS));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_slab", POLISHED_CALCITE_SLAB));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_wall", POLISHED_CALCITE_WALL));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_bricks", POLISHED_CALCITE_BRICKS));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_bricks_stairs", POLISHED_CALCITE_BRICKS_STAIRS));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_bricks_slab", POLISHED_CALCITE_BRICKS_SLAB));
        CALCITE_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem("polished_calcite_bricks_wall", POLISHED_CALCITE_BRICKS_WALL));

        CALCITE_VERTICAL_SLABS.add(registerCopiedVerticalSlab("calcite_vertical_slab", Blocks.CALCITE));
        CALCITE_VERTICAL_SLABS.add(registerVerticalSlab("polished_calcite_vertical_slab",
                BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE).noOcclusion()));
        CALCITE_VERTICAL_SLABS.add(registerVerticalSlab("polished_calcite_bricks_vertical_slab",
                BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE).noOcclusion()));
    }

    /** 混合竖半砖：两片不同材质竖半砖合并成的完整方块，材质数据存放在方块实体中，不可直接获取 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.MixedVerticalSlabBlock> MIXED_VERTICAL_SLAB =
            BLOCKS.registerBlock("mixed_vertical_slabs",
                    com.emberstar.endbounember.block.MixedVerticalSlabBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .strength(2.0F, 6.0F)
                            .noOcclusion()
                            .noLootTable());

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.emberstar.endbounember.block.MixedVerticalSlabBlockEntity>>
            MIXED_VERTICAL_SLAB_BLOCK_ENTITY = BLOCK_ENTITIES.register("mixed_vertical_slabs",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(com.emberstar.endbounember.block.MixedVerticalSlabBlockEntity::new,
                                    MIXED_VERTICAL_SLAB.get())
                            .build(null));

    /** 木质竖半砖统一属性：硬度与原版木板一致，斧头挖掘，徒手也可掉落 */
    private static BlockBehaviour.Properties woodVerticalSlabProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .sound(SoundType.WOOD)
                .strength(2.0F, 3.0F)
                .noOcclusion();
    }

    /**
     * 注册一个竖半砖方块及其对应物品，减少重复代码。
     * 方块注册在 BLOCKS，物品以 BlockItem 形式注册在 ITEMS。
     */
    private static DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> registerVerticalSlab(
            String name, BlockBehaviour.Properties properties) {
        DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> block =
                BLOCKS.registerBlock(name, com.emberstar.endbounember.block.VerticalSlabBlock::new, properties);
        ITEMS.registerItem(name,
                itemProperties -> new com.emberstar.endbounember.item.VerticalSlabItem(block.get(), itemProperties));
        return block;
    }

    /** 注册竖半砖：方块属性直接复制原版对应方块（硬度、音效与挖掘要求保持一致），强制关闭遮挡 */
    private static DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> registerCopiedVerticalSlab(
            String name, Block base) {
        return registerVerticalSlab(name, BlockBehaviour.Properties.ofFullCopy(base).noOcclusion());
    }

    /** 拂晓成长等级（1~100），与固定为 1 级的附魔等级分开存储。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DAWN_LEVEL =
            DATA_COMPONENTS.register("dawn_level", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());
    /** 匠心传承：该工具累计破坏的方块数，用于永久提升挖掘速度。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CRAFTSMANSHIP_COUNT =
            DATA_COMPONENTS.register("craftsmanship_count", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());
    public static final DeferredHolder<MenuType<?>, MenuType<com.emberstar.endbounember.menu.DisenchantmentTableMenu>> DISENCHANTMENT_TABLE_MENU =
            MENU_TYPES.register("disenchantment_table", () -> IMenuTypeExtension.create(
                    com.emberstar.endbounember.menu.DisenchantmentTableMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.emberstar.endbounember.entity.ZombieGirl.CompanionMenu>> COMPANION_MENU =
            MENU_TYPES.register("companion_inventory", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new com.emberstar.endbounember.entity.ZombieGirl.CompanionMenu(id, inventory, buffer.readVarInt())));

    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_1 = registerSound("husk_girl_idle_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_2 = registerSound("husk_girl_idle_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_3 = registerSound("husk_girl_idle_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_HUST_1 = registerSound("husk_girl_hust_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_HUST_2 = registerSound("husk_girl_hust_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_FAREWELL_1 = registerSound("husk_girl_farewell_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_EAT_1 = registerSound("husk_girl_eat_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMAN_GIRL_IDLE_1 = registerSound("human_girl_idle_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMAN_GIRL_IDLE_2 = registerSound("human_girl_idle_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMAN_GIRL_IDLE_3 = registerSound("human_girl_idle_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOME_BELL_SOUND = registerSound("home_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARLIGHT = registerSound("starlight");
    public static final DeferredHolder<SoundEvent, SoundEvent> HALO_RITUAL = registerSound("halo_ritual");
    /** 时间乱流的秒针 / 分针走动声 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_TICK_SOUND = registerSound("time_tick");
    /** 时间乱流的整点钟声 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_CHIME_SOUND = registerSound("time_chime");
    /**
     * Him Boss 战背景音乐。
     * <p>音频文件位置：assets/endbound_ember/sounds/music/herobrine_boss_music.ogg</p>
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEROBRINE_BOSS_MUSIC = registerSound("herobrine_boss_music");

    /** 星尘光环的获取仪式：需要消耗的经验值总量 */
    public static final int HALO_RITUAL_EXPERIENCE_COST = 120_000;
    /** 星尘光环的获取仪式：动画总时长（tick），15 秒 */
    public static final int HALO_RITUAL_DURATION = 300;
    /** 星尘光环的获取仪式：动画结束前多少 tick 完成光环替换 */
    public static final int HALO_RITUAL_SWAP_TICK = 280;
    /** 星尘光环的获取仪式：祭坛方块（守神的展示台）周围环绕的展示台数量要求 */
    public static final int HALO_RITUAL_PEDESTAL_COUNT = 8;
    /** 星尘光环的获取仪式：底座必须是用末地石铺成的 7×7 正方形 */
    public static final int ALTAR_BASE_SIZE = 7;

    /** 晨曦护符的获取仪式：动画总时长（tick），15 秒 */
    public static final int AMULET_RITUAL_DURATION = 300;
    /** 晨曦护符的获取仪式：动画结束前多少 tick 完成护符替换 */
    public static final int AMULET_RITUAL_SWAP_TICK = 280;
    /** 晨曦护符的获取仪式：环绕展示台数量（上下左右 + 四个斜角） */
    public static final int AMULET_RITUAL_PEDESTAL_COUNT = 8;

    /** 阿娜希塔·晨曦·莉德尔护符的获取仪式：动画总时长（tick），15 秒 */
    public static final int ANAHITA_RITUAL_DURATION = 300;
    /** 阿娜希塔·晨曦·莉德尔护符的获取仪式：动画结束前多少 tick 完成护符替换 */
    public static final int ANAHITA_RITUAL_SWAP_TICK = 280;

    /** 生命之星的获取仪式：动画总时长（tick），15 秒 */
    public static final int LIFE_STAR_RITUAL_DURATION = 300;
    /** 生命之星的获取仪式：动画结束前多少 tick 完成替换 */
    public static final int LIFE_STAR_RITUAL_SWAP_TICK = 280;

    /** Herobrine 召唤仪式：动画总时长（tick），15 秒 */
    public static final int HEROBRINE_RITUAL_DURATION = 300;
    /** Herobrine 召唤仪式：能量汇聚完毕、Boss 现身的时间点（tick） */
    public static final int HEROBRINE_RITUAL_SUMMON_TICK = 240;
    /** Herobrine 召唤仪式：四角展示台数量 */
    public static final int HEROBRINE_RITUAL_PEDESTAL_COUNT = 4;
    /** Herobrine 召唤仪式：四角展示台相对中心的最大搜索半径（格） */
    public static final int HEROBRINE_ALTAR_RADIUS = 3;

    /** 星尘系列物品标签：晨曦祭坛四角可放置任意星尘变体 */
    public static final TagKey<Item> EMBERSTARS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "emberstars"));

    // 成就「星尘的祝福」
    public static final ResourceLocation STARDUST_BLESSING_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(MODID, "stardust_blessing");
    public static final String STARDUST_BLESSING_CRITERION = "obtain_stardust_halo";

    /** 本模组命名空间下的资源路径 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    // 友好生物标签（与 minecraft:friendly_mobs 内容一致，供本模组实体直接绑定）
    public static final TagKey<EntityType<?>> FRIENDLY_MOB_TAG = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "friendly_mobs"));

    // 铜工具介于石制与铁制之间：保留石制的掉落门槛（可采石及铁矿），但比铁制更低。
    private static final Tier COPPER_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_STONE_TOOL, 190, 5.0F, 1.0F, 13,
            () -> Ingredient.of(Items.COPPER_INGOT));

    public static final DeferredItem<SwordItem> COPPER_SWORD = ITEMS.register("copper_sword",
            () -> new SwordItem(COPPER_TIER, new Item.Properties().attributes(SwordItem.createAttributes(COPPER_TIER, 3, -2.4F))));
    public static final DeferredItem<SpearItem> WOODEN_SPEAR = ITEMS.register("wooden_spear",
            () -> new SpearItem(new Item.Properties().durability(59)
                    .attributes(SwordItem.createAttributes(Tiers.WOOD, 2.0F, -2.9F)), true));
    public static final DeferredItem<SpearItem> STONE_SPEAR = ITEMS.register("stone_spear",
            () -> new SpearItem(new Item.Properties().durability(131)
                    .attributes(SwordItem.createAttributes(Tiers.STONE, 3.0F, -2.9F))));
    public static final DeferredItem<SpearItem> COPPER_SPEAR = ITEMS.register("copper_spear",
            () -> new SpearItem(new Item.Properties().durability(190)
                    .attributes(SwordItem.createAttributes(COPPER_TIER, 3.5F, -2.9F))));
    public static final DeferredItem<SpearItem> IRON_SPEAR = ITEMS.register("iron_spear",
            () -> new SpearItem(new Item.Properties().durability(250)
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 4.0F, -2.9F))));
    public static final DeferredItem<SpearItem> GOLDEN_SPEAR = ITEMS.register("golden_spear",
            () -> new SpearItem(new Item.Properties().durability(32)
                    .attributes(SwordItem.createAttributes(Tiers.GOLD, 2.0F, -2.9F))));
    public static final DeferredItem<SpearItem> DIAMOND_SPEAR = ITEMS.register("diamond_spear",
            () -> new SpearItem(new Item.Properties().durability(1561)
                    .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 5.0F, -2.9F))));
    public static final DeferredItem<SpearItem> NETHERITE_SPEAR = ITEMS.register("netherite_spear",
            () -> new SpearItem(new Item.Properties().durability(2031)
                    .fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 6.0F, -2.9F))));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_ATTACK = registerSound("spear_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_HIT = registerSound("spear_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_USE = registerSound("spear_use");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_LUNGE = registerSound("spear_lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_WOOD_ATTACK = registerSound("spear_wood_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_WOOD_HIT = registerSound("spear_wood_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR_WOOD_USE = registerSound("spear_wood_use");
    public static final DeferredItem<PickaxeItem> COPPER_PICKAXE = ITEMS.register("copper_pickaxe",
            () -> new PickaxeItem(COPPER_TIER, new Item.Properties().attributes(PickaxeItem.createAttributes(COPPER_TIER, 1, -2.8F))));
    public static final DeferredItem<AxeItem> COPPER_AXE = ITEMS.register("copper_axe",
            () -> new AxeItem(COPPER_TIER, new Item.Properties().attributes(AxeItem.createAttributes(COPPER_TIER, 6, -3.1F))));
    public static final DeferredItem<ShovelItem> COPPER_SHOVEL = ITEMS.register("copper_shovel",
            () -> new ShovelItem(COPPER_TIER, new Item.Properties().attributes(ShovelItem.createAttributes(COPPER_TIER, 1.5F, -3.0F))));
    public static final DeferredItem<HoeItem> COPPER_HOE = ITEMS.register("copper_hoe",
            () -> new HoeItem(COPPER_TIER, new Item.Properties().attributes(HoeItem.createAttributes(COPPER_TIER, -2, -1.0F))));

    public static final DeferredItem<ArmorItem> COPPER_HELMET = ITEMS.register("copper_helmet",
            () -> new ArmorItem(ModArmorMaterials.COPPER, ArmorItem.Type.HELMET, new Item.Properties().durability(121)));
    public static final DeferredItem<ArmorItem> COPPER_CHESTPLATE = ITEMS.register("copper_chestplate",
            () -> new ArmorItem(ModArmorMaterials.COPPER, ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(176)));
    public static final DeferredItem<ArmorItem> COPPER_LEGGINGS = ITEMS.register("copper_leggings",
            () -> new ArmorItem(ModArmorMaterials.COPPER, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(165)));
    public static final DeferredItem<ArmorItem> COPPER_BOOTS = ITEMS.register("copper_boots",
            () -> new ArmorItem(ModArmorMaterials.COPPER, ArmorItem.Type.BOOTS, new Item.Properties().durability(143)));

    public static final DeferredItem<Item> LIFE_STAR = ITEMS.register("life_star",
            () -> new LifeStarItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> END_STAR = ITEMS.register("end_star",
            () -> new EndStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<OceanStarItem> OCEAN_STAR = ITEMS.register("ocean_star",
            () -> new OceanStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    public static final DeferredItem<StarlightStarItem> STARLIGHT_STAR = ITEMS.register("starlight_star",
            () -> new StarlightStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    public static final DeferredItem<VoidStarItem> VOID_STAR = ITEMS.register("void_star",
            () -> new VoidStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    public static final DeferredItem<HomeBellItem> HOME_BELL = ITEMS.register("home_bell",
            () -> new HomeBellItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    // 拂晓：通过附魔书形式发放，附魔等级即成长层数（逻辑见 DawnEnchantment）

    public static final DeferredItem<StardustCordBraceletItem> STARDUST_CORD_BRACELET = ITEMS.register(
            "stardust_cord_bracelet", () -> new StardustCordBraceletItem(new Item.Properties().stacksTo(1)));

    // 晨曦护符：免疫黑暗 / 失明 / 凋零，强化对亡灵与 BOSS 的伤害，周期性释放虚空冲击
    public static final DeferredItem<DawnAmuletItem> DAWN_AMULET = ITEMS.register(
            "dawn_amulet", () -> new DawnAmuletItem(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // 阿娜希塔·晨曦·莉德尔：晨曦护符的上位护符，效果介于晨曦护符与星尘光环之间
    public static final DeferredItem<AnahitaFirstlightLiddellItem> ANAHITA_FIRSTLIGHT_LIDDELL =
            ITEMS.register("anahita_firstlight_liddell",
                    () -> new AnahitaFirstlightLiddellItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    // 洛红雨：赤色刀锋，命中叠加赤雨印记，叠满引爆赤雨斩（属性值为相对玩家基础值的偏移）
    public static final DeferredItem<LuoHongyuItem> LUO_HONGYU = ITEMS.register(
            "luohongyu", () -> new LuoHongyuItem(new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .attributes(LuoHongyuItem.attributes(18.0D, 0.7D))));

    public static final DeferredItem<StardustWitchHaloItem> STARDUST_WITCH_HALO = ITEMS.register(
            "stardust_witch_halo", () -> new StardustWitchHaloItem(ModArmorMaterials.STARDUST_WITCH_HALO,
                    new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<TimeHaloItem> TIME_HALO = ITEMS.register(
            "time_halo", () -> new TimeHaloItem(ModArmorMaterials.STARDUST_WITCH_HALO,
                    new Item.Properties().rarity(Rarity.RARE)));

    // 永恒图腾：击败万灵之主掉落，30 耐久；手持时抵挡一次致死伤害并回满生命
    public static final DeferredItem<Item> ETERNAL_TOTEM = ITEMS.registerSimpleItem("eternal_totem",
            new Item.Properties().stacksTo(1).durability(30).rarity(Rarity.EPIC).fireResistant()
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    public static final DeferredItem<UnownedStardustItem> UNOWNED_STARDUST = ITEMS.register(
            "unowned_stardust", () -> new UnownedStardustItem(new Item.Properties()
                    .stacksTo(1).fireResistant()));

    /** 展示台的方块物品 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DISPLAY_PEDESTAL_ITEM =
            ITEMS.registerSimpleBlockItem("display_pedestal", DISPLAY_PEDESTAL);

    private static final String[] DISPLAY_PEDESTAL_MATERIALS = {
            "smooth_quartz", "quartz", "stone_brick", "cracked_stone_brick", "mossy_stone_brick", "smooth_stone",
            "deepslate_brick", "polished_deepslate", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped", "stone", "granite", "polished_granite", "diorite",
            "polished_diorite", "andesite", "polished_andesite", "cobblestone", "mossy_cobblestone", "sandstone",
            "smooth_sandstone", "cut_sandstone", "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone",
            "brick", "mud_brick", "cobbled_deepslate", "deepslate_tile", "tuff", "polished_tuff", "tuff_brick",
            "blackstone", "polished_blackstone", "polished_blackstone_brick", "end_stone_brick", "purpur",
            "prismarine", "prismarine_brick", "dark_prismarine", "nether_brick", "red_nether_brick",
            "bamboo_mosaic", "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
            "waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper",
            "waxed_oxidized_cut_copper", "packed_ice_brick", "snow_brick", "calcite", "polished_calcite",
            "polished_calcite_bricks",
            // 矿物展示台：从铁开始，煤炭不做
            "iron", "copper", "gold", "redstone", "lapis", "emerald", "diamond", "netherite", "amethyst",
    };

    static {
        for (String material : DISPLAY_PEDESTAL_MATERIALS) {
            String name = material + "_display_pedestal";
            DeferredHolder<Block, com.emberstar.endbounember.block.DisplayPedestalBlock> block =
                    BLOCKS.registerBlock(name,
                            com.emberstar.endbounember.block.DisplayPedestalBlock::new,
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.STONE)
                                    .strength(6.7F, DISPLAY_PEDESTAL_BLAST_RESISTANCE)
                                    .noOcclusion()
                                    .isViewBlocking((state, level, pos) -> true)
                                    .isSuffocating((state, level, pos) -> false));
            DISPLAY_PEDESTAL_VARIANTS.add(block);
            DISPLAY_PEDESTAL_VARIANT_ITEMS.add(ITEMS.registerSimpleBlockItem(name, block));
        }
    }

    /** 矿物建筑套件的材质名与纹理来源：从铁开始，煤炭不做。 */
    private static final Object[][] MINERAL_FAMILIES = {
            {"iron", Blocks.IRON_BLOCK},
            {"copper", Blocks.COPPER_BLOCK},
            {"gold", Blocks.GOLD_BLOCK},
            {"redstone", Blocks.REDSTONE_BLOCK},
            {"lapis", Blocks.LAPIS_BLOCK},
            {"emerald", Blocks.EMERALD_BLOCK},
            {"diamond", Blocks.DIAMOND_BLOCK},
            {"netherite", Blocks.NETHERITE_BLOCK},
            {"amethyst", Blocks.AMETHYST_BLOCK},
    };

    /** 矿物台阶与半砖物品，按材质顺序收集，供原版建筑分区使用。 */
    public static final java.util.List<DeferredItem<net.minecraft.world.item.BlockItem>> MINERAL_BUILDING_ITEMS =
            new java.util.ArrayList<>();

    /** 矿物竖半砖方块，按材质顺序收集，供模组建筑分区使用。 */
    public static final java.util.List<DeferredHolder<Block, ? extends Block>> MINERAL_VERTICAL_SLABS =
            new java.util.ArrayList<>();

    static {
        for (Object[] family : MINERAL_FAMILIES) {
            String name = (String) family[0];
            Block base = (Block) family[1];

            // 楼梯以原版矿物方块为基底，可与矿物方块直接衔接
            String stairsName = name + "_stairs";
            DeferredHolder<Block, net.minecraft.world.level.block.StairBlock> stairs = BLOCKS.registerBlock(
                    stairsName,
                    properties -> new net.minecraft.world.level.block.StairBlock(base.defaultBlockState(), properties),
                    BlockBehaviour.Properties.ofFullCopy(base));
            MINERAL_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem(stairsName, stairs));

            String slabName = name + "_slab";
            DeferredHolder<Block, net.minecraft.world.level.block.SlabBlock> slab = BLOCKS.registerBlock(
                    slabName,
                    net.minecraft.world.level.block.SlabBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(base));
            MINERAL_BUILDING_ITEMS.add(ITEMS.registerSimpleBlockItem(slabName, slab));

            MINERAL_VERTICAL_SLABS.add(registerCopiedVerticalSlab(name + "_vertical_slab", base));
        }
    }

    // 人权剑：基础攻击伤害 20 / 攻速 3.5 / 护甲 10（属性值为相对玩家基础值的偏移）
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL = ITEMS.register(
            "sword_of_the_free_will", () -> new SwordOfTheFreeWillItem(false, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(19.0D, -0.5D, 10.0D, 0.0D))));

    // 人权剑·星火永燃：基础攻击伤害 30 / 攻速 4.5 / 护甲 100 / 盔甲韧性 25
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL_ENHANCED = ITEMS.register(
            "sword_of_the_free_will_enhanced", () -> new SwordOfTheFreeWillItem(true, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(29.0D, 0.5D, 100.0D, 25.0D))));

    // 晨曦之星：击败 Herobrine 后掉落，锻造升级素材
    public static final DeferredItem<Item> DAWN_STAR = ITEMS.registerSimpleItem("dawn_star",
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

    // 晨曦模板：宝箱 50% 概率开出，锻造升级模板
    public static final DeferredItem<Item> DAWN_TEMPLATE = ITEMS.registerSimpleItem("dawn_template",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> EMBERSTAR = ITEMS.registerSimpleItem("emberstar");
    public static final DeferredItem<Item> EMBERSTAR_1 = ITEMS.registerSimpleItem("emberstar_1");
    public static final DeferredItem<Item> EMBERSTAR_2 = ITEMS.registerSimpleItem("emberstar_2");
    public static final DeferredItem<Item> EMBERSTAR_3 = ITEMS.registerSimpleItem("emberstar_3");

    public static final DeferredItem<Item> VOID_EMBERSTAR = ITEMS.registerSimpleItem("void_emberstar");

    public static final DeferredItem<Item> CIVILIAN_RATION = ITEMS.registerSimpleItem("civilian_ration");
    public static final DeferredItem<Item> MILITARY_RATION = ITEMS.registerSimpleItem("military_ration");
    // 面粉不可食用；炒面 1 点饥饿值 / 1 点饱和度；湿炒面 2 点饥饿值 / 1.5 点饱和度
    public static final DeferredItem<Item> FLOUR = ITEMS.registerSimpleItem("flour");
    public static final DeferredItem<com.emberstar.endbounember.item.DryFriedFlourItem> DRY_FRIED_FLOUR = ITEMS.register("dry_fried_flour",
            () -> new com.emberstar.endbounember.item.DryFriedFlourItem(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.5F).build())));
    public static final DeferredItem<Item> WET_FRIED_FLOUR = ITEMS.register("wet_fried_flour",
            () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.375F).build())));

    public static final DeferredItem<SpawnEggItem> ZOMBIE_GIRL_SPAWN_EGG = ITEMS.register("zombie_girl_spawn_egg", 
            () -> new SpawnEggItem(ModEntities.ZOMBIE_GIRL.get(), 0x4A5B4A, 0xB7B7B7, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> DROWNED_GIRL_SPAWN_EGG = ITEMS.register("drowned_girl_spawn_egg",
            () -> new SpawnEggItem(ModEntities.DROWNED_GIRL.get(), 0x2B5D66, 0x5AB5D1, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> HUMAN_GIRL_SPAWN_EGG = ITEMS.register("human_girl_spawn_egg",
            () -> new SpawnEggItem(ModEntities.HUMAN_GIRL.get(), 0xD9A97E, 0x6B4A2F, new Item.Properties()));

    // 物品分区：星尘、生命之星、手绳等道具，图标使用虚无星尘
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_TAB = CREATIVE_MODE_TABS.register("items",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.endbound_ember.items"))
                    // 排在原版全部 10 个分区之后，使本模组三个分区都落在创造模式第 2 页
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> VOID_EMBERSTAR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(COPPER_SWORD.get());
                        output.accept(IRON_SPEAR.get());
                        output.accept(COPPER_PICKAXE.get());
                        output.accept(COPPER_AXE.get());
                        output.accept(COPPER_SHOVEL.get());
                        output.accept(COPPER_HOE.get());
                        output.accept(COPPER_HELMET.get());
                        output.accept(COPPER_CHESTPLATE.get());
                        output.accept(COPPER_LEGGINGS.get());
                        output.accept(COPPER_BOOTS.get());
                        output.accept(LIFE_STAR.get());
                        output.accept(END_STAR.get());
                        output.accept(OCEAN_STAR.get());
                        output.accept(STARLIGHT_STAR.get());
                        output.accept(VOID_STAR.get());
                        output.accept(HOME_BELL.get());
                        output.accept(STARDUST_CORD_BRACELET.get());
                        output.accept(STARDUST_WITCH_HALO.get());
                        output.accept(TIME_HALO.get());
                        output.accept(ETERNAL_TOTEM.get());
                        output.accept(UNOWNED_STARDUST.get());
                        output.accept(EMBERSTAR.get());
                        output.accept(EMBERSTAR_1.get());
                        output.accept(EMBERSTAR_2.get());
                        output.accept(EMBERSTAR_3.get());
                        output.accept(VOID_EMBERSTAR.get());
                        output.accept(SWORD_OF_THE_FREE_WILL.get());
                        output.accept(SWORD_OF_THE_FREE_WILL_ENHANCED.get());
                        output.accept(DAWN_STAR.get());
                        output.accept(DAWN_TEMPLATE.get());
                        output.accept(DAWN_AMULET.get());
                        output.accept(ANAHITA_FIRSTLIGHT_LIDDELL.get());
                        output.accept(LUO_HONGYU.get());
                        ItemStack guideBook = createGuideBookForGift();
                        if (!guideBook.isEmpty()) output.accept(guideBook);
                    }).build());

    // 食物分区：五种新增物品，图标暂用面粉
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FOOD_TAB =
            CREATIVE_MODE_TABS.register("food",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.endbound_ember.food"))
                            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                            .icon(() -> FLOUR.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(FLOUR.get());
                                output.accept(DRY_FRIED_FLOUR.get());
                                output.accept(WET_FRIED_FLOUR.get());
                                output.accept(CIVILIAN_RATION.get());
                                output.accept(MILITARY_RATION.get());
                            }).build());

    // 建筑分区：竖半砖与展示台，图标使用展示台
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_TAB =
            CREATIVE_MODE_TABS.register("building",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.endbound_ember.building"))
                            .withTabsBefore(ITEMS_TAB.getKey())
                            .icon(() -> DISPLAY_PEDESTAL_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                for (DeferredItem<net.minecraft.world.item.BlockItem> glass : SEAMLESS_GLASS_ITEMS) {
                                    output.accept(glass.get());
                                }
                                for (DeferredItem<net.minecraft.world.item.BlockItem> pane : SEAMLESS_GLASS_PANE_ITEMS) {
                                    output.accept(pane.get());
                                }
                                for (DeferredHolder<Block, ? extends Block> shape : GLASS_SHAPE_BLOCKS) {
                                    output.accept(shape.get().asItem());
                                }
                                output.accept(DISPLAY_PEDESTAL_ITEM.get());
                                for (DeferredItem<net.minecraft.world.item.BlockItem> pedestal : DISPLAY_PEDESTAL_VARIANT_ITEMS) {
                                    output.accept(pedestal.get());
                                }
                                output.accept(SMOOTH_QUARTZ_VERTICAL_SLAB.get());
                                output.accept(QUARTZ_VERTICAL_SLAB.get());
                                output.accept(STONE_BRICK_VERTICAL_SLAB.get());
                                output.accept(CRACKED_STONE_BRICK_VERTICAL_SLAB.get());
                                output.accept(MOSSY_STONE_BRICK_VERTICAL_SLAB.get());
                                output.accept(SMOOTH_STONE_VERTICAL_SLAB.get());
                                output.accept(DEEPSLATE_BRICK_VERTICAL_SLAB.get());
                                output.accept(POLISHED_DEEPSLATE_VERTICAL_SLAB.get());
                                output.accept(OAK_VERTICAL_SLAB.get());
                                output.accept(SPRUCE_VERTICAL_SLAB.get());
                                output.accept(BIRCH_VERTICAL_SLAB.get());
                                output.accept(JUNGLE_VERTICAL_SLAB.get());
                                output.accept(ACACIA_VERTICAL_SLAB.get());
                                output.accept(DARK_OAK_VERTICAL_SLAB.get());
                                output.accept(MANGROVE_VERTICAL_SLAB.get());
                                output.accept(CHERRY_VERTICAL_SLAB.get());
                                output.accept(BAMBOO_VERTICAL_SLAB.get());
                                output.accept(CRIMSON_VERTICAL_SLAB.get());
                                output.accept(WARPED_VERTICAL_SLAB.get());
                                output.accept(STONE_VERTICAL_SLAB.get());
                                output.accept(GRANITE_VERTICAL_SLAB.get());
                                output.accept(POLISHED_GRANITE_VERTICAL_SLAB.get());
                                output.accept(DIORITE_VERTICAL_SLAB.get());
                                output.accept(POLISHED_DIORITE_VERTICAL_SLAB.get());
                                output.accept(ANDESITE_VERTICAL_SLAB.get());
                                output.accept(POLISHED_ANDESITE_VERTICAL_SLAB.get());
                                output.accept(COBBLESTONE_VERTICAL_SLAB.get());
                                output.accept(MOSSY_COBBLESTONE_VERTICAL_SLAB.get());
                                output.accept(SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(SMOOTH_SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(CUT_SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(RED_SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(SMOOTH_RED_SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(CUT_RED_SANDSTONE_VERTICAL_SLAB.get());
                                output.accept(BRICK_VERTICAL_SLAB.get());
                                output.accept(MUD_BRICK_VERTICAL_SLAB.get());
                                output.accept(COBBLED_DEEPSLATE_VERTICAL_SLAB.get());
                                output.accept(DEEPSLATE_TILE_VERTICAL_SLAB.get());
                                output.accept(TUFF_VERTICAL_SLAB.get());
                                output.accept(POLISHED_TUFF_VERTICAL_SLAB.get());
                                output.accept(TUFF_BRICK_VERTICAL_SLAB.get());
                                output.accept(BLACKSTONE_VERTICAL_SLAB.get());
                                output.accept(POLISHED_BLACKSTONE_VERTICAL_SLAB.get());
                                output.accept(POLISHED_BLACKSTONE_BRICK_VERTICAL_SLAB.get());
                                output.accept(END_STONE_BRICK_VERTICAL_SLAB.get());
                                output.accept(PURPUR_VERTICAL_SLAB.get());
                                output.accept(PRISMARINE_VERTICAL_SLAB.get());
                                output.accept(PRISMARINE_BRICK_VERTICAL_SLAB.get());
                                output.accept(DARK_PRISMARINE_VERTICAL_SLAB.get());
                                output.accept(NETHER_BRICK_VERTICAL_SLAB.get());
                                output.accept(RED_NETHER_BRICK_VERTICAL_SLAB.get());
                                output.accept(BAMBOO_MOSAIC_VERTICAL_SLAB.get());
                                output.accept(CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(EXPOSED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(WEATHERED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(OXIDIZED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(WAXED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(WAXED_EXPOSED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(WAXED_WEATHERED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(WAXED_OXIDIZED_CUT_COPPER_VERTICAL_SLAB.get());
                                output.accept(PACKED_ICE_BRICK_VERTICAL_SLAB.get());
                                output.accept(SNOW_BRICK_VERTICAL_SLAB.get());
                                for (DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> holder
                                        : CONCRETE_VERTICAL_SLABS) {
                                    output.accept(holder.get());
                                }
                                for (DeferredHolder<Block, com.emberstar.endbounember.block.VerticalSlabBlock> holder
                                        : CALCITE_VERTICAL_SLABS) {
                                    output.accept(holder.get());
                                }
                                for (DeferredHolder<Block, ? extends Block> holder : MINERAL_VERTICAL_SLABS) {
                                    output.accept(holder.get().asItem());
                                }
                            }).build());

    // 生物分区：仅放置刷怪蛋
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOBS_TAB = CREATIVE_MODE_TABS.register("mobs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.endbound_ember.mobs"))
                    .withTabsBefore(ITEMS_TAB.getKey())
                    .icon(() -> ZOMBIE_GIRL_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ZOMBIE_GIRL_SPAWN_EGG.get());
                        output.accept(DROWNED_GIRL_SPAWN_EGG.get());
                        output.accept(HUMAN_GIRL_SPAWN_EGG.get());
                    }).build());

    // 附魔书分区：本模组的附魔书集中在此，避免混在原版附魔书里难以查找
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENCHANTMENT_BOOKS_TAB =
            CREATIVE_MODE_TABS.register("enchantment_books",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.endbound_ember.enchantment_books"))
                            .withTabsBefore(MOBS_TAB.getKey())
                            .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
                            .displayItems((parameters, output) -> {
                                output.accept(createEnchantedBook(parameters.holders(), DawnEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        CloudBowEnchantments.CLOUD_BOW, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        CloudBowEnchantments.ANCIENT_CLOUD_BOW, 1));
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            DurabilityBoostEnchantment.DURABILITY_BOOST, level));
                                }
                                output.accept(createEnchantedBook(parameters.holders(),
                                        CraftsmanshipEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        MadeInChinaEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        EndApproachesEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        FreeWillEnchantments.DAWN_BLOOM, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        FreeWillEnchantments.HOPE_BLOOM, 1));
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FreeWillEnchantments.ETERNAL_SPARK, level));
                                }
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            ExNihiloEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 5; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            QianpoQingMingSwordEnchantment.KEY, level));
                                }
                                output.accept(createEnchantedBook(parameters.holders(),
                                        DifficultyGiftEnchantment.KEY, 1));
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            ShockwaveEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            SkywardSlamEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FallingStarGuidanceEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            LightweightEnchantment.KEY, level));
                                }
                                output.accept(createEnchantedBook(parameters.holders(),
                                        WindRippleEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        ElegantCatwalkEnchantment.KEY, 1));
                                for (int level = 1; level <= 2; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            SpringHarvestEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 2; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            AllNatureReviveEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 2; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FertileBountyEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            AutoSmeltEnchantment.KEY, level));
                                }
                                for (int level = 1; level <= 5; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FreeWillEnchantments.STAGNATION_PIERCE, level));
                                }
                            }).build());

    /** 构建一本只带指定附魔与等级的附魔书。 */
    public static ItemStack createEnchantedBook(HolderLookup.Provider registries, ResourceKey<Enchantment> key,
            int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
    }

    /**
     * 构建《终界之书》：帕秋莉手册本体 + patchouli:book 组件。
     * 帕秋莉是软依赖，未安装时返回空栈，避免在分区里塞入无法打开的物品。
     */
    public static ItemStack createGuideBookForGift() {
        if (!ModList.get().isLoaded("patchouli")) return ItemStack.EMPTY;
        Item guideBook = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"));
        if (guideBook == null || guideBook == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(guideBook);
        DataComponentType<ResourceLocation> bookComponent = (DataComponentType<ResourceLocation>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
        if (bookComponent == null) return ItemStack.EMPTY;
        stack.set(bookComponent, ResourceLocation.fromNamespaceAndPath(MODID, MODID));
        return stack;
    }

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public EndboundEmber(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(com.emberstar.endbounember.network.GirlInteractionPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DawnSkillPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DawnEnergyPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.CloudArrowVelocityPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.FlywheelDashPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.TimeTurbulenceTogglePayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.TimeHaloSkillPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.EnchantmentPreviewPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.SwordParryPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.WallJumpPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DualWieldAttackPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DualWieldCooldownPayload::register);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        com.emberstar.endbounember.effect.ModEffects.MOB_EFFECTS.register(modEventBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entities get registered
        ModEntities.ENTITY_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (EndboundEmber) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

    }

    /** 冰砖、雪砖及其楼梯注入原版建筑方块分区，作为砖块家族插在泥砖墙之后 */
    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(COPPER_PICKAXE.get());
            event.accept(COPPER_AXE.get());
            event.accept(COPPER_SHOVEL.get());
            event.accept(COPPER_HOE.get());
        }

        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(COPPER_SWORD.get());
            event.accept(IRON_SPEAR.get());
            event.accept(COPPER_HELMET.get());
            event.accept(COPPER_CHESTPLATE.get());
            event.accept(COPPER_LEGGINGS.get());
            event.accept(COPPER_BOOTS.get());
        }

        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(DISENCHANTMENT_TABLE_ITEM.get());
            ItemStack packedIceBricks = PACKED_ICE_BRICKS_ITEM.get().getDefaultInstance();
            ItemStack packedIceBrickStairs = PACKED_ICE_BRICK_STAIRS_ITEM.get().getDefaultInstance();
            ItemStack snowBricks = SNOW_BRICKS_ITEM.get().getDefaultInstance();
            ItemStack snowBrickStairs = SNOW_BRICK_STAIRS_ITEM.get().getDefaultInstance();
            event.insertAfter(Items.MUD_BRICK_WALL.getDefaultInstance(), packedIceBricks,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.insertAfter(packedIceBricks, packedIceBrickStairs,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.insertAfter(packedIceBrickStairs, snowBricks,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.insertAfter(snowBricks, snowBrickStairs,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        // 混凝土楼梯与半砖：高版本内容，追加到原版建筑方块分区
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (int i = 0; i < CONCRETE_STAIRS_ITEMS.size(); i++) {
                event.accept(CONCRETE_STAIRS_ITEMS.get(i).get().getDefaultInstance());
                event.accept(CONCRETE_SLAB_ITEMS.get(i).get().getDefaultInstance());
            }
        }

        // 冰砖半砖、墙与雕纹冰砖：补齐高版本冰砖家族
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(PACKED_ICE_BRICK_SLAB_ITEM.get().getDefaultInstance());
            event.accept(PACKED_ICE_BRICK_WALL_ITEM.get().getDefaultInstance());
            event.accept(CHISELED_PACKED_ICE_ITEM.get().getDefaultInstance());
        }

        // 方解石家族：柱、基础方块、楼梯、半砖与墙，追加到原版建筑方块分区
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (DeferredItem<net.minecraft.world.item.BlockItem> item : CALCITE_BUILDING_ITEMS) {
                event.accept(item.get().getDefaultInstance());
            }
        }

        // 矿物台阶与半砖：追加到原版建筑方块分区（竖半砖与展示台仍在模组建筑分区）
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (DeferredItem<net.minecraft.world.item.BlockItem> item : MINERAL_BUILDING_ITEMS) {
                event.accept(item.get().getDefaultInstance());
            }
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
