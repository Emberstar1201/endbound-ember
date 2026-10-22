package com.emberstar.endbounember.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class LifeStarItem extends Item {
    private static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "life_star_health");
    private static final String BLOOM_COOLDOWN = "EndboundEmberLifeStarBloomCooldown";
    private static final String REVIVAL_COOLDOWN = "EndboundEmberLifeStarRevivalCooldown";
    private static final long BLOOM_COOLDOWN_TICKS = 3600L;
    private static final long REVIVAL_COOLDOWN_TICKS = 12000L;
    private static final int STAGE1_MIN_TICKS = 400;
    private static final int STAGE1_RANDOM_TICKS = 400;
    private static final int STAGE2_TICKS = 100;
    /** 治愈过程结束后的使用冷却，避免立刻再次右键触发同一段流程。 */
    private static final int USE_COOLDOWN_TICKS = 2400;
    private static final String HEALING_UNTIL = "EndboundEmberHealingUntil";
    private static final Map<UUID, Long> HEALING = new HashMap<>();

    public LifeStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(0x9EE5A2));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ZombieGirl) || target instanceof HumanGirl) return InteractionResult.PASS;
        // 客户端直接返回 SUCCESS：让原版把这次右键判定为已使用物品，
        // 否则会继续按左键逻辑攻击少女、或转而打开少女的背包界面。
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (HEALING.containsKey(target.getUUID())
                || target.getPersistentData().contains(HEALING_UNTIL)) {
            return InteractionResult.SUCCESS;
        }
        long start = target.level().getGameTime();
        long until = start + STAGE1_MIN_TICKS
                + target.getRandom().nextInt(STAGE1_RANDOM_TICKS + 1) + STAGE2_TICKS;
        HEALING.put(target.getUUID(), until);
        target.getPersistentData().putLong(HEALING_UNTIL, until);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                target.getSoundSource(), 1.0F, 1.0F);
        if (!player.getAbilities().instabuild) {
            player.getCooldowns().addCooldown(this, (int) (until - start) + USE_COOLDOWN_TICKS);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.life_star.condition").withStyle(style -> style.withColor(0x9EE5A2)));
        tooltip.add(Component.translatable("item.endbound_ember.life_star.breath").withStyle(style -> style.withColor(0x9EE5A2)));
        tooltip.add(Component.translatable("item.endbound_ember.life_star.morning_dew").withStyle(style -> style.withColor(0x9EE5A2)));
        tooltip.add(Component.translatable("item.endbound_ember.life_star.revival").withStyle(style -> style.withColor(0x9EE5A2)));
        tooltip.add(Component.translatable("item.endbound_ember.life_star.camellia").withStyle(style -> style.withColor(0x9EE5A2)));
        tooltip.add(Component.translatable("item.endbound_ember.life_star.rain_dew").withStyle(style -> style.withColor(0x9EE5A2)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        @SubscribeEvent
        public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            boolean active = hasLifeStar(player);
            updateMaxHealth(player, active);
            if (!active) return;
            if (player.tickCount % 8 == 0 && player.getHealth() < player.getMaxHealth()) player.heal(1.0F);
            player.getFoodData().setExhaustion(0.0F);
            if (player.getHealth() < player.getMaxHealth() * 0.5F
                    && player.getPersistentData().getLong(BLOOM_COOLDOWN) <= player.level().getGameTime()) {
                player.heal(player.getMaxHealth() * 0.33F);
                player.getPersistentData().putLong(
                        BLOOM_COOLDOWN,
                        player.level().getGameTime() + BLOOM_COOLDOWN_TICKS);
            }
            if (player.level().isRainingAt(player.blockPosition()) && player.tickCount % 60 == 0) player.heal(1.5F);
        }

        @SubscribeEvent
        public static void onLivingTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof TamableAnimal animal)
                    || !animal.isTame()
                    || animal.level().isClientSide
                    || !(animal.getOwner() instanceof Player owner)
                    || !hasLifeStar(owner)
                    || !animal.level().isRainingAt(animal.blockPosition())
                    || animal.tickCount % 60 != 0) {
                return;
            }
            animal.heal(1.5F);
        }

        private static boolean hasLifeStar(Player player) {
            return player.getMainHandItem().is(EndboundEmber.LIFE_STAR.get())
                    || player.getOffhandItem().is(EndboundEmber.LIFE_STAR.get())
                    || hasCuriosLifeStar(player);
        }

        private static boolean hasCuriosLifeStar(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.LIFE_STAR.get());
        }

        private static void updateMaxHealth(Player player, boolean active) {
            var instance = player.getAttribute(Attributes.MAX_HEALTH);
            if (instance == null) return;
            AttributeModifier modifier = instance.getModifier(HEALTH_MODIFIER_ID);
            if (active && modifier == null) {
                instance.addPermanentModifier(new AttributeModifier(
                        HEALTH_MODIFIER_ID,
                        80.0D,
                        AttributeModifier.Operation.ADD_VALUE));
            } else if (!active && modifier != null) {
                instance.removeModifier(HEALTH_MODIFIER_ID);
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }
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
            if (event.getEntity() instanceof HumanGirl human
                    && !human.level().isClientSide
                    && event.getSource().getEntity() instanceof LivingEntity attacker
                    && attacker.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                    && human.getHealth() - event.getAmount() <= human.getMaxHealth() * 0.5F) {
                replaceWithUndead((ServerLevel) human.level(), human);
                return;
            }

            if (!(event.getEntity() instanceof Player player)
                    || player.level().isClientSide
                    || !hasLifeStar(player)) {
                return;
            }

            float amount = event.getAmount();
            long gameTime = player.level().getGameTime();
            if (player.getHealth() - amount <= 0.0F
                    && player.getPersistentData().getLong(REVIVAL_COOLDOWN) <= gameTime) {
                event.setCanceled(true);
                for (var effect : new java.util.ArrayList<>(player.getActiveEffects())) {
                    if (!effect.getEffect().value().isBeneficial()) {
                        player.removeEffect(effect.getEffect());
                    }
                }
                player.setHealth(player.getMaxHealth() * 0.75F);
                player.getPersistentData().putLong(
                        REVIVAL_COOLDOWN,
                        gameTime + REVIVAL_COOLDOWN_TICKS);
                return;
            }

            // 朝露：仅对直接物理攻击（近战）按最大生命值的10%追加伤害
            var source = event.getSource();
            if (source.getEntity() instanceof LivingEntity
                    && source.getDirectEntity() == source.getEntity()) {
                event.setAmount(amount + player.getMaxHealth() * 0.10F);
            }
        }

        private static void replaceWithHuman(ServerLevel level, ZombieGirl old) {
            HumanGirl replacement = ModEntities.HUMAN_GIRL.get().create(level);
            if (replacement == null) return;
            copyCommon(old, replacement);
            replacement.setDrownedSource(old instanceof DrownedGirl);
            replacement.setLifeStarDiarySource(old.diaryVariant());
            old.remove(Entity.RemovalReason.DISCARDED);
            level.addFreshEntity(replacement);
        }

        private static void replaceWithUndead(ServerLevel level, HumanGirl old) {
            ZombieGirl replacement = (old.isDrownedSource() ? ModEntities.DROWNED_GIRL.get() : ModEntities.ZOMBIE_GIRL.get()).create(level);
            if (replacement == null) return;
            copyCommon(old, replacement);
            old.remove(Entity.RemovalReason.DISCARDED);
            level.addFreshEntity(replacement);
        }

        private static void copyCommon(ZombieGirl old, ZombieGirl replacement) {
            replacement.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
            replacement.setUUID(old.getUUID());
            replacement.setOwnerUUID(old.getOwnerUUID());
            replacement.setSkinVariant(old.getSkinVariant());
            // 保留随机化后的最大生命值，避免治愈/感染转换后血量上限回落
            var oldMaxHealth = old.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            var newMaxHealth = replacement.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (oldMaxHealth != null && newMaxHealth != null) {
                newMaxHealth.setBaseValue(oldMaxHealth.getBaseValue());
            }
            replacement.getPersistentData().putBoolean(ZombieGirl.HEALTH_ROLLED_TAG, true);
            replacement.setHealth(Math.min(old.getHealth(), replacement.getMaxHealth()));
            replacement.setPersistenceRequired();
            for (EquipmentSlot slot : EquipmentSlot.values()) replacement.setItemSlot(slot, old.getItemBySlot(slot).copy());
            for (int i = 0; i < old.getCompanionInventorySize(); i++) replacement.setCompanionStack(i, old.getCompanionStack(i).copy());
        }
    }
}
