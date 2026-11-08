package com.emberstar.endbounember.enchantment;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 蓄积：剑/斧/三叉戟专属，最高 III 级。
 * 长按右键蓄力，每 5 秒积累一阶（上限为附魔等级），蓄满后下一次近战攻击造成额外伤害与击退。
 * 切换主手物品、受到伤害或完成一次攻击都会清空蓄力。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AccumulateEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "accumulate"));

    /** 每阶所需 tick（5 秒）。 */
    private static final int TICKS_PER_STAGE = 100;
    /** 每阶额外伤害比例。 */
    private static final double DAMAGE_PER_STAGE = 0.5D;
    /** 每阶击退强度。 */
    private static final double KNOCKBACK_PER_STAGE = 0.4D;

    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();

    private AccumulateEnchantment() {
    }

    private static final class Charge {
        private final ItemStack stack;
        private final long startTick;
        private final int level;

        private Charge(ItemStack stack, long startTick, int level) {
            this.stack = stack;
            this.startTick = startTick;
            this.level = level;
        }
    }

    /** 读取物品上的蓄积等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 右键开始蓄力；已处于蓄力状态时不重置。 */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        int level = levelOf(stack);
        if (level <= 0) return;
        if (CHARGES.containsKey(player.getUUID())) return;

        CHARGES.put(player.getUUID(), new Charge(stack.copy(), player.level().getGameTime(), level));
    }

    /** 维护蓄力状态：切换物品或受伤时中断。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        Charge charge = CHARGES.get(player.getUUID());
        if (charge == null) return;

        boolean interrupted = player.hurtTime > 0
                || !ItemStack.isSameItemSameComponents(charge.stack, player.getMainHandItem())
                || levelOf(player.getMainHandItem()) <= 0;
        if (interrupted) CHARGES.remove(player.getUUID());
    }

    /** 近战命中时按已积累阶级结算额外伤害与击退。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        Charge charge = CHARGES.get(player.getUUID());
        if (charge == null) return;

        int stages = stages(charge, player);
        CHARGES.remove(player.getUUID());
        if (stages <= 0) return;

        event.setAmount(event.getAmount() * (float) (1.0D + DAMAGE_PER_STAGE * stages));

        LivingEntity target = event.getEntity();
        Vec3 look = player.getLookAngle();
        target.knockback(KNOCKBACK_PER_STAGE * stages, look.x, look.z);
    }

    /** 当前已完成的蓄力阶级数。 */
    private static int stages(Charge charge, Player player) {
        long elapsed = player.level().getGameTime() - charge.startTick;
        long stages = elapsed / TICKS_PER_STAGE;
        return (int) Math.min(charge.level, Math.max(0L, stages));
    }
}
