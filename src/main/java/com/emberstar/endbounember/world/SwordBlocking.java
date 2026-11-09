package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 剑右键格挡：手持剑按住右键进入格挡姿态，正面受到的伤害减半，松开右键结束。
 * <p>姿势、音效都复用原版格挡动画，只是把适用范围从盾牌换成了剑。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SwordBlocking {
    /** 格挡时长：设得足够长，实际由松开右键结束 */
    public static final int USE_DURATION = 72000;
    /** 格挡时正面伤害的倍率 */
    private static final float DAMAGE_MULTIPLIER = 0.5F;

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
        }
    }

    private static boolean isFromBehind(Player player, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) return false;
        Vec3 toAttacker = attacker.position().subtract(player.position());
        if (toAttacker.lengthSqr() < 1.0E-4D) return false;
        return player.getLookAngle().dot(toAttacker.normalize()) < 0.0D;
    }
}
