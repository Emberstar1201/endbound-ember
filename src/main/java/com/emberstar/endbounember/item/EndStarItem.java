package com.emberstar.endbounember.item;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 终界之星：参考旧版实现，效果全部在 Events 中处理。
 */
public class EndStarItem extends Item {
    private static final int COLOR = 0xC9A0FF;

    public EndStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(COLOR));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.end_star.condition").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.flight").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.experience").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.explosion").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.projectile").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.physical").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.magic").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.void").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.labor").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.movement").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.end_star.end_kin").withStyle(style -> style.withColor(COLOR)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        private static final String HOLDING = "EndboundEmberEndStarHolding";
        private static final String TEMP_MAYFLY = "EndboundEmberEndStarTempMayfly";
        private static final String PREV_MAYFLY = "EndboundEmberEndStarPrevMayfly";
        private static final String PREV_FLYING = "EndboundEmberEndStarPrevFlying";
        private static final String VOID_RESCUE_TICKS = "EndboundEmberEndStarVoidRescueTicks";
        private static final int VOID_RESCUE_DELAY_TICKS = 60;

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            boolean holding = hasEndStar(player);

            if (player.level().isClientSide) {
                spawnParticles(player, holding);
                return;
            }

            updateFlight(player, holding);
            if (!holding) {
                player.getPersistentData().remove(VOID_RESCUE_TICKS);
                return;
            }

            if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) player.removeEffect(MobEffects.DIG_SLOWDOWN);
            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            removeNegativeMoveModifiers(player);

            if (player instanceof ServerPlayer serverPlayer) {
                handleVoidRescue(serverPlayer);
            }

            if (player.tickCount % 10 == 0) {
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D))) {
                    if (isEndKin(mob) && mob.getTarget() != null) mob.setTarget(null);
                }
            }
        }

        @SubscribeEvent
        public static void onDragonDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof EnderDragon dragon)) return;
            if (!(dragon.level() instanceof ServerLevel level)) return;
            if (level.dimension() != Level.END) return;

            // 击败末影龙后，在末地中央 (0, 70, 0) 生成终界之星。
            // 关闭重力，避免掉落物被下方中央的返回传送门吸走。
            ItemEntity star = new ItemEntity(level, 0.5D, 70.0D, 0.5D,
                    new ItemStack(EndboundEmber.END_STAR.get()));
            star.setNoGravity(true);
            star.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(star);
        }

        @SubscribeEvent
        public static void onAttackBonus(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof Player attacker)
                    || attacker == event.getEntity()
                    || !hasEndStar(attacker)) {
                return;
            }

            // 经验加伤：每 10 点经验提升 1%，无上限。
            int bonusPercent = Math.floorDiv(attacker.totalExperience, 10);
            if (bonusPercent > 0) {
                event.setAmount(event.getAmount() * (1.0F + bonusPercent / 100.0F));
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof Player player)
                    || player.level().isClientSide
                    || !hasEndStar(player)) {
                return;
            }

            DamageSource source = event.getSource();
            float amount = event.getAmount();

            // 绝对致死伤害（/kill 的 generic_kill 等无视无敌的伤害）免疫。
            if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
                return;
            }

            // 末影系绝对中立。
            if (isEndKin(source.getEntity())) {
                event.setCanceled(true);
                return;
            }

            // 虚空伤害免疫。
            if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
                event.setCanceled(true);
                return;
            }

            // 爆炸伤害免疫。
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                event.setCanceled(true);
                return;
            }

            // 弹射物伤害减免 75%。
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                event.setAmount(amount * 0.25F);
                return;
            }

            // 魔法伤害减免 80%。
            if (isMagic(source)) {
                event.setAmount(amount * 0.2F);
                return;
            }

            // 其余（物理与环境）伤害减免 50%。
            event.setAmount(amount * 0.5F);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            Player player = event.getEntity();
            if (!hasEndStar(player)) return;

            float speed = event.getOriginalSpeed();
            if (player.isEyeInFluid(FluidTags.WATER)) {
                double factor = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
                if (factor > 0.0D && factor < 1.0D) speed /= (float) factor;
            }
            if (!player.onGround()) speed *= 5.0F;
            event.setNewSpeed(speed);
        }

        @SubscribeEvent
        public static void onChangeTarget(LivingChangeTargetEvent event) {
            if (isEndKin(event.getEntity())
                    && event.getNewAboutToBeSetTarget() instanceof Player player
                    && hasEndStar(player)) {
                event.setCanceled(true);
            }
        }

        private static void updateFlight(Player player, boolean holding) {
            // 创造与旁观模式的飞行由原版管理，不覆盖其状态。
            if (player.isCreative() || player.isSpectator()) {
                player.getPersistentData().putBoolean(HOLDING, false);
                clearFlightData(player);
                return;
            }

            CompoundTag data = player.getPersistentData();
            boolean wasHolding = data.getBoolean(HOLDING);
            if (holding && !wasHolding) {
                data.putBoolean(PREV_MAYFLY, player.getAbilities().mayfly);
                data.putBoolean(PREV_FLYING, player.getAbilities().flying);
                data.putBoolean(TEMP_MAYFLY, !player.getAbilities().mayfly);
            }
            if (holding && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            } else if (!holding && wasHolding) {
                if (data.getBoolean(TEMP_MAYFLY)) {
                    player.getAbilities().mayfly = data.getBoolean(PREV_MAYFLY);
                    player.getAbilities().flying = data.getBoolean(PREV_FLYING);
                    player.onUpdateAbilities();
                }
                clearFlightData(player);
            }
            data.putBoolean(HOLDING, holding);
        }

        private static void clearFlightData(Player player) {
            CompoundTag data = player.getPersistentData();
            data.remove(TEMP_MAYFLY);
            data.remove(PREV_MAYFLY);
            data.remove(PREV_FLYING);
        }

        private static void removeNegativeMoveModifiers(Player player) {
            AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance == null) return;
            List<net.minecraft.resources.ResourceLocation> negative = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.amount() < 0.0D) negative.add(modifier.id());
            }
            for (net.minecraft.resources.ResourceLocation id : negative) {
                instance.removeModifier(id);
            }
        }

        private static void handleVoidRescue(ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            if (player.getY() >= player.level().getMinBuildHeight() - 16) {
                data.remove(VOID_RESCUE_TICKS);
                return;
            }

            player.setDeltaMovement(
                    player.getDeltaMovement().x,
                    Math.max(player.getDeltaMovement().y, 0.12D),
                    player.getDeltaMovement().z);
            player.resetFallDistance();

            int remaining = data.getInt(VOID_RESCUE_TICKS);
            if (remaining <= 0) remaining = VOID_RESCUE_DELAY_TICKS;
            remaining--;
            if (remaining <= 0) {
                teleportToRespawn(player);
                data.remove(VOID_RESCUE_TICKS);
            } else {
                data.putInt(VOID_RESCUE_TICKS, remaining);
            }
        }

        private static void teleportToRespawn(ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server == null) return;

            BlockPos respawn = player.getRespawnPosition();
            ServerLevel targetLevel = respawn == null
                    ? server.overworld()
                    : server.getLevel(player.getRespawnDimension());
            if (targetLevel == null) targetLevel = server.overworld();

            BlockPos targetPos = respawn == null ? targetLevel.getSharedSpawnPos() : respawn;
            player.stopRiding();
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(targetLevel,
                    targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        }

        private static void spawnParticles(Player player, boolean holding) {
            if (player.tickCount % 4 != 0) return;
            if (holding) {
                spawnEnchantParticles(player.getX(), player.getY() + player.getBbHeight() * 0.5D,
                        player.getZ(), player);
            }
            for (ItemEntity item : player.level().getEntitiesOfClass(
                    ItemEntity.class, player.getBoundingBox().inflate(32.0D))) {
                if (!item.getItem().is(EndboundEmber.END_STAR.get())) continue;
                spawnEnchantParticles(item.getX(), item.getY() + item.getBbHeight() * 0.5D,
                        item.getZ(), player);
            }
        }

        private static void spawnEnchantParticles(double x, double y, double z, Player player) {
            float radius = 0.9F;
            for (int i = 0; i < 4; i++) {
                double angle = player.getRandom().nextDouble() * 2.0D * Math.PI;
                double ox = x + Math.cos(angle) * radius;
                double oz = z + Math.sin(angle) * radius;
                player.level().addParticle(ParticleTypes.ENCHANT,
                        ox, y + (player.getRandom().nextDouble() - 0.5D) * 0.5D, oz,
                        (player.getRandom().nextDouble() - 0.5D) * 0.02D,
                        0.08D + player.getRandom().nextDouble() * 0.04D,
                        (player.getRandom().nextDouble() - 0.5D) * 0.02D);
            }
        }

        private static boolean isMagic(DamageSource source) {
            return source.is(DamageTypes.MAGIC)
                    || source.is(DamageTypes.INDIRECT_MAGIC)
                    || source.is(DamageTypes.DRAGON_BREATH)
                    || source.is(DamageTypes.SONIC_BOOM)
                    || source.is(DamageTypes.WITHER);
        }

        private static boolean isEndKin(Entity entity) {
            return entity instanceof EnderMan
                    || entity instanceof Endermite
                    || entity instanceof Shulker
                    || entity instanceof EnderDragon;
        }

        private static boolean hasEndStar(Player player) {
            return player.getMainHandItem().is(EndboundEmber.END_STAR.get())
                    || player.getOffhandItem().is(EndboundEmber.END_STAR.get())
                    || hasCuriosEndStar(player);
        }

        private static boolean hasCuriosEndStar(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.END_STAR.get());
        }
    }
}
