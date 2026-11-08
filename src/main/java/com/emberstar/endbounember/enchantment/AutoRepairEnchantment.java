package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 自动修复：所有可损耗耐久的物品，最高 III 级。
 * 装备中的附魔物品每 100 tick（5 秒）自动恢复一次耐久。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AutoRepairEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "auto_repair"));

    /** 对应旧版配置 autoRepair.intervalTicks（默认 100）。 */
    private static final int INTERVAL_TICKS = 100;
    /** 对应旧版配置 autoRepair.level1MinAmount（默认 1）。 */
    private static final int LEVEL1_MIN = 1;
    /** 对应旧版配置 autoRepair.level1MaxAmount（默认 3）。 */
    private static final int LEVEL1_MAX = 3;
    /** 对应旧版配置 autoRepair.level2MinAmount（默认 5）。 */
    private static final int LEVEL2_MIN = 5;
    /** 对应旧版配置 autoRepair.level2MaxAmount（默认 7）。 */
    private static final int LEVEL2_MAX = 7;
    /** 对应旧版配置 autoRepair.level3MinAmount（默认 10）。 */
    private static final int LEVEL3_MIN = 10;
    /** 对应旧版配置 autoRepair.level3MaxAmount（默认 12）。 */
    private static final int LEVEL3_MAX = 12;

    /** 旧版 AutoRepairHandler 遍历的槽位：主手、副手与四个护甲槽。 */
    private static final EquipmentSlot[] REPAIR_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final Map<UUID, Map<EquipmentSlot, Integer>> TIMERS = new HashMap<>();

    private AutoRepairEnchantment() {
    }

    /** 读取物品上的自动修复等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 按等级随机取本次修复的耐久点数。 */
    private static int repairAmount(int level, RandomSource random) {
        return switch (level) {
            case 1 -> LEVEL1_MIN + random.nextInt(LEVEL1_MAX - LEVEL1_MIN + 1);
            case 2 -> LEVEL2_MIN + random.nextInt(LEVEL2_MAX - LEVEL2_MIN + 1);
            default -> LEVEL3_MIN + random.nextInt(LEVEL3_MAX - LEVEL3_MIN + 1);
        };
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();
        Map<EquipmentSlot, Integer> timers = TIMERS.computeIfAbsent(uuid, ignored -> new HashMap<>());
        List<EquipmentSlot> activeSlots = new ArrayList<>(REPAIR_SLOTS.length);

        for (EquipmentSlot slot : REPAIR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamaged()) continue;
            int level = levelOf(stack);
            if (level <= 0) continue;

            activeSlots.add(slot);
            int ticks = timers.getOrDefault(slot, 0) + 1;
            if (ticks >= INTERVAL_TICKS) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - repairAmount(level, player.getRandom())));
                ticks = 0;
            }
            timers.put(slot, ticks);
        }

        // 清理已经卸下/满耐久的物品计时器，与旧版定期清理离线玩家数据的行为一致。
        if (player.tickCount % 200 == 0) {
            timers.keySet().removeIf(slot -> !activeSlots.contains(slot));
        }
    }
}
