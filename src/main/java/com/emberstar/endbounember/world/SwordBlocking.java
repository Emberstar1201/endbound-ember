package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import com.emberstar.endbounember.network.SwordParryPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 剑右键格挡：手持剑按住右键进入格挡姿态，正面受到的伤害降低 60%，松开右键结束。
 * <p>姿势、音效都复用原版格挡动画，只是把适用范围从盾牌换成了剑。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SwordBlocking {
    /** 格挡时长：设得足够长，实际由松开右键结束 */
    public static final int USE_DURATION = 72000;
    /** 开始格挡后的完美格挡窗口（tick） */
    public static final int PARRY_WINDOW_TICKS = 10;
    /** 格挡时正面伤害的倍率：原版减伤 50% 的基础上再减 10% */
    private static final float DAMAGE_MULTIPLIER = 0.4F;

    private SwordBlocking() {
    }

    /** 判断这个物品能不能当盾牌举着，剑类物品统一走剑标签。 */
    public static boolean isBlockingSword(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!Config.SWORD_BLOCKING_ENABLED.get()) return;
        Player player = event.getEntity();
        if (player.isSpectator() || player.isUsingItem()) return;
        // 副手拿着盾牌时优先举盾，不抢原版盾牌
        if (player.getOffhandItem().getItem() instanceof ShieldItem) return;
        if (!isBlockingSword(event.getItemStack())) return;
        player.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!Config.SWORD_BLOCKING_ENABLED.get()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isUsingItem() || !isBlockingSword(player.getUseItem())) return;
        // 无视盾牌的伤害（虚空、/kill 等）照旧打满
        if (event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
        // 背后来的伤害挡不住
        if (isFromBehind(player, event.getSource())) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        event.setAmount(amount * DAMAGE_MULTIPLIER);
        if (!player.level().isClientSide) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F,
                    0.9F + player.level().random.nextFloat() * 0.2F);
            // 完美格挡：开场窗口内挡下正面伤害，通知客户端把格挡条回满
            if (player.getTicksUsingItem() < PARRY_WINDOW_TICKS
                    && player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new SwordParryPayload());
            }
        }
    }

    /** 剑格挡把从正面飞来的箭类弹射物弹回去，反弹后的箭归属格挡者。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!Config.SWORD_BLOCKING_ENABLED.get()) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (arrow.level().isClientSide()) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof Player player)) return;
        if (!player.isUsingItem() || !isBlockingSword(player.getUseItem())) return;
        if (arrow.getOwner() == player) return;

        Vec3 motion = arrow.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6D) return;
        Vec3 incoming = motion.normalize();
        // 只有迎着视线飞来的弹射物才会被弹开，背后来的照常命中
        if (player.getLookAngle().dot(incoming) > 0.0D) return;

        Vec3 direction = incoming.scale(-1.0D);
        Vec3 start = player.getEyePosition().add(direction.scale(0.8D));
        float speed = (float) Math.max(motion.length(), 1.0D);

        event.setCanceled(true);
        arrow.setOwner(player);
        arrow.moveTo(start.x, start.y, start.z);
        arrow.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        arrow.setDeltaMovement(direction.scale(speed));
        arrow.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    private static boolean isFromBehind(Player player, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) return false;
        Vec3 toAttacker = attacker.position().subtract(player.position());
        if (toAttacker.lengthSqr() < 1.0E-4D) return false;
        return player.getLookAngle().dot(toAttacker.normalize()) < 0.0D;
    }
}
