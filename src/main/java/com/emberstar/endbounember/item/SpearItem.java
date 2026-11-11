package com.emberstar.endbounember.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpearItem extends Item {
    private static final int MAX_USE_TICKS = 72000;
    private static final int MIN_CHARGE_TICKS = 8;
    private static final int COOLDOWN_TICKS = 10;
    private static final double HIT_RANGE = 3.0;
    private static final double HIT_MARGIN = 0.45;
    private final boolean wooden;

    public SpearItem(Properties properties) {
        this(properties, false);
    }

    public SpearItem(Properties properties, boolean wooden) {
        super(properties);
        this.wooden = wooden;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    wooden ? EndboundEmber.SPEAR_WOOD_USE.get() : EndboundEmber.SPEAR_USE.get(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;
        int chargedTicks = MAX_USE_TICKS - timeLeft;
        if (chargedTicks < MIN_CHARGE_TICKS || player.getCooldowns().isOnCooldown(this)) return;

        Vec3 look = player.getLookAngle();
        Vec3 movement = player.getDeltaMovement().scale(20.0);
        double speed = look.dot(movement);
        if (speed < 2.0 || !player.onGround() && !player.isFallFlying()) return;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                wooden ? EndboundEmber.SPEAR_WOOD_ATTACK.get() : EndboundEmber.SPEAR_ATTACK.get(),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        Set<UUID> hitEntities = new HashSet<>();
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(HIT_RANGE)).inflate(HIT_MARGIN);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != player && entity.isAlive() && player.canAttack(entity))) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            double distance = toTarget.length();
            if (distance > HIT_RANGE + target.getBbWidth() * 0.5 || distance < 1.0e-5) continue;
            if (look.dot(toTarget.normalize()) < 0.72) continue;
            if (!hitEntities.add(target.getUUID())) continue;

            double relativeSpeed = Math.max(0.0, speed - look.dot(target.getDeltaMovement().scale(20.0)));
            float damage = (float) (player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    + Math.floor(relativeSpeed * 0.75));
            boolean hurt = target.hurt(player.damageSources().playerAttack(player), damage);
            if (hurt) {
                Vec3 knockback = look.scale(Math.min(1.6, 0.4 + relativeSpeed * 0.04));
                target.push(knockback.x, Math.max(0.15, knockback.y + 0.12), knockback.z);
                target.hurtMarked = true;
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        wooden ? EndboundEmber.SPEAR_WOOD_HIT.get() : EndboundEmber.SPEAR_HIT.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), EndboundEmber.SPEAR_LUNGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
