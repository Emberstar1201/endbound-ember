package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 春华秋实：锄类专属，最高 II 级。
 * 右键成熟作物时范围收获并补种，右键土地时范围开垦。
 * 同一把锄头同时附有「万物回春」时，右键未成熟作物会转为范围催熟。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SpringHarvestEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "spring_harvest"));

    /** 每开垦一块土地的耐久消耗。 */
    private static final int DURABILITY_PER_BLOCK = 1;
    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    private SpringHarvestEnchantment() {
    }

    /** 读取物品上的春华秋实附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 右键分流：成熟作物 → 收获补种；未成熟作物 → 催熟；土地 → 开垦。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        ItemStack hoe = player.getMainHandItem();
        int springLevel = levelOf(hoe);
        int reviveLevel = AllNatureReviveEnchantment.levelOf(hoe);
        if (springLevel <= 0 && reviveLevel <= 0) return;

        BlockPos center = event.getPos();
        BlockState centerState = serverLevel.getBlockState(center);

        boolean handled = false;
        if (springLevel > 0 && centerState.getBlock() instanceof CropBlock crop && crop.isMaxAge(centerState)) {
            handled = harvestArea(serverLevel, player, center, springLevel, hoe);
        } else if (reviveLevel > 0 && centerState.getBlock() instanceof CropBlock crop
                && !crop.isMaxAge(centerState)) {
            handled = AllNatureReviveEnchantment.reviveArea(serverLevel, player, center, reviveLevel);
        } else if (springLevel > 0 && isTillable(centerState)) {
            handled = tillArea(serverLevel, player, center, springLevel, hoe);
        }

        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 范围开垦：把半径内的泥土类方块转为耕地，每块消耗 1 点耐久。 */
    private static boolean tillArea(ServerLevel level, Player player, BlockPos center, int springLevel,
            ItemStack hoe) {
        int radius = radiusOf(springLevel);
        if (radius <= 0) return false;

        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            BlockState tilled = getTilledState(level, pos, state);
            if (tilled == null) continue;

            level.setBlock(pos, tilled, 3);
            level.levelEvent(2001, pos, Block.getId(tilled));
            level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            count++;
        }

        if (count <= 0) return false;

        if (!player.isCreative()) {
            hoe.hurtAndBreak(DURABILITY_PER_BLOCK * count, player, EquipmentSlot.MAINHAND);
        }
        level.playSound(null, center, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    /** 判断方块是否可被锄头开垦。 */
    private static boolean isTillable(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT;
    }

    /** 返回开垦后的方块状态，不可开垦时返回 null。 */
    private static BlockState getTilledState(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.GRASS_BLOCK && block != Blocks.DIRT_PATH && block != Blocks.DIRT
                && block != Blocks.COARSE_DIRT && block != Blocks.ROOTED_DIRT) {
            return null;
        }
        // 上方有方块时不能变成耕地
        if (!level.getBlockState(pos.above()).isAir()) return null;
        return Blocks.FARMLAND.defaultBlockState();
    }

    /** 范围收获并补种，附有丰饶之息时掉落翻倍。 */
    private static boolean harvestArea(ServerLevel level, Player player, BlockPos center, int springLevel,
            ItemStack hoe) {
        int radius = radiusOf(springLevel);
        if (radius <= 0) return false;

        boolean doubleDrop = FertileBountyEnchantment.isDoublingHarvest(hoe);
        boolean any = false;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;

            // 生成掉落物，暂不破坏方块，避免二次掉落
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, hoe);
            if (doubleDrop) drops = doubleDrops(drops);

            // 补种：从掉落物中取出一个种子
            Item seed = getSeedItem(crop);
            boolean replanted = false;
            for (ItemStack drop : drops) {
                if (!replanted && drop.is(seed) && drop.getCount() > 0) {
                    drop.shrink(1);
                    replanted = true;
                    break;
                }
            }

            // 掉落物给玩家，背包满则落到作物位置上方
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                if (!player.addItem(drop)) {
                    ItemEntity itemEntity = new ItemEntity(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                    itemEntity.setDefaultPickUpDelay();
                    level.addFreshEntity(itemEntity);
                }
            }

            if (replanted) {
                level.setBlock(pos, crop.getStateForAge(0), 3);
            } else {
                level.destroyBlock(pos, false);
            }

            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
            any = true;
        }

        if (any) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                    8, radius, 0.5, radius, 0);
        }
        return any;
    }

    private static List<ItemStack> doubleDrops(List<ItemStack> drops) {
        List<ItemStack> result = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack copy = drop.copy();
            copy.setCount(drop.getCount() * 2);
            result.add(copy);
        }
        return result;
    }

    /** 按作物类型获取补种种子。 */
    public static Item getSeedItem(CropBlock crop) {
        if (crop instanceof CarrotBlock) return Items.CARROT;
        if (crop instanceof PotatoBlock) return Items.POTATO;
        if (crop instanceof BeetrootBlock) return Items.BEETROOT_SEEDS;
        return Items.WHEAT_SEEDS;
    }

    /** 作用半径：I 级 3×3，II 级 5×5。 */
    public static int radiusOf(int level) {
        return level >= 2 ? 2 : (level >= 1 ? 1 : 0);
    }

    /** 物品提示：在附魔名称之后追加效果说明与作用范围。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = levelOf(event.getItemStack());
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.spring_harvest").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.spring_harvest.desc"));
        int side = radiusOf(level) * 2 + 1;
        tooltip.add(insertAt + 1, line("endbound_ember.spring_harvest.radius", side, side));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
