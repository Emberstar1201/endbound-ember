package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 丰饶之息：锄类专属，最高 II 级。
 * 手持时以自身为中心持续催熟周围作物；配合「春华秋实」收获时掉落翻倍。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FertileBountyEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "fertile_bounty"));

    /** 光环半径（水平方向，单位：格）。 */
    private static final int AURA_RADIUS = 10;
    /** 光环触发间隔（单位：tick）。 */
    private static final int GROWTH_INTERVAL_TICKS = 20;
    /** 光环垂直范围（玩家所在高度 ± 该值）。 */
    private static final int VERTICAL_RANGE = 4;
    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    private FertileBountyEnchantment() {
    }

    /** 读取物品上的丰饶之息附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 手持带丰饶之息的工具时，春华秋实的收获掉落翻倍。 */
    public static boolean isDoublingHarvest(ItemStack stack) {
        return levelOf(stack) > 0;
    }

    /** 每秒催熟概率：I 级 12%，II 级 22%。 */
    public static double chanceOf(int level) {
        return level >= 2 ? 0.22D : (level >= 1 ? 0.12D : 0.0D);
    }

    /** 手持光环：定时扫描玩家周围的未成熟作物并催熟。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int level = levelOf(player.getMainHandItem());
        if (level <= 0) return;
        if (player.tickCount % GROWTH_INTERVAL_TICKS != 0) return;

        double chance = chanceOf(level);
        if (chance <= 0) return;

        ServerLevel serverLevel = player.serverLevel();
        int yMin = (int) Math.floor(player.getY()) - VERTICAL_RANGE;
        int yMax = (int) Math.ceil(player.getY()) + VERTICAL_RANGE;
        BlockPos minPos = new BlockPos(player.getBlockX() - AURA_RADIUS, yMin,
                player.getBlockZ() - AURA_RADIUS);
        BlockPos maxPos = new BlockPos(player.getBlockX() + AURA_RADIUS, yMax,
                player.getBlockZ() + AURA_RADIUS);

        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
                    && serverLevel.random.nextDouble() < chance) {
                crop.performBonemeal(serverLevel, serverLevel.random, pos, state);
                grown++;
            }
        }

        if (grown > 0) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    Math.min(grown, 10), 1.5, 1.0, 1.5, 0);
        }
    }

    /** 物品提示：在附魔名称之后追加效果说明、光环半径与催熟概率。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = levelOf(event.getItemStack());
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.fertile_bounty").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.fertile_bounty.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.fertile_bounty.radius", AURA_RADIUS));
        tooltip.add(insertAt + 2, line("endbound_ember.fertile_bounty.chance",
                String.format(Locale.ROOT, "%.0f", chanceOf(level) * 100.0D)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
