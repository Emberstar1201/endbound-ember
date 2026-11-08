package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.EnchantmentPreviewPayload;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 把附魔台三个档位真正会给出的附魔提前算好并同步给客户端。
 *
 * <p>算法与原版 {@code EnchantmentMenu#getEnchantmentList} 完全一致：
 * 用 {@code enchantmentSeed + 档位} 作为随机种子，再走
 * {@link EnchantmentHelper#selectEnchantment}；书本额外剔除一个附魔的步骤也照搬。
 * 因为消耗等级、种子和物品都在同一刻从菜单快照读取，
 * 所以显示结果与玩家点击后实际获得的附魔一致。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EnchantmentPreviewEvents {
    /** 每个玩家的上一次快照，用于避免每 tick 重复发包 */
    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();

    private EnchantmentPreviewEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID playerId = player.getUUID();
        boolean enabled = Config.BETTER_LUCK_ENCHANTMENT.get();

        if (!enabled || !(player.containerMenu instanceof EnchantmentMenu menu)
                || !EnchantingTableRules.canUse(menu.getSlot(0).getItem())) {
            // 离开附魔台或物品被拿走时清掉快照，并通知客户端停止显示。
            Snapshot previous = SNAPSHOTS.remove(playerId);
            if (previous != null) {
                EnchantmentPreviewPayload.send(player, EnchantmentPreviewPayload.empty(previous.containerId));
            }
            return;
        }

        ItemStack stack = menu.getSlot(0).getItem();
        Snapshot current = new Snapshot(menu.containerId, stack.copy(), menu.costs.clone(), menu.getEnchantmentSeed(),
                Config.ENCHANTING_TABLE_MAX_ENCHANTMENTS.get());
        Snapshot previous = SNAPSHOTS.get(playerId);
        if (previous != null && previous.sameAs(current)) return;

        SNAPSHOTS.put(playerId, current);
        EnchantmentPreviewPayload.send(player, new EnchantmentPreviewPayload(
                menu.containerId, computeRows(player, stack, menu.costs, menu.getEnchantmentSeed())));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SNAPSHOTS.remove(event.getEntity().getUUID());
    }

    /** 复刻原版 {@code getEnchantmentList} 的计算过程，得到三档的完整附魔列表 */
    private static List<List<EnchantmentPreviewPayload.Entry>> computeRows(
            ServerPlayer player, ItemStack stack, int[] costs, int seed) {
        RegistryAccess registryAccess = player.level().registryAccess();
        Optional<HolderSet.Named<Enchantment>> tag = registryAccess.registryOrThrow(Registries.ENCHANTMENT)
                .getTag(EnchantmentTags.IN_ENCHANTING_TABLE);

        List<List<EnchantmentPreviewPayload.Entry>> rows = new ArrayList<>(3);
        for (int row = 0; row < 3; row++) {
            if (tag.isEmpty() || costs[row] <= 0) {
                rows.add(List.of());
                continue;
            }

            // 与原版一致：先按 int 相加再作为随机种子
            RandomSource random = RandomSource.create((long) (seed + row));
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(
                    random, stack.copy(), costs[row], tag.get().stream());
            // 书本只保留其中一个附魔，这里同样消耗一次随机数
            if (stack.is(Items.BOOK) && list.size() > 1) {
                list.remove(random.nextInt(list.size()));
            }
            list = EnchantingTableRules.limitCandidates(stack, list);

            List<EnchantmentPreviewPayload.Entry> entries = new ArrayList<>(list.size());
            for (EnchantmentInstance instance : list) {
                instance.enchantment.unwrapKey().ifPresent(key ->
                        entries.add(new EnchantmentPreviewPayload.Entry(key.location(), instance.level)));
            }
            rows.add(List.copyOf(entries));
        }
        return List.copyOf(rows);
    }

    /** 一次菜单状态快照，变化时才重新计算并发包 */
    private record Snapshot(int containerId, ItemStack stack, int[] costs, int seed, int maxEnchantments) {
        boolean sameAs(Snapshot other) {
            return containerId == other.containerId
                    && seed == other.seed
                    && maxEnchantments == other.maxEnchantments
                    && Arrays.equals(costs, other.costs)
                    && ItemStack.matches(stack, other.stack);
        }
    }
}
