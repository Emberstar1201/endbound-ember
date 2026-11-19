package com.emberstar.endbounember.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.entity.EmberStarSorceressEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = "endbound_ember")
public final class ChainMiningEvents {
    private static final int MAX_EXTRA_BLOCKS = 64;
    private static final int MAX_FARM_SCAN = 1024;
    private static final float SATURATION_COST_PER_BLOCK = 0.01F;
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    /** 石材家族：挖掘其中任意一种时，可连锁同家族的其他天然石材。 */
    private static final Set<Block> STONE_FAMILY = Set.of(
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE);
    /** 深板岩矿归一到对应的浅层矿，用于识别混搭矿脉中的同种矿石。 */
    private static final Map<Block, Block> DEEPSLATE_ORE_BASES = Map.of(
            Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_ORE,
            Blocks.DEEPSLATE_IRON_ORE, Blocks.IRON_ORE,
            Blocks.DEEPSLATE_COPPER_ORE, Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE, Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.EMERALD_ORE);
    /** 水平四方向，用于连锁耕种时沿耕地扩散。 */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
    /** 可连锁播种的种子与其对应作物（不含需要占两格的瓶子草，避免只放下半截）。 */
    private static final Map<Item, BlockState> SEED_CROPS = Map.of(
            Items.WHEAT_SEEDS, Blocks.WHEAT.defaultBlockState(),
            Items.BEETROOT_SEEDS, Blocks.BEETROOTS.defaultBlockState(),
            Items.CARROT, Blocks.CARROTS.defaultBlockState(),
            Items.POTATO, Blocks.POTATOES.defaultBlockState(),
            Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM.defaultBlockState(),
            Items.MELON_SEEDS, Blocks.MELON_STEM.defaultBlockState(),
            Items.TORCHFLOWER_SEEDS, Blocks.TORCHFLOWER_CROP.defaultBlockState());

