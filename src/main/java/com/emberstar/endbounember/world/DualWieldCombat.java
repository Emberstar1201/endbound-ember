package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.DualWieldCooldownPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "endbound_ember")
public final class DualWieldCombat {
    private static final Map<UUID, Long> LAST_OFFHAND_ATTACK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_END = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_TOTAL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_COOLDOWN_SYNC = new ConcurrentHashMap<>();

    private DualWieldCombat() {
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_OFFHAND_ATTACK.remove(event.getEntity().getUUID());
        COOLDOWN_END.remove(event.getEntity().getUUID());
        COOLDOWN_TOTAL.remove(event.getEntity().getUUID());
        LAST_COOLDOWN_SYNC.remove(event.getEntity().getUUID());
    }

    public static void attackOffhand(ServerPlayer player) {
        ItemStack weapon = player.getOffhandItem();
        if (!hasMatchingDualWeapon(player) || !hasAttackDamage(weapon)) return;

        long now = player.level().getGameTime();
        long cooldown = cooldownTicks(player, weapon);
        long lastAttack = LAST_OFFHAND_ATTACK.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - lastAttack < cooldown) return;

        LivingEntity target = getTarget(player);
        if (target != null && !player.canAttack(target)) target = null;

        LAST_OFFHAND_ATTACK.put(player.getUUID(), now);
        LAST_COOLDOWN_SYNC.put(player.getUUID(), now);
        COOLDOWN_END.put(player.getUUID(), now + cooldown);
        COOLDOWN_TOTAL.put(player.getUUID(), (int) cooldown);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new DualWieldCooldownPayload((int) cooldown, (int) cooldown));
        player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
        if (target != null) attack(player, target, weapon);
    }

    public static boolean hasMatchingDualWeapon(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return !mainHand.isEmpty() && !offHand.isEmpty()
                && mainHand.is(offHand.getItem())
                && hasAttackDamage(mainHand) && hasAttackDamage(offHand);
    }

    private static LivingEntity getTarget(ServerPlayer player) {
        double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(range));
        AABB search = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, end, search,
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
                        && !(entity instanceof Player targetPlayer && !player.canHarmPlayer(targetPlayer)),
                range * range);
        if (entityHit == null) return null;

        HitResult blockHit = player.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS
                && start.distanceToSqr(blockHit.getLocation()) < start.distanceToSqr(entityHit.getLocation())) {
            return null;
        }
        return (LivingEntity) entityHit.getEntity();
    }

    private static boolean hasAttackDamage(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream()
                .anyMatch(modifier -> modifier.attribute().equals(Attributes.ATTACK_DAMAGE));
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long end = COOLDOWN_END.get(player.getUUID());
        if (end == null) return;
        int remaining = (int) Math.max(0L, end - player.serverLevel().getGameTime());
        long now = player.serverLevel().getGameTime();
        long lastSync = LAST_COOLDOWN_SYNC.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if ((remaining > 0 && now - lastSync >= 2L) || (remaining == 0 && lastSync < now)) {
            int total = COOLDOWN_TOTAL.getOrDefault(player.getUUID(), 0);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, new DualWieldCooldownPayload(remaining, total));
            LAST_COOLDOWN_SYNC.put(player.getUUID(), now);
        }
        if (remaining == 0) {
            COOLDOWN_END.remove(player.getUUID());
            COOLDOWN_TOTAL.remove(player.getUUID());
            LAST_COOLDOWN_SYNC.remove(player.getUUID());
        }
    }

    /**
     * 副手冷却与原版主手完全一致：直接取玩家当前的攻击速度属性（主副手为同一物品，数值相同），
     * 这样冷却就等于玩家在主手使用同一把武器时的攻击间隔。
     */
    private static int cooldownTicks(ServerPlayer player, ItemStack weapon) {
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (speed <= 0.0D) {
            speed = weapon.getAttributeModifiers().compute(4.0D, EquipmentSlot.MAINHAND);
        }
        int ticks = Math.max(1, (int) Math.ceil(20.0D / Math.max(0.1D, speed)));
        com.emberstar.endbounember.EndboundEmber.LOGGER.info(
                "[DualWield] 副手冷却：武器={} 当前攻速={} 冷却={}tick({}ms)",
                weapon.getItem(), speed, ticks, ticks * 50);
        return ticks;
    }

    private static void attack(ServerPlayer player, LivingEntity target, ItemStack weapon) {
        // 与主手攻击使用同一套伤害属性，避免因 OFFHAND 槽位漏掉武器本身的伤害修饰符。
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) Math.max(1.0D, attackDamage);
        DamageSource source = player.damageSources().playerAttack(player);
        // 补上原版主手攻击必经的附魔伤害结算，否则副手这一刀锋利/亡灵杀手等附魔完全不生效。
        damage = EnchantmentHelper.modifyDamage(player.serverLevel(), weapon, target, source, damage);
        if (!target.hurt(source, damage)) return;

        target.knockback(0.4F, player.getX() - target.getX(), player.getZ() - target.getZ());
        weapon.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        player.resetLastActionTime();
    }
}
