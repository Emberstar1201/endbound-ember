package com.emberstar.endbounember;

import org.slf4j.Logger;

import com.emberstar.endbounember.enchantment.CloudBowEnchantments;
import com.emberstar.endbounember.enchantment.CraftsmanshipEnchantment;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.DurabilityBoostEnchantment;
import com.emberstar.endbounember.enchantment.EndApproachesEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.item.EndStarItem;
import com.emberstar.endbounember.item.LifeStarItem;
import com.emberstar.endbounember.item.StardustCordBraceletItem;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;
import com.emberstar.endbounember.item.UnownedStardustItem;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
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

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    // 友好生物标签（与 minecraft:friendly_mobs 内容一致，供本模组实体直接绑定）
    public static final TagKey<EntityType<?>> FRIENDLY_MOB_TAG = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "friendly_mobs"));

    public static final DeferredItem<Item> LIFE_STAR = ITEMS.register("life_star",
            () -> new LifeStarItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> END_STAR = ITEMS.register("end_star",
            () -> new EndStarItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // 拂晓：通过附魔书形式发放，附魔等级即成长层数（逻辑见 DawnEnchantment）

    public static final DeferredItem<StardustCordBraceletItem> STARDUST_CORD_BRACELET = ITEMS.register(
            "stardust_cord_bracelet", () -> new StardustCordBraceletItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<UnownedStardustItem> UNOWNED_STARDUST = ITEMS.register(
            "unowned_stardust", () -> new UnownedStardustItem(new Item.Properties()
                    .stacksTo(1).fireResistant()));

    // 人权剑：攻击伤害 30 / 攻速 4.5（属性值为相对玩家基础值的偏移）
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL = ITEMS.register(
            "sword_of_the_free_will", () -> new SwordOfTheFreeWillItem(false, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(29.0D, 0.5D))));

    // 人权剑·星火永燃：攻击伤害 40 / 攻速 4.5
    public static final DeferredItem<SwordOfTheFreeWillItem> SWORD_OF_THE_FREE_WILL_ENHANCED = ITEMS.register(
            "sword_of_the_free_will_enhanced", () -> new SwordOfTheFreeWillItem(true, new Item.Properties()
                    .stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .attributes(SwordOfTheFreeWillItem.attributes(39.0D, 0.5D))));

    // 晨曦之星：夜间再次击杀凋灵获得，锻造升级素材
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
                        output.accept(STARDUST_CORD_BRACELET.get());
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
                                        EndApproachesEnchantment.KEY, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        FreeWillEnchantments.DAWN_BLOOM, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        FreeWillEnchantments.HOPE_BLOOM, 1));
                                output.accept(createEnchantedBook(parameters.holders(),
                                        FreeWillEnchantments.WE_FALL_WE_REMAIN, 1));
                                for (int level = 1; level <= 3; level++) {
                                    output.accept(createEnchantedBook(parameters.holders(),
                                            FreeWillEnchantments.ETERNAL_SPARK, level));
                                }
                            }).build());

    /** 构建一本只带指定附魔与等级的附魔书。 */
    private static ItemStack createEnchantedBook(HolderLookup.Provider registries, ResourceKey<Enchantment> key,
            int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
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

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
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

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
