package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 自动冶炼：镐类专属，最高 III 级。
 * 挖掘矿石时直接掉落冶炼成品，并按等级概率额外产出；与精准采集互斥。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AutoSmeltEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "auto_smelt"));

    /** 会触发自动冶炼的矿石方块。 */
    private static final Set<String> ORE_BLOCKS = Set.of(
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:raw_iron_block",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:raw_copper_block",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:raw_gold_block",
            "minecraft:nether_gold_ore",
            "minecraft:ancient_debris");

    /** 粗矿 → 冶炼成品。 */
    private static final Map<Item, Item> SMELT_MAP = Map.of(
            Items.RAW_IRON, Items.IRON_INGOT,
            Items.RAW_COPPER, Items.COPPER_INGOT,
            Items.RAW_GOLD, Items.GOLD_INGOT,
            Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);

    /** II 级额外 +1 的概率。 */
    private static final double LEVEL2_BONUS_CHANCE = 0.33D;
    /** III 级额外 +1~2 的概率。 */
    private static final double LEVEL3_BONUS_CHANCE = 0.5D;
    private static final double XP_MIN = 0.5D;
    private static final double XP_MAX = 1.0D;
    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    private AutoSmeltEnchantment() {
    }

    /** 读取物品上的自动冶炼附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 挖掘矿石时取消原版掉落，改为按冶炼结果重新生成掉落物。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;

        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        ItemStack tool = player.getMainHandItem();
        int enchantLevel = levelOf(tool);
        if (enchantLevel <= 0) return;

        BlockState state = event.getState();
        if (state.isAir()) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!ORE_BLOCKS.contains(blockId.toString())) return;

        // 取消原版破坏流程，防止默认掉落与经验重复生成
        event.setCanceled(true);

        BlockPos pos = event.getPos();
        List<ItemStack> originalDrops = Block.getDrops(state, serverLevel, pos, null, player, tool);
        List<ItemStack> finalDrops = new ArrayList<>(originalDrops.size());

        for (ItemStack drop : originalDrops) {
            Item smelted = SMELT_MAP.get(drop.getItem());
            if (smelted == null) {
                finalDrops.add(drop.copy());
                continue;
            }

            int count = drop.getCount();
            if (enchantLevel >= 2 && serverLevel.random.nextDouble() < LEVEL2_BONUS_CHANCE) {
                count += 1;
            }
            if (enchantLevel >= 3 && serverLevel.random.nextDouble() < LEVEL3_BONUS_CHANCE) {
                count += 1 + serverLevel.random.nextInt(2);
            }
            finalDrops.add(new ItemStack(smelted, count));
        }

        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        serverLevel.levelEvent(2001, pos, Block.getId(state));

        for (ItemStack stack : finalDrops) {
            Block.popResource(serverLevel, pos, stack);
        }

        double xp = XP_MIN + serverLevel.random.nextDouble() * (XP_MAX - XP_MIN);
        int totalXp = Math.max(1, (int) Math.floor(xp));
        serverLevel.addFreshEntity(new ExperienceOrb(serverLevel,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, totalXp));
    }

    /** 额外产出概率：I 级 0%，II 级 33%，III 级 50%。 */
    private static double bonusChanceOf(int level) {
        if (level >= 3) return LEVEL3_BONUS_CHANCE;
        if (level >= 2) return LEVEL2_BONUS_CHANCE;
        return 0.0D;
    }

    /** 物品提示：在附魔名称之后追加效果说明与额外产出概率。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = levelOf(event.getItemStack());
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.auto_smelt").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.auto_smelt.desc"));
        if (bonusChanceOf(level) > 0) {
            tooltip.add(insertAt + 1, line("endbound_ember.auto_smelt.bonus",
                    String.format(Locale.ROOT, "%.0f", bonusChanceOf(level) * 100.0D)));
        }
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
