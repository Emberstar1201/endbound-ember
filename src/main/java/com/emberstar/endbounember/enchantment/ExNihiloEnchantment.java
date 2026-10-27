package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 无中生有：镐类工具与剪刀专属，最高 III 级。
 * 挖掘石质方块时额外掉落矿物（稀有池：钻石 / 下界合金碎片 / 下界合金锭）；
 * 剪刀采集树叶时按等级概率额外掉落金苹果与附魔金苹果。
 * 与精准采集互斥。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ExNihiloEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ex_nihilo"));

    /** 每级触发稀有池的概率。 */
    private static final double RARE_CHANCE_PER_LEVEL = 0.01D;
    private static final int DIAMOND_WEIGHT = 70;
    private static final int SCRAP_WEIGHT = 25;
    private static final int INGOT_WEIGHT = 5;
    private static final double GOLDEN_APPLE_CHANCE = 0.005D;
    private static final double ENCHANTED_GOLDEN_APPLE_CHANCE = 0.0005D;
    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    /** 会触发额外掉落的石质基底方块。 */
    private static final Set<String> STONE_BLOCKS = Set.of(
            // 主世界
            "minecraft:stone", "minecraft:smooth_stone",
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
            "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
            "minecraft:cobblestone", "minecraft:mossy_cobblestone",
            "minecraft:granite", "minecraft:polished_granite",
            "minecraft:diorite", "minecraft:polished_diorite",
            "minecraft:andesite", "minecraft:polished_andesite",
            "minecraft:deepslate", "minecraft:polished_deepslate",
            "minecraft:deepslate_bricks", "minecraft:deepslate_tiles",
            "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles",
            "minecraft:chiseled_deepslate",
            "minecraft:tuff", "minecraft:polished_tuff",
            "minecraft:tuff_bricks", "minecraft:cracked_tuff_bricks",
            "minecraft:chiseled_tuff",
            // 下界
            "minecraft:netherrack",
            "minecraft:basalt", "minecraft:polished_basalt", "minecraft:smooth_basalt",
            "minecraft:blackstone", "minecraft:polished_blackstone",
            "minecraft:blackstone_bricks", "minecraft:polished_blackstone_bricks",
            "minecraft:cracked_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks",
            "minecraft:chiseled_blackstone", "minecraft:gilded_blackstone",
            // 末地
            "minecraft:end_stone", "minecraft:end_stone_bricks");

    private ExNihiloEnchantment() {
    }

    /** 读取物品上的无中生有附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int level = levelOf(stack, DataComponents.ENCHANTMENTS);
        if (level > 0) return level;
        return levelOf(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static int levelOf(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /**
     * 破坏方块时追加额外掉落。
     * 不取消事件，原版掉落照常生成，这里只额外 popResource 一次。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;

        Player player = event.getPlayer();
        Level level = player.level();
        if (level.isClientSide()) return;
        if (player.isCreative()) return;

        ItemStack tool = player.getMainHandItem();
        int enchantLevel = levelOf(tool);
        if (enchantLevel <= 0) return;

        BlockState state = event.getState();

        // 剪刀：采集树叶时概率额外掉落金苹果
        if (tool.is(Items.SHEARS)) {
            if (state.is(BlockTags.LEAVES) && level instanceof ServerLevel serverLevel) {
                handleShearDrop(serverLevel, event.getPos(), enchantLevel);
            }
            return;
        }

        // 镐：挖掘石质方块时额外爆矿
        if (!tool.isCorrectToolForDrops(state)) return;
        if (!STONE_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack drop = rollDrop(serverLevel, enchantLevel);
        if (drop.isEmpty()) return;

        Block.popResource(level, event.getPos(), drop);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("endbound_ember.ex_nihilo.drop",
                    String.valueOf(drop.getCount()), drop.getHoverName()), true);
        }
    }

    private static ItemStack rollDrop(ServerLevel level, int enchantLevel) {
        if (level.random.nextDouble() < RARE_CHANCE_PER_LEVEL * enchantLevel) {
            return rollRareDrop(level);
        }
        return rollCommonDrop(level);
    }

    private static ItemStack rollRareDrop(ServerLevel level) {
        int roll = level.random.nextInt(DIAMOND_WEIGHT + SCRAP_WEIGHT + INGOT_WEIGHT);
        if (roll < DIAMOND_WEIGHT) return new ItemStack(Items.DIAMOND);
        if (roll < DIAMOND_WEIGHT + SCRAP_WEIGHT) return new ItemStack(Items.NETHERITE_SCRAP);
        return new ItemStack(Items.NETHERITE_INGOT);
    }

    private static ItemStack rollCommonDrop(ServerLevel level) {
        ItemStack[] commonDrops = {
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.IRON_NUGGET)
        };
        return commonDrops[level.random.nextInt(commonDrops.length)];
    }

    /** 剪刀模式：等级倍率 I:1×, II:1.5×, III:2×。 */
    private static void handleShearDrop(ServerLevel level, BlockPos pos, int enchantLevel) {
        double levelMultiplier = 1.0D + (enchantLevel - 1) * 0.5D;
        if (level.random.nextDouble() < GOLDEN_APPLE_CHANCE * levelMultiplier) {
            Block.popResource(level, pos, new ItemStack(Items.GOLDEN_APPLE));
        }
        if (level.random.nextDouble() < ENCHANTED_GOLDEN_APPLE_CHANCE * levelMultiplier) {
            Block.popResource(level, pos, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        }
    }

    /** 物品提示：在附魔名称之后追加效果说明与当前等级稀有概率。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.ex_nihilo").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.ex_nihilo.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.ex_nihilo.chance",
                String.format(Locale.ROOT, "%.0f", RARE_CHANCE_PER_LEVEL * level * 100.0D)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
