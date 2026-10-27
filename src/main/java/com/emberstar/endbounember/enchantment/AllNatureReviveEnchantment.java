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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 万物回春：锄类专属，最高 II 级。
 * 右键未成熟作物时范围催熟，每催熟一块消耗 1 个骨粉；背包没有骨粉时完全不生效。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AllNatureReviveEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "all_nature_revive"));

    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    private AllNatureReviveEnchantment() {
    }

    /** 读取物品上的万物回春附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 范围催熟，由春华秋实的右键分流调用。 */
    public static boolean reviveArea(ServerLevel level, Player player, BlockPos center, int reviveLevel) {
        int radius = radiusOf(reviveLevel);
        double chance = chanceOf(reviveLevel);
        if (radius <= 0 || chance <= 0) return false;

        // 背包没有骨粉时本次催熟完全不生效
        if (player.getInventory().countItem(Items.BONE_MEAL) <= 0) return false;

        boolean any = false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) continue;
            if (level.random.nextDouble() >= chance) continue;

            // 每催熟一块作物扣一个骨粉，不足则停止后续催熟
            if (!consumeOneBoneMeal(player)) break;

            crop.performBonemeal(level, level.random, pos, state);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0);
            any = true;
        }

        if (any) {
            level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return any;
    }

    /** 从玩家背包消耗一个骨粉，成功返回 true。 */
    private static boolean consumeOneBoneMeal(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (!slot.isEmpty() && slot.is(Items.BONE_MEAL)) {
                slot.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** 作用半径：I 级 3×3，II 级 5×5。 */
    public static int radiusOf(int level) {
        return level >= 2 ? 2 : (level >= 1 ? 1 : 0);
    }

    /** 催熟概率：I 级 60%，II 级 85%。 */
    public static double chanceOf(int level) {
        return level >= 2 ? 0.85D : (level >= 1 ? 0.6D : 0.0D);
    }

    /** 物品提示：在附魔名称之后追加效果说明与催熟概率。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = levelOf(event.getItemStack());
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.all_nature_revive").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.all_nature_revive.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.all_nature_revive.chance",
                String.format(Locale.ROOT, "%.0f", chanceOf(level) * 100.0D)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