    private ChainMiningEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || PROCESSING.get() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || player.isSpectator()) return;

        ChainMiningState.State playerState = ChainMiningState.get(player);
        if (!playerState.enabled()) return;

        // 创造模式仅豁免工具限制（空手、挖掘等级），连锁数量上限仍然生效
        boolean creativeMode = player.isCreative();

        BlockPos origin = event.getPos().immutable();
        BlockState target = event.getState();
        ItemStack tool = player.getMainHandItem();
        if (!isEligible(target, level, origin) || !player.getAbilities().mayBuild
                || !player.mayUseItemAt(origin, Direction.UP, tool)) return;
        if (!creativeMode && tool.isEmpty()) return;

        List<BlockPos> candidates = findCandidates(level, player, origin, target, playerState.mode());
        if (candidates.isEmpty()) return;

        PROCESSING.set(true);
        try {
            int durability = 0;
            for (BlockPos pos : candidates) {
                if (!creativeMode && !player.mayUseItemAt(pos, Direction.UP, tool)) continue;
                BlockState state = level.getBlockState(pos);
                if (!sameFamily(target, state) || !isEligible(state, level, pos)
                        || (!creativeMode && state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) continue;
                if (!creativeMode && !Config.CHAIN_MINING_DURABILITY_PROTECTION.get()
                        && durability >= MAX_EXTRA_BLOCKS) break;

                List<ItemStack> drops = creativeMode ? List.of()
                        : Block.getDrops(state, level, pos, null, player, tool);
                if (!level.removeBlock(pos, false)) continue;
                level.levelEvent(2001, pos, Block.getId(state));
                for (ItemStack drop : drops) Block.popResource(level, pos, drop);
                EmberStarSorceressEvents.addMinedBlocks(player, 1);
                // 每个额外连锁方块消耗 0.01 点饱和度；只在服务端实际破坏成功后收取。
                if (!creativeMode) {
                    player.getFoodData().setSaturation(
                            Math.max(0.0F, player.getFoodData().getSaturationLevel() - SATURATION_COST_PER_BLOCK));
                }
                if (!creativeMode && !Config.CHAIN_MINING_DURABILITY_PROTECTION.get()) {
                    tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    durability++;
                    if (tool.isEmpty()) break;
                }
            }
        } finally {
            PROCESSING.set(false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChainMiningState.remove(event.getEntity().getUUID());
    }

    /**
     * 耕种模式的右键入口：只接管“右键方块”这一动作，且仅在服务端执行。
     * 手持锄头 → 一键开垦 8×8 耕地；手持种子 → 把种子沿附近耕地扩散播种。
     * 不取消事件，让原版逻辑在已被占用的位置自然失败即可。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        if (player.isSpectator() || !player.getAbilities().mayBuild) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ChainMiningState.State state = ChainMiningState.get(player);
        if (!state.enabled() || state.mode() != ChainMiningState.Mode.FARMING) return;

        BlockPos origin = event.getPos();
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof HoeItem) {
            tillArea(level, player, origin, held);
        } else if (SEED_CROPS.containsKey(held.getItem())) {
            plantArea(level, player, origin, held);
        }
    }

    /** 一键开垦：以点击的方块为锚点向四周铺出 8×8 的耕地（dx/dz 取 -3..4）。 */
    private static void tillArea(ServerLevel level, ServerPlayer player, BlockPos origin, ItemStack tool) {
        if (!isTillable(level.getBlockState(origin))) return;
        boolean creative = player.isCreative();
        for (int dx = -3; dx <= 4; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                BlockPos pos = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(pos) || !level.isEmptyBlock(pos.above())) continue;
                if (!isTillable(level.getBlockState(pos))) continue;

                level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                // 创造模式不消耗耐久，生存模式每开垦一格扣 1 点
                if (!creative) {
                    tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    if (tool.isEmpty()) return;
                }
            }
        }
    }

    /** 一键播种：从点击的耕地出发，水平四方向 BFS，最多播种 64 株，生存模式逐株消耗背包种子。 */
    private static void plantArea(ServerLevel level, ServerPlayer player, BlockPos origin, ItemStack seedStack) {
        BlockState crop = SEED_CROPS.get(seedStack.getItem());
        if (crop == null || !level.getBlockState(origin).is(Blocks.FARMLAND)) return;

        boolean creative = player.isCreative();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);
        queue.addLast(origin);

        int planted = 0;
        while (!queue.isEmpty() && planted < MAX_EXTRA_BLOCKS) {
            // 扫描上限，避免在超大农田上无限扩散拖慢服务端
            if (visited.size() > MAX_FARM_SCAN) break;
            BlockPos current = queue.removeFirst();

            BlockPos above = current.above();
            if (level.isEmptyBlock(above) && (creative || consumeSeed(player, seedStack.getItem()))) {
                level.setBlock(above, crop, 3);
                planted++;
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next) || !level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).is(Blocks.FARMLAND)) queue.addLast(next);
            }
        }
    }

    /** 从玩家背包里扣掉 1 个手持的那种种子，扣不到说明种子用完了。 */
    private static boolean consumeSeed(ServerPlayer player, Item seedItem) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(seedItem)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 能被锄头开垦成耕地的方块。 */
    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL);
    }

    /**
     * 连锁家族判定：同一种方块永远算一族；此外开放同种矿石、石材、树叶和植物家族。
     * 普通矿与深板岩矿先归一到浅层矿，因此两者自然接壤形成混搭矿脉时可以连续扩散。
     */
    private static boolean sameFamily(BlockState target, BlockState candidate) {
        if (candidate.is(target.getBlock())) return true;
        Block targetOre = DEEPSLATE_ORE_BASES.getOrDefault(target.getBlock(), target.getBlock());
        Block candidateOre = DEEPSLATE_ORE_BASES.getOrDefault(candidate.getBlock(), candidate.getBlock());
        if (targetOre == candidateOre) return true;
        if (STONE_FAMILY.contains(target.getBlock())) return STONE_FAMILY.contains(candidate.getBlock());
        if (target.is(BlockTags.LEAVES)) return candidate.is(BlockTags.LEAVES);
        if (isPlant(target)) return isPlant(candidate);
        return false;
    }

    /** 植物类：作物、树苗、花，以及所有 BushBlock（矮草、浆果丛等）。 */
    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || state.getBlock() instanceof BushBlock;
    }

    /** 方块是否可以被连锁处理（客户端预览复用同一判定）。 */
    public static boolean isEligible(BlockState state, Level level, BlockPos pos) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F && !state.hasBlockEntity()
                && level.getBlockEntity(pos) == null;
    }

    /**
     * 统一种模式下的连锁候选方块计算，服务端破坏与客户端白框预览共用，
     * 保证“看到的白框”和“实际会被破坏的方块”完全一致。
     */
    public static List<BlockPos> findCandidates(Level level, Player player, BlockPos origin, BlockState target,
            ChainMiningState.Mode mode) {
        return switch (mode) {
            case VEIN -> veinCandidates(level, origin, target, MAX_EXTRA_BLOCKS);
            case TUNNEL -> tunnelCandidates(level, player, origin, target, MAX_EXTRA_BLOCKS);
            case STAIRS_UP -> stairCandidates(level, player, origin, target, true, MAX_EXTRA_BLOCKS);
            case STAIRS_DOWN -> stairCandidates(level, player, origin, target, false, MAX_EXTRA_BLOCKS);
            // 耕种模式不参与连锁挖掘
            case FARMING -> List.of();
        };
    }

    private static List<BlockPos> veinCandidates(Level level, BlockPos origin, BlockState target, int limit) {
        List<BlockPos> result = new ArrayList<>(Math.min(limit, 256));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && result.size() < limit) {
            BlockPos current = queue.removeFirst();
            for (int x = -1; x <= 1 && result.size() < limit; x++) {
                for (int y = -1; y <= 1 && result.size() < limit; y++) {
                    for (int z = -1; z <= 1 && result.size() < limit; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        BlockPos next = current.offset(x, y, z);
                        if (!visited.add(next) || !level.hasChunkAt(next)) continue;
                        BlockState state = level.getBlockState(next);
                        if (sameFamily(target, state) && isEligible(state, level, next)) {
                            result.add(next);
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<BlockPos> tunnelCandidates(Level level, Player player, BlockPos origin,
            BlockState target, int limit) {
        double x = player.getLookAngle().x;
        double y = player.getLookAngle().y;
        double z = player.getLookAngle().z;
        Direction forward = Direction.getNearest(x, y, z);
        Direction side = forward.getAxis() == Direction.Axis.Y
                ? Direction.fromYRot(player.getYRot()) : forward.getClockWise();
        List<BlockPos> result = new ArrayList<>(Math.min(limit, 256));
        for (int distance = 0; distance < limit && result.size() < limit; distance++) {
            BlockPos base = origin.relative(forward, distance);
            for (int width = 0; width < 1 && result.size() < limit; width++) {
                for (int height = 0; height < 2 && result.size() < limit; height++) {
                    BlockPos pos = base.relative(side, width).above(height);
                    if (pos.equals(origin) || !level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (sameFamily(target, state) && isEligible(state, level, pos)) result.add(pos);
                }
            }
        }
        return result;
    }

    /**
     * 楼梯模式：沿玩家水平朝向逐级斜向延伸，截面 1×2（与通道模式保持一致）。
     * 每一级前进 1 格并上升/下降 1 格，遇到不匹配方块才停止；
     * 旧实现要求每级单格必须严格命中，楼梯通道中稍有缺块就整段不触发。
     */
    private static List<BlockPos> stairCandidates(Level level, Player player, BlockPos origin,
            BlockState target, boolean ascending, int limit) {
        Direction forward = Direction.fromYRot(player.getYRot());
        Direction vertical = ascending ? Direction.UP : Direction.DOWN;
        List<BlockPos> result = new ArrayList<>(Math.min(limit, 256));
        for (int step = 1; step <= limit && result.size() < limit; step++) {
            BlockPos base = origin.relative(forward, step).relative(vertical, step);
            if (!level.hasChunkAt(base)) break;
            boolean matched = false;
            for (int height = 0; height < 2 && result.size() < limit; height++) {
                BlockPos pos = base.above(height);
                BlockState state = level.getBlockState(pos);
                if (sameFamily(target, state) && isEligible(state, level, pos)) {
                    result.add(pos);
                    matched = true;
                }
            }
            if (!matched) break;
        }
        return result;
    }
}
