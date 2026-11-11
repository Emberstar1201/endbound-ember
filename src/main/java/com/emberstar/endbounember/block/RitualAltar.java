package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.DisplayPedestalBlockEntity.RitualType;
import com.emberstar.endbounember.enchantment.DawnEnchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 展示台仪式的统一定义与扫描器。
 *
 * <p>所有仪式都由一份 {@link Definition} 描述（核心物品、环绕槽位、每槽期望物品、底座规则），
 * 由 {@link #find} 统一扫描。新增仪式只需补充一条定义，不再各自手写扫描逻辑。</p>
 *
 * <p>扫描只校验图纸内的槽位，图纸以外的多余展示台与空展示台一律忽略；失败时返回可定位的
 * {@link Issue} 列表，供动作栏提示与粒子高亮使用。</p>
 */
public final class RitualAltar {
    /** 环绕展示台距离中心的最大半径。 */
    public static final int MAX_RADIUS = 3;
    private static final int AMULET_BASE_RADIUS = 3;

    private static final Predicate<ItemStack> CORD =
            stack -> stack.is(EndboundEmber.STARDUST_CORD_BRACELET.get());
    private static final Predicate<ItemStack> VOID_EMBERSTAR =
            stack -> stack.is(EndboundEmber.VOID_EMBERSTAR.get());
    private static final Predicate<ItemStack> EMBERSTARS =
            stack -> stack.is(EndboundEmber.EMBERSTARS);
    private static final Predicate<ItemStack> BLAZE_POWDER =
            stack -> stack.is(Items.BLAZE_POWDER);
    private static final Predicate<ItemStack> NETHER_STAR =
            stack -> stack.is(Items.NETHER_STAR);
    private static final Predicate<ItemStack> DAWN_BOOK = RitualAltar::hasDawnEnchantment;
    private static final Predicate<ItemStack> DAWN_AMULET =
            stack -> stack.is(EndboundEmber.DAWN_AMULET.get());
    private static final Predicate<ItemStack> ENCHANTED_APPLE =
            stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    /** 任意花朵，凋零玫瑰除外 */
    private static final Predicate<ItemStack> ANY_FLOWER =
            stack -> stack.is(ItemTags.FLOWERS) && !stack.is(Items.WITHER_ROSE);
    private static final Predicate<ItemStack> MELON_SLICE =
            stack -> stack.is(Items.GLISTERING_MELON_SLICE);

    /** 底座单元格的判定规则。 */
    @FunctionalInterface
    public interface BaseRule {
        boolean test(Level level, BlockPos pos, BlockState state);
    }

    /** 环绕展示台的排布方式。 */
    public enum SlotMode {
        /** 半径 r 的完整八格环（四角 + 四边中点）。 */
        RING,
        /** 半径 r 的四个斜角。 */
        CORNERS
    }

    /** 一种仪式的完整定义。 */
    public record Definition(
            RitualType type,
            Predicate<ItemStack> core,
            int minRadius,
            int maxRadius,
            SlotMode slotMode,
            Predicate<ItemStack> cornerItem,
            String cornerKey,
            Predicate<ItemStack> edgeItem,
            String edgeKey,
            BaseRule base,
            boolean baseFollowsRadius,
            int baseRadius,
            String baseKey) {
    }

    /** 单个环绕槽位。 */
    public record Slot(int dx, int dz, Predicate<ItemStack> expected, String expectedKey) {
    }

    /** 成功匹配到的祭坛。 */
    public record Match(BlockPos center, Definition definition, int radius, List<BlockPos> pedestals) {
    }

    /** 失败点类型。 */
    public enum IssueKind {
        MISSING_PEDESTAL,
        WRONG_ITEM,
        BAD_BASE
    }

    /** 一处失败点：坐标 + 期望内容。 */
    public record Issue(BlockPos pos, IssueKind kind, String expectedKey) {
    }

    /** 扫描结果：成功时带 {@link Match}，失败时带诊断列表。 */
    public record Result(@Nullable Match match, List<Issue> issues) {
        public boolean ok() {
            return match != null;
        }
    }

    private static final Definition HALO = new Definition(
            RitualType.HALO, CORD, 1, 3, SlotMode.RING,
            VOID_EMBERSTAR, "item.endbound_ember.void_emberstar",
            VOID_EMBERSTAR, "item.endbound_ember.void_emberstar",
            (level, pos, state) -> state.is(Blocks.END_STONE), false, 3, "block.minecraft.end_stone");

    private static final Definition AMULET = new Definition(
            RitualType.AMULET, DAWN_BOOK, 1, 3, SlotMode.RING,
            EMBERSTARS, "item.endbound_ember.emberstar",
            BLAZE_POWDER, "item.minecraft.blaze_powder",
            RitualAltar::isLightFullBase,
            false, 3, "block.endbound_ember.display_pedestal.base_light");

    /**
     * 阿娜希塔·晨曦·莉德尔护符：晨曦护符的上位仪式。
     * 中心放晨曦护符，上下左右放虚无星尘，四个斜角放星尘，底座与晨曦护符仪式相同。
     */
    private static final Definition ANAHITA = new Definition(
            RitualType.ANAHITA, DAWN_AMULET, 1, 3, SlotMode.RING,
            EMBERSTARS, "item.endbound_ember.emberstar",
            VOID_EMBERSTAR, "item.endbound_ember.void_emberstar",
            RitualAltar::isLightFullBase,
            false, 3, "block.endbound_ember.display_pedestal.base_light");

    /** 生命之星：与合成配方同构，四角任意花朵、四边闪烁的西瓜片，底座为可种植的自然方块。 */
    private static final Definition LIFE_STAR = new Definition(
            RitualType.LIFE_STAR, ENCHANTED_APPLE, 1, 3, SlotMode.RING,
            ANY_FLOWER, "block.endbound_ember.display_pedestal.item_any_flower",
            MELON_SLICE, "item.minecraft.glistering_melon_slice",
            (level, pos, state) -> state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND)
                    || state.is(BlockTags.NYLIUM),
            false, 3, "block.endbound_ember.display_pedestal.base_plant");

    private static final Definition HEROBRINE = new Definition(
            RitualType.HEROBRINE, NETHER_STAR, 2, 3, SlotMode.CORNERS,
            NETHER_STAR, "item.minecraft.nether_star",
            NETHER_STAR, "item.minecraft.nether_star",
            (level, pos, state) -> isNecroBaseBlock(state), true, 0,
            "block.endbound_ember.display_pedestal.base_necro");

    private static final List<Definition> DEFINITIONS = List.of(HALO, AMULET, ANAHITA, LIFE_STAR, HEROBRINE);

    private RitualAltar() {
    }

    /** 核心物品是否为仪式核心。 */
    public static boolean isCore(ItemStack stack) {
        return definitionFor(stack) != null;
    }

    @Nullable
    public static Definition definitionFor(ItemStack stack) {
        for (Definition definition : DEFINITIONS) {
            if (definition.core().test(stack)) {
                return definition;
            }
        }
        return null;
    }

    /**
     * 以玩家点击的展示台为线索寻找祭坛：点击位置本身是中心，或属于某个完整祭坛的环绕位时返回成功。
     * 失败时返回最接近完整结构的诊断信息（优先使用点击位置自身的扫描结果）。
     */
    public static Result find(Level level, BlockPos clicked) {
        BlockPos clickedPos = clicked.immutable();
        Result clickedFailure = null;
        Result otherFailure = null;
        for (BlockPos candidate : BlockPos.betweenClosed(
                clicked.offset(-MAX_RADIUS, 0, -MAX_RADIUS), clicked.offset(MAX_RADIUS, 0, MAX_RADIUS))) {
            if (!(level.getBlockEntity(candidate) instanceof DisplayPedestalBlockEntity pedestal)) {
                continue;
            }
            Definition definition = definitionFor(pedestal.getStoredItem());
            if (definition == null) {
                continue;
            }
            BlockPos center = candidate.immutable();
            Result result = check(level, center, definition);
            if (result.ok()) {
                Match match = result.match();
                if (center.equals(clickedPos) || match.pedestals().contains(clickedPos)) {
                    return result;
                }
                continue;
            }
            if (center.equals(clickedPos)) {
                if (clickedFailure == null || result.issues().size() < clickedFailure.issues().size()) {
                    clickedFailure = result;
                }
            } else if (otherFailure == null || result.issues().size() < otherFailure.issues().size()) {
                otherFailure = result;
            }
        }
        Result failure = clickedFailure != null ? clickedFailure : otherFailure;
        return failure != null ? failure : new Result(null, List.of());
    }

    /** 校验以 {@code center} 为中心的祭坛，返回匹配结果或诊断。 */
    public static Result check(Level level, BlockPos center, Definition definition) {
        Result best = null;
        for (int radius = definition.minRadius(); radius <= definition.maxRadius(); radius++) {
            List<Slot> slots = slots(definition, radius);
            List<Issue> issues = new ArrayList<>();
            List<BlockPos> found = new ArrayList<>();
            for (Slot slot : slots) {
                BlockPos pos = center.offset(slot.dx(), 0, slot.dz());
                if (!(level.getBlockState(pos).getBlock() instanceof DisplayPedestalBlock)) {
                    issues.add(new Issue(pos, IssueKind.MISSING_PEDESTAL, slot.expectedKey()));
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof DisplayPedestalBlockEntity pedestal)) {
                    issues.add(new Issue(pos, IssueKind.MISSING_PEDESTAL, slot.expectedKey()));
                    continue;
                }
                if (!slot.expected().test(pedestal.getStoredItem())) {
                    issues.add(new Issue(pos, IssueKind.WRONG_ITEM, slot.expectedKey()));
                    continue;
                }
                found.add(pos);
            }
            issues.addAll(checkBase(level, center, definition, radius));
            if (issues.isEmpty()) {
                return new Result(new Match(center.immutable(), definition, radius, List.copyOf(found)), List.of());
            }
            if (best == null || issues.size() < best.issues().size()) {
                best = new Result(null, List.copyOf(issues));
            }
        }
        return best != null ? best : new Result(null, List.of());
    }

    /** 生成某个半径下的全部槽位。 */
    private static List<Slot> slots(Definition definition, int radius) {
        List<Slot> slots = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean corner = dx != 0 && dz != 0;
                boolean onRing = Math.max(Math.abs(dx), Math.abs(dz)) == radius;
                // 只取四角与四边中点，半径 r 的环上其余空位不要求摆展示台
                boolean onCross = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
                if (definition.slotMode() == SlotMode.CORNERS) {
                    if (!corner || Math.abs(dx) != radius) {
                        continue;
                    }
                    slots.add(new Slot(dx, dz, definition.cornerItem(), definition.cornerKey()));
                } else if (onRing && onCross) {
                    slots.add(corner
                            ? new Slot(dx, dz, definition.cornerItem(), definition.cornerKey())
                            : new Slot(dx, dz, definition.edgeItem(), definition.edgeKey()));
                }
            }
        }
        return slots;
    }

    /** 校验底座层：底座半径固定时用定义值，跟随环半径时用当前半径。 */
    private static List<Issue> checkBase(Level level, BlockPos center, Definition definition, int radius) {
        int baseRadius = definition.baseFollowsRadius() ? radius : definition.baseRadius();
        if (definition == AMULET || definition == ANAHITA) {
            baseRadius = AMULET_BASE_RADIUS;
        }
        BlockPos baseCenter = center.below();
        List<Issue> issues = new ArrayList<>();
        for (BlockPos base : BlockPos.betweenClosed(
                baseCenter.offset(-baseRadius, 0, -baseRadius), baseCenter.offset(baseRadius, 0, baseRadius))) {
            if (!definition.base().test(level, base, level.getBlockState(base))) {
                issues.add(new Issue(base.immutable(), IssueKind.BAD_BASE, definition.baseKey()));
            }
        }
        return issues;
    }

    /** 发光完整方块：任意发光且碰撞箱完整的方块，红石灯除外。 */
    private static boolean isLightFullBase(Level level, BlockPos pos, BlockState state) {
        return state.getLightEmission(level, pos) > 0
                && state.isCollisionShapeFullBlock(level, pos)
                && !state.is(Blocks.REDSTONE_LAMP);
    }

    /** 下界 / 亡灵风格的底座方块白名单（含金块：金在下界象征猪灵供奉）。 */
    private static boolean isNecroBaseBlock(BlockState state) {
        return state.is(Blocks.GLOWSTONE) || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK)
                || state.is(Blocks.NETHERRACK) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.BLACKSTONE) || state.is(Blocks.POLISHED_BLACKSTONE)
                || state.is(Blocks.BASALT) || state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.WARPED_NYLIUM) || state.is(Blocks.NETHER_BRICKS)
                || state.is(Blocks.RED_NETHER_BRICKS) || state.is(Blocks.GOLD_BLOCK);
    }

    /** 附魔书是否带有拂晓附魔。 */
    private static boolean hasDawnEnchantment(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) {
            return false;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(DawnEnchantment.KEY)) {
                return true;
            }
        }
        return false;
    }

    /** 把第一处失败点提示到动作栏，并在该坐标播报粒子。 */
    public static void report(Player player, Level level, Result result) {
        if (result.issues().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("block.endbound_ember.display_pedestal.altar_incomplete"), true);
            return;
        }
        Issue issue = result.issues().get(0);
        BlockPos pos = issue.pos();
        Component expected = Component.translatable(issue.expectedKey());
        Component message = switch (issue.kind()) {
            case MISSING_PEDESTAL -> Component.translatable(
                    "block.endbound_ember.display_pedestal.issue_missing_pedestal",
                    pos.getX(), pos.getY(), pos.getZ());
            case WRONG_ITEM -> Component.translatable(
                    "block.endbound_ember.display_pedestal.issue_wrong_item",
                    pos.getX(), pos.getY(), pos.getZ(), expected);
            case BAD_BASE -> Component.translatable(
                    "block.endbound_ember.display_pedestal.issue_bad_base",
                    pos.getX(), pos.getY(), pos.getZ(), expected);
        };
        player.displayClientMessage(message, true);
        EndboundEmber.LOGGER.warn("[祭坛] 结构不完整：{} @ {}", issue.kind(), pos);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    10, 0.15D, 0.15D, 0.15D, 0.01D);
        }
    }
}
