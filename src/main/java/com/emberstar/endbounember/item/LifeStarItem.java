package com.emberstar.endbounember.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.DrownedGirl;
import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.entity.ZombieGirl;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class LifeStarItem extends Item {
    private static final int STAGE1_MIN_TICKS = 1200;
    private static final int STAGE1_RANDOM_TICKS = 1200;
    private static final int STAGE2_TICKS = 200;
    private static final String HEALING_UNTIL = "EndboundEmberHealingUntil";
    private static final Map<UUID, Long> HEALING = new HashMap<>();

    public LifeStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ZombieGirl) || target instanceof HumanGirl || player.level().isClientSide
                || HEALING.containsKey(target.getUUID())
                || target.getPersistentData().contains(HEALING_UNTIL)) {
            return InteractionResult.PASS;
        }
        long start = target.level().getGameTime();
        long until = start + STAGE1_MIN_TICKS
                + target.getRandom().nextInt(STAGE1_RANDOM_TICKS + 1) + STAGE2_TICKS;
        HEALING.put(target.getUUID(), until);
        target.getPersistentData().putLong(HEALING_UNTIL, until);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                target.getSoundSource(), 1.0F, 1.0F);
        if (!player.getAbilities().instabuild) player.getCooldowns().addCooldown(this, 2400);
        return InteractionResult.SUCCESS;
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof ZombieGirl girl) || entity instanceof HumanGirl) continue;
                    Long until = HEALING.get(girl.getUUID());
                    if (until == null) {
                        long savedUntil = girl.getPersistentData().getLong(HEALING_UNTIL);
                        if (savedUntil <= 0L) continue;
                        until = savedUntil;
                        HEALING.put(girl.getUUID(), until);
                    }
                    long remaining = until - level.getGameTime();
                    if (remaining <= 0L) {
                        HEALING.remove(girl.getUUID());
                        girl.getPersistentData().remove(HEALING_UNTIL);
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, girl.getX(), girl.getY(0.5D), girl.getZ(),
                                1, 0.0D, 0.0D, 0.0D, 0.0D);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, girl.getX(), girl.getY(0.5D), girl.getZ(),
                                60, 0.6D, 0.9D, 0.6D, 0.01D);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, girl.getX(), girl.getY(0.5D), girl.getZ(),
                                24, 0.5D, 0.8D, 0.5D, 0.0D);
                        level.playSound(null, girl.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                                girl.getSoundSource(), 1.0F, 1.0F);
                        replaceWithHuman(level, girl);
                    } else if (remaining <= STAGE2_TICKS) {
                        if (girl.tickCount % 3 == 0) {
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, girl.getRandomX(1.0D), girl.getRandomY(), girl.getRandomZ(1.0D),
                                    4, 0.3D, 0.5D, 0.3D, 0.0D);
                        }
                        if (girl.tickCount % 10 == 0) {
                            level.sendParticles(ParticleTypes.INSTANT_EFFECT, girl.getX(), girl.getY(0.5D), girl.getZ(),
                                    2, 0.4D, 0.6D, 0.4D, 0.0D);
                        }
                    } else if (girl.tickCount % 20 == 0) {
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, girl.getRandomX(1.0D), girl.getRandomY(), girl.getRandomZ(1.0D),
                                3, 0.3D, 0.5D, 0.3D, 0.0D);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof HumanGirl human) || human.level().isClientSide
                    || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                    || !attacker.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                    || human.getHealth() - event.getAmount() > human.getMaxHealth() * 0.5F) return;
            ServerLevel level = (ServerLevel) human.level();
            replaceWithUndead(level, human);
        }

        private static void replaceWithHuman(ServerLevel level, ZombieGirl old) {
            HumanGirl replacement = ModEntities.HUMAN_GIRL.get().create(level);
            if (replacement == null) return;
            copyCommon(old, replacement);
            replacement.setDrownedSource(old instanceof DrownedGirl);
            level.addFreshEntity(replacement);
            old.discard();
        }

        private static void replaceWithUndead(ServerLevel level, HumanGirl old) {
            ZombieGirl replacement = (old.isDrownedSource() ? ModEntities.DROWNED_GIRL.get() : ModEntities.ZOMBIE_GIRL.get()).create(level);
            if (replacement == null) return;
            copyCommon(old, replacement);
            level.addFreshEntity(replacement);
            old.discard();
        }

        private static void copyCommon(ZombieGirl old, ZombieGirl replacement) {
            replacement.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
            replacement.setOwnerUUID(old.getOwnerUUID());
            replacement.setSkinVariant(old.getSkinVariant());
            replacement.setHealth(Math.min(old.getHealth(), replacement.getMaxHealth()));
            for (EquipmentSlot slot : EquipmentSlot.values()) replacement.setItemSlot(slot, old.getItemBySlot(slot).copy());
            for (int i = 0; i < old.getCompanionInventorySize(); i++) replacement.setCompanionStack(i, old.getCompanionStack(i).copy());
        }
    }
}
