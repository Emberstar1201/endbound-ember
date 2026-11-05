package com.emberstar.endbounember;

import org.slf4j.Logger;

import com.emberstar.endbounember.enchantment.AllNatureReviveEnchantment;
import com.emberstar.endbounember.enchantment.AutoSmeltEnchantment;
import com.emberstar.endbounember.enchantment.CloudBowEnchantments;
import com.emberstar.endbounember.enchantment.ChainBreakerEnchantment;
import com.emberstar.endbounember.enchantment.CraftsmanshipEnchantment;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.DifficultyGiftEnchantment;
import com.emberstar.endbounember.enchantment.DurabilityBoostEnchantment;
import com.emberstar.endbounember.enchantment.ElegantCatwalkEnchantment;
import com.emberstar.endbounember.enchantment.EndApproachesEnchantment;
import com.emberstar.endbounember.enchantment.ExNihiloEnchantment;
import com.emberstar.endbounember.enchantment.FallingStarGuidanceEnchantment;
import com.emberstar.endbounember.enchantment.FertileBountyEnchantment;
import com.emberstar.endbounember.enchantment.FlywheelEffectEnchantment;
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
import com.emberstar.endbounember.item.StardustWitchHaloItem;
import com.emberstar.endbounember.item.TimeHaloItem;
import com.emberstar.endbounember.item.StarlightStarItem;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;
import com.emberstar.endbounember.item.UnownedStardustItem;
import com.emberstar.endbounember.item.VoidStarItem;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    /** 展示台方块：可放置任意物品并悬浮展示，后续作为仪式方块 */
    public static final DeferredHolder<Block, com.emberstar.endbounember.block.DisplayPedestalBlock> DISPLAY_PEDESTAL =
            BLOCKS.registerBlock("display_pedestal",
                    com.emberstar.endbounember.block.DisplayPedestalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            // 徒手挖掘约 10 秒（无工具需求时破坏速度按 hardness × 30 tick 计算）
                            .strength(6.7F, 6.0F)
                            .noOcclusion()
                            // 使用完整方块的遮挡形状，避免空手/持物右键时被判定为「可穿过」而不触发交互
                            .isViewBlocking((state, level, pos) -> true)
                            .isSuffocating((state, level, pos) -> false));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<com.emberstar.endbounember.block.DisplayPedestalBlockEntity>>
            DISPLAY_PEDESTAL_BLOCK_ENTITY = BLOCK_ENTITIES.register("display_pedestal",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(com.emberstar.endbounember.block.DisplayPedestalBlockEntity::new,
                                    DISPLAY_PEDESTAL.get())
                            .build(null));

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

    // 人权剑：攻击伤害 30 / 攻速 4.5 / 护甲 10（属性值为相对玩家基础值的偏移）
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL = ITEMS.register(
            "sword_of_the_free_will", () -> new SwordOfTheFreeWillItem(false, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(29.0D, 0.5D, 10.0D, 0.0D))));

    // 人权剑·星火永燃：攻击伤害 40 / 攻速 4.5 / 护甲 100 / 盔甲韧性 25
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL_ENHANCED = ITEMS.register(
            "sword_of_the_free_will_enhanced", () -> new SwordOfTheFreeWillItem(true, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(39.0D, 0.5D, 100.0D, 25.0D))));

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
                        output.accept(LUO_HONGYU.get());
                        ItemStack guideBook = createGuideBook();
                        if (!guideBook.isEmpty()) output.accept(guideBook);
                    }).build());

    // 建筑分区：竖半砖与展示台，图标使用展示台
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_TAB =
            CREATIVE_MODE_TABS.register("building",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.endbound_ember.building"))
                            .withTabsBefore(ITEMS_TAB.getKey())
                            .icon(() -> DISPLAY_PEDESTAL_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(DISPLAY_PEDESTAL_ITEM.get());
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
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FlywheelEffectEnchantment.KEY, level));
                                }
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
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            ChainBreakerEnchantment.KEY, level));
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
    private static ItemStack createGuideBook() {
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
        modEventBus.addListener(com.emberstar.endbounember.network.GirlInteractionPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DawnSkillPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.DawnEnergyPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.CloudArrowVelocityPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.FlywheelDashPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.ChainBreakerPayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.TimeTurbulenceTogglePayload::register);
        modEventBus.addListener(com.emberstar.endbounember.network.TimeHaloSkillPayload::register);

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
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
