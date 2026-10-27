package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 连锁挖掘：镐 / 斧 / 锹专属，最高 III 级。
 * 按住 ~ 键开启连锁模式后，挖掘方块会一并破坏范围内的同种方块；与无中生有互斥。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ChainBreakerEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chain_breaker"));

    /** 当前处于连锁模式的玩家。 */
    private static final Set<UUID> CHAIN_MODE_PLAYERS = ConcurrentHashMap.newKeySet();
    /** 每个额外方块的耐久消耗。 */
    private static final double DURABILITY_PER_BLOCK = 0.5D;
    /** 单次连锁的最大耐久消耗。 */
    private static final int MAX_DURABILITY_COST = 10;
    private static final int TOOLTIP_COLOR = 0x9BD7A6;

    private ChainBreakerEnchantment() {
    }

    /** 读取物品上的连锁挖掘附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 开关连锁模式，由客户端按键包调用。 */
    public static void setChainMode(UUID uuid, boolean active) {
        if (active) {
            CHAIN_MODE_PLAYERS.add(uuid);
        } else {
            CHAIN_MODE_PLAYERS.remove(uuid);
        }
    }

    public static boolean isChainMode(UUID uuid) {
        return CHAIN_MODE_PLAYERS.contains(uuid);
    }

    /** 玩家登出时清理连锁模式标记。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CHAIN_MODE_PLAYERS.remove(event.getEntity().getUUID());
    }

    /** 连锁破坏范围内的同种方块，掉落走原版 playerDestroy 逻辑。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;

        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!isChainMode(player.getUUID())) return;

        ItemStack tool = player.getMainHandItem();
        int level = levelOf(tool);
        if (level <= 0) return;

        int radius = radiusOf(level);
        if (radius <= 0) return;

        BlockPos origin = event.getPos();
        BlockState targetState = event.getState();
        Block targetBlock = targetState.getBlock();
        if (targetState.isAir()) return;

        BlockPos minPos = origin.offset(-radius, -radius, -radius);
        BlockPos maxPos = origin.offset(radius, radius, radius);
        int broken = 0;

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            // 中心方块由原版破坏流程处理
            if (pos.equals(origin)) continue;

            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir() || state.getBlock() != targetBlock) continue;
            // destroySpeed < 0 表示不可破坏
            if (state.getDestroySpeed(serverLevel, pos) < 0.0F) continue;

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
            targetBlock.playerDestroy(serverLevel, player, pos, state, blockEntity, tool);
            serverLevel.removeBlock(pos, false);
            serverLevel.levelEvent(2001, pos, Block.getId(state));
            broken++;
        }

        if (broken > 0 && !player.isCreative()) {
            int cost = Math.max(1, (int) Math.ceil(broken * DURABILITY_PER_BLOCK));
            cost = Math.min(cost, MAX_DURABILITY_COST);
            tool.hurtAndBreak(cost, player, EquipmentSlot.MAINHAND);
        }
    }

    /** 连锁半径：I 级 3×3×3，II 级 5×5×5，III 级 9×9×9。 */
    public static int radiusOf(int level) {
        if (level >= 3) return 4;
        if (level == 2) return 2;
        return level >= 1 ? 1 : 0;
    }

    /** 物品提示：在附魔名称之后追加效果说明与连锁范围。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = levelOf(event.getItemStack());
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.chain_breaker").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.chain_breaker.desc"));
        int side = radiusOf(level) * 2 + 1;
        tooltip.add(insertAt + 1, line("endbound_ember.chain_breaker.radius", side, side, side));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
