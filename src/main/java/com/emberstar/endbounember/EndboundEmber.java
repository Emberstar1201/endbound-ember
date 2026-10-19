package com.emberstar.endbounember;

import org.slf4j.Logger;

import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.item.LifeStarItem;
import com.emberstar.endbounember.item.UnownedStardustItem;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
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
    public static final DeferredHolder<MenuType<?>, MenuType<com.emberstar.endbounember.entity.ZombieGirl.CompanionMenu>> COMPANION_MENU =
            MENU_TYPES.register("companion_inventory", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new com.emberstar.endbounember.entity.ZombieGirl.CompanionMenu(id, inventory, buffer.readVarInt())));

    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_1 = registerSound("husk_girl_idle_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_2 = registerSound("husk_girl_idle_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_IDLE_3 = registerSound("husk_girl_idle_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_HUST_1 = registerSound("husk_girl_hust_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_HUST_2 = registerSound("husk_girl_hust_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUSK_GIRL_FAREWELL_1 = registerSound("husk_girl_farewell_1");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    // 友好生物标签（与 minecraft:friendly_mobs 内容一致，供本模组实体直接绑定）
    public static final TagKey<EntityType<?>> FRIENDLY_MOB_TAG = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, "friendly_mobs"));

    // Creates a new Block with the id "endbound_ember:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "endbound_ember:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "endbound_ember:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    public static final DeferredItem<Item> LIFE_STAR = ITEMS.register("life_star",
            () -> new LifeStarItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<UnownedStardustItem> UNOWNED_STARDUST = ITEMS.register(
            "unowned_stardust", () -> new UnownedStardustItem(new Item.Properties()
                    .stacksTo(1).fireResistant()));

    public static final DeferredItem<SpawnEggItem> ZOMBIE_GIRL_SPAWN_EGG = ITEMS.register("zombie_girl_spawn_egg",
            () -> new SpawnEggItem(ModEntities.ZOMBIE_GIRL.get(), 0x4A5B4A, 0xB7B7B7, new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> DROWNED_GIRL_SPAWN_EGG = ITEMS.register("drowned_girl_spawn_egg",
            () -> new SpawnEggItem(ModEntities.DROWNED_GIRL.get(), 0x2B5D66, 0x5AB5D1, new Item.Properties()));

    // Creates a creative tab with the id "endbound_ember:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.endbound_ember")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
                output.accept(ZOMBIE_GIRL_SPAWN_EGG.get());
                output.accept(DROWNED_GIRL_SPAWN_EGG.get());
                output.accept(LIFE_STAR.get());
                output.accept(UNOWNED_STARDUST.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public EndboundEmber(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entities get registered
        ModEntities.ENTITY_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (EndboundEmber) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

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

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ZOMBIE_GIRL_SPAWN_EGG);
            event.accept(DROWNED_GIRL_SPAWN_EGG);
            event.accept(LIFE_STAR);
            event.accept(UNOWNED_STARDUST);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
