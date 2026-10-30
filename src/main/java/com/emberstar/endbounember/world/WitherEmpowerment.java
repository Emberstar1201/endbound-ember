package com.emberstar.endbounember.world;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.MotherWitherSkull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 强化版凋零。
 *
 * <ul>
 *   <li>基础属性：最大生命 1000，护甲 24，只在生成时爆炸一次。</li>
 *   <li>凋零护盾：2000 点。护盾不吃护甲与抗性等常规减伤，但按原始伤害的 1000% 结算；
 *       近战再减 30%，弹射物再减 30%。</li>
 *   <li>护盾破裂：护盾归零后立刻失去飞行能力并瘫痪 5 秒。</li>
 *   <li>弹幕强化：头颅可破坏黑曜石，并偶尔发射会分裂的「母凋零之首」。</li>
 *   <li>飞行：全程保持飞行，悬停高度以目标为基准并限制上限，不会无限爬升。</li>
 *   <li>地面狂暴：公牛式直线冲撞并摧毁沿途非基岩方块，持续召唤强化凋零骷髅。</li>
 *   <li>吸血与回血：击杀非亡灵生物回血；自身回血快于原版，被亡灵杀手武器命中时回血受挫。</li>
 *   <li>凋零威压：附近玩家持续黑暗，每 15 秒一次 3 秒凋零 II。</li>
 *   <li>下界限定：主世界等其他维度召唤会被阻止并提示玩家。</li>
 * </ul>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class WitherEmpowerment {
    private static final String TAG_INITIALIZED = "EndboundEmberWitherInit";
    private static final String TAG_SHIELD = "EndboundEmberWitherShield";
    private static final String TAG_SHIELD_MAX = "EndboundEmberWitherShieldMax";
    private static final String TAG_PARALYZE = "EndboundEmberWitherParalyze";
    private static final String TAG_SKULL_COOLDOWN = "EndboundEmberWitherSkullCooldown";
    private static final String TAG_MOTHER_COOLDOWN = "EndboundEmberWitherMotherCooldown";
    private static final String TAG_CHARGE_COOLDOWN = "EndboundEmberWitherChargeCooldown";
    private static final String TAG_CHARGE_TICKS = "EndboundEmberWitherChargeTicks";
    private static final String TAG_CHARGE_DIR = "EndboundEmberWitherChargeDir";
    private static final String TAG_SUMMON_COOLDOWN = "EndboundEmberWitherSummonCooldown";
    private static final String TAG_REGEN_PENALTY = "EndboundEmberWitherRegenPenalty";
    private static final String TAG_WITHER_COOLDOWN = "EndboundEmberWitherWitherCooldown";
    /** 子头颅标记：伤害略低于普通头颅。 */
    public static final String TAG_CHILD_SKULL = "EndboundEmberWitherChildSkull";

    private static final double MAX_HEALTH = 1000.0D;
    private static final double ARMOR = 24.0D;
    private static final double BASE_ARMOR = 4.0D;

    /** 护盾上限。 */
    private static final float SHIELD_MAX = 2000.0F;
    /** 破盾效率：原始伤害 × 10。 */
    private static final float SHIELD_DAMAGE_MULTIPLIER = 10.0F;
    /** 护盾对近战物理伤害的减免。 */
    private static final float SHIELD_MELEE_REDUCTION = 0.30F;
    /** 护盾对弹射物的减免。 */
    private static final float SHIELD_PROJECTILE_REDUCTION = 0.30F;

    /** 护盾破裂后的瘫痪时长：5 秒。 */
    private static final int PARALYZE_TICKS = 100;

    /** 悬停高度（相对目标）。 */
    private static final double HOVER_HEIGHT = 12.0D;
    /**
     * 悬停高度上限（相对目标）。
     * <p>目标站在高处时若只按固定高度跟随，凋零会一路向上叠高直到脱离战斗，
     * 这里给相对高度封顶，保证它始终停留在玩家可攻击的范围内。
     */
    private static final double HOVER_MAX_HEIGHT = 20.0D;
    /** 悬停高度下限（相对目标），避免目标在低处时凋零贴地。 */
    private static final double HOVER_MIN_HEIGHT = 4.0D;

    private static final int SKULL_INTERVAL = 60;
    private static final int MOTHER_INTERVAL = 260;
    private static final int SUMMON_INTERVAL = 240;
    private static final int CHARGE_INTERVAL = 200;
    /** 冲撞持续 tick。 */
    private static final int CHARGE_TICKS = 30;
    /** 冲撞速度（格/tick）。 */
    private static final double CHARGE_SPEED = 1.1D;
    /** 冲撞命中伤害。 */
    private static final float CHARGE_DAMAGE = 24.0F;
    /** 冲撞破坏方块的半径（以凋零躯体为中心）。 */
    private static final int CHARGE_BREAK_RADIUS = 2;

    /** 附近玩家的判定半径。 */
    private static final double AURA_RANGE = 40.0D;
    /** 凋零威压的凋零 II 间隔与持续时间。 */
    private static final int AURA_WITHER_INTERVAL = 300;
    private static final int AURA_WITHER_DURATION = 60;
    /** 黑暗效果的刷新时长。 */
    private static final int AURA_DARKNESS_DURATION = 60;

    /** 每 20 tick 结算一次自愈，单次恢复最大生命的 0.4%（约为原版凋零的 4 倍）。 */
    private static final int REGEN_INTERVAL = 20;
    private static final double REGEN_RATIO = 0.004D;
    /** 被亡灵杀手命中后回血降低 60%，持续 8 秒。 */
    private static final int SMITE_PENALTY_TICKS = 160;
    private static final double SMITE_PENALTY = 0.6D;
    /** 击杀非亡灵生物时回复最大生命的 4%。 */
    private static final double LIFESTEAL_RATIO = 0.04D;
    /** 子头颅伤害相对普通头颅的倍率。 */
    private static final float CHILD_SKULL_DAMAGE_SCALE = 0.6F;

    private WitherEmpowerment() {
    }

    // ------------------------------------------------------------------
    // 生成：下界限定 + 属性强化
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onWitherJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof WitherBoss wither)) return;

        // 凋零只能在下界召唤：其他维度直接取消生成并提示最近的玩家
        if (event.getLevel().dimension() != Level.NETHER) {
            event.setCanceled(true);
            notifyNetherOnly(wither);
            return;
        }

        applyAttributes(wither);
        CompoundTag data = wither.getPersistentData();
        if (!data.getBoolean(TAG_INITIALIZED)) {
            data.putBoolean(TAG_INITIALIZED, true);
            // 生成时立即获得护盾
            data.putFloat(TAG_SHIELD_MAX, SHIELD_MAX);
            data.putFloat(TAG_SHIELD, SHIELD_MAX);
            wither.setHealth(wither.getMaxHealth());
        } else {
            // 读档：补齐缺失的护盾上限，避免旧存档没有护盾
            if (data.getFloat(TAG_SHIELD_MAX) <= 0.0F) {
                data.putFloat(TAG_SHIELD_MAX, SHIELD_MAX);
                data.putFloat(TAG_SHIELD, SHIELD_MAX);
            }
            if (wither.getHealth() > wither.getMaxHealth()) {
                wither.setHealth(wither.getMaxHealth());
            }
        }
    }

    /** 主世界召唤被阻止时，给附近玩家一条提示。 */
    private static void notifyNetherOnly(WitherBoss wither) {
        Component message = Component.translatable("entity.endbound_ember.wither.nether_only");
        for (Player player : wither.level().getEntitiesOfClass(Player.class,
                wither.getBoundingBox().inflate(48.0D))) {
            player.displayClientMessage(message, false);
        }
        if (wither.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE,
                    wither.getX(), wither.getY() + 1.0D, wither.getZ(), 40, 1.5D, 1.0D, 1.5D, 0.02D);
        }
    }

    private static void applyAttributes(WitherBoss wither) {
        AttributeInstance maxHealth = wither.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            if (Math.abs(maxHealth.getBaseValue() - MAX_HEALTH) > 0.001D) {
                maxHealth.setBaseValue(MAX_HEALTH);
            }
            if (wither.getHealth() > wither.getMaxHealth()) {
                wither.setHealth(wither.getMaxHealth());
            }
        }
        AttributeInstance armor = wither.getAttribute(Attributes.ARMOR);
        if (armor != null && Math.abs(armor.getBaseValue() - ARMOR) > 0.001D) {
            armor.setBaseValue(ARMOR);
        }
    }

    // ------------------------------------------------------------------
    // 护盾结算
    // ------------------------------------------------------------------

    /**
     * 护盾存在时按「韧性条」结算：
     * <p>
     * 本体照常损失生命，护盾同步被消耗，护盾伤害 = 原始伤害 × 10 ×（近战 70% / 弹射物 70% / 其他 100%），
     * 不吃护甲、抗性提升等常规减伤。护盾归零即破裂，解除保护并触发瘫痪。
     */
    @SubscribeEvent
    public static void onWitherIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof WitherBoss wither)) return;
        if (wither.level().isClientSide()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }

        CompoundTag data = wither.getPersistentData();
        float shield = data.getFloat(TAG_SHIELD);
        if (shield <= 0.0F) return;

        float shieldDamage = event.getAmount() * SHIELD_DAMAGE_MULTIPLIER;
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            shieldDamage *= 1.0F - SHIELD_PROJECTILE_REDUCTION;
        } else if (event.getSource().getDirectEntity() instanceof LivingEntity) {
            shieldDamage *= 1.0F - SHIELD_MELEE_REDUCTION;
        }

        // 护盾只作为韧性条被消耗，本体生命照常扣除
        data.putFloat(TAG_SHIELD, Math.max(0.0F, shield - shieldDamage));

        if (wither.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    wither.getX(), wither.getY() + wither.getBbHeight() * 0.5D, wither.getZ(),
                    6, wither.getBbWidth() * 0.5D, wither.getBbHeight() * 0.4D, wither.getBbWidth() * 0.5D, 0.02D);
        }

        if (data.getFloat(TAG_SHIELD) <= 0.0F) {
            breakShield(wither);
        }
    }

    /** 护盾破裂：失去飞行并瘫痪 5 秒。 */
    private static void breakShield(WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        data.putFloat(TAG_SHIELD, 0.0F);

        if (!(wither.level() instanceof ServerLevel level)) return;
        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                wither.getX(), wither.getY() + wither.getBbHeight() * 0.5D, wither.getZ(),
                2, 1.5D, 1.0D, 1.5D, 0.0D);

        data.putInt(TAG_PARALYZE, PARALYZE_TICKS);
        wither.setNoAi(true);
        wither.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------------
    // 每 tick 驱动
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) {
                if (entity instanceof WitherBoss wither) {
                    tickWither(level, wither);
                }
            }
        }
    }

    private static void tickWither(ServerLevel level, WitherBoss wither) {
        if (wither.isRemoved() || wither.isDeadOrDying()) return;

        applyAttributes(wither);
        tickCooldowns(wither);

        // 瘫痪：无法移动和攻击，同时停止回血与凋零威压
        if (wither.getPersistentData().getInt(TAG_PARALYZE) > 0) {
            tickParalyze(wither);
            return;
        }
        wither.setNoAi(false);

        // 回血与凋零威压是默认机制：全程持续生效，仅在瘫痪期间暂停
        tickRegen(level, wither);
        tickAura(level, wither);

        tickHover(level, wither);
        tickCharge(level, wither);
        tickSummon(level, wither);
        tickSkulls(level, wither);
    }

    private static void tickParalyze(WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        int ticks = data.getInt(TAG_PARALYZE);
        wither.setNoAi(true);
        wither.setDeltaMovement(Vec3.ZERO);
        data.putInt(TAG_PARALYZE, ticks - 1);
        if (ticks - 1 <= 0 && wither.level() instanceof ServerLevel level) {
            level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 1.2F);
        }
    }

    /**
     * 飞行悬停：以最近玩家为基准保持固定高度，并对相对高度封顶。
     * <p>
     * 原版凋零会不断向上爬升追击高处目标，玩家站在高台时它会一路飞到脱离战斗。
     * 这里把悬停高度夹在 [{@link #HOVER_MIN_HEIGHT}, {@link #HOVER_MAX_HEIGHT}] 之间，
     * 保证它始终停留在可攻击范围内。
     */
    private static void tickHover(ServerLevel level, WitherBoss wither) {
        wither.setNoGravity(true);
        // 冲撞期间不接管移动
        if (wither.getPersistentData().getInt(TAG_CHARGE_TICKS) > 0) return;

        Player target = nearestTarget(level, wither, 64.0D);
        if (target == null) {
            wither.setDeltaMovement(wither.getDeltaMovement().scale(0.8D));
            return;
        }

        double height = Mth.clamp(HOVER_HEIGHT, HOVER_MIN_HEIGHT, HOVER_MAX_HEIGHT);
        double desiredY = Math.min(target.getY() + height, target.getY() + HOVER_MAX_HEIGHT);
        Vec3 desired = new Vec3(target.getX(), desiredY, target.getZ());
        Vec3 delta = desired.subtract(wither.position()).scale(0.08D);
        // 限制垂直速度，避免上下抖动；到达高度后不再继续上浮
        double dy = Mth.clamp(delta.y, -0.4D, 0.4D);
        wither.setDeltaMovement(delta.x, dy, delta.z);
        wither.hurtMarked = true;
    }

    // ------------------------------------------------------------------
    // 冲撞
    // ------------------------------------------------------------------

    private static void tickCharge(ServerLevel level, WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        int active = data.getInt(TAG_CHARGE_TICKS);

        if (active > 0) {
            Vec3 direction = new Vec3(data.getDouble(TAG_CHARGE_DIR + "X"),
                    data.getDouble(TAG_CHARGE_DIR + "Y"),
                    data.getDouble(TAG_CHARGE_DIR + "Z"));
            wither.setDeltaMovement(direction.scale(CHARGE_SPEED));
            wither.hurtMarked = true;
            breakBlocksAlongCharge(level, wither);
            damageAlongCharge(level, wither, direction);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    wither.getX(), wither.getY() + 1.0D, wither.getZ(), 8, 1.0D, 0.6D, 1.0D, 0.02D);
            data.putInt(TAG_CHARGE_TICKS, active - 1);
            return;
        }

        if (data.getInt(TAG_CHARGE_COOLDOWN) > 0) return;
        Player target = nearestTarget(level, wither, 32.0D);
        if (target == null) return;

        Vec3 aim = target.position().subtract(wither.position());
        Vec3 flat = new Vec3(aim.x, 0.0D, aim.z);
        if (flat.lengthSqr() < 0.01D) return;

        Vec3 direction = flat.normalize();
        data.putInt(TAG_CHARGE_COOLDOWN, CHARGE_INTERVAL);
        data.putInt(TAG_CHARGE_TICKS, CHARGE_TICKS);
        data.putDouble(TAG_CHARGE_DIR + "X", direction.x);
        data.putDouble(TAG_CHARGE_DIR + "Y", 0.0D);
        data.putDouble(TAG_CHARGE_DIR + "Z", direction.z);

        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    /** 冲撞沿途摧毁非基岩方块。 */
    private static void breakBlocksAlongCharge(ServerLevel level, WitherBoss wither) {
        BlockPos center = wither.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-CHARGE_BREAK_RADIUS, -1, -CHARGE_BREAK_RADIUS),
                center.offset(CHARGE_BREAK_RADIUS, CHARGE_BREAK_RADIUS, CHARGE_BREAK_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            // 基岩与不可破坏方块保留
            if (state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0F) continue;
            level.destroyBlock(pos, false, wither);
        }
    }

    /** 冲撞撞到的生物受到伤害并被撞飞。 */
    private static void damageAlongCharge(ServerLevel level, WitherBoss wither, Vec3 direction) {
        AABB area = wither.getBoundingBox().inflate(1.5D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, WitherEmpowerment::isValidTarget)) {
            victim.hurt(level.damageSources().mobAttack(wither), CHARGE_DAMAGE);
            victim.push(direction.x * 1.4D, 0.5D, direction.z * 1.4D);
            victim.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // 召唤强化凋零骷髅
    // ------------------------------------------------------------------

    private static void tickSummon(ServerLevel level, WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        if (data.getInt(TAG_SUMMON_COOLDOWN) > 0) return;

        int nearby = level.getEntitiesOfClass(WitherSkeleton.class,
                wither.getBoundingBox().inflate(24.0D), Entity::isAlive).size();
        if (nearby >= 6) return;

        data.putInt(TAG_SUMMON_COOLDOWN, SUMMON_INTERVAL);
        for (int i = 0; i < 2; i++) {
            WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
            if (skeleton == null) continue;
            double x = wither.getX() + level.random.nextInt(9) - 4;
            double z = wither.getZ() + level.random.nextInt(9) - 4;
            double y = wither.getY();
            skeleton.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            // 强化：加速、生命翻倍、武器加亡灵杀手
            skeleton.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
            skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1));
            AttributeInstance maxHealth = skeleton.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * 2.0D);
                skeleton.setHealth(skeleton.getMaxHealth());
            }
            ItemStack sword = skeleton.getMainHandItem();
            if (!sword.isEmpty()) {
                sword.enchant(level.holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SMITE), 2);
            }
            skeleton.setTarget(nearestTarget(level, wither, 24.0D));
            level.addFreshEntity(skeleton);
        }
        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    // ------------------------------------------------------------------
    // 弹幕：普通头颅 + 母头颅
    // ------------------------------------------------------------------

    private static void tickSkulls(ServerLevel level, WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        Player target = nearestTarget(level, wither, 48.0D);
        if (target == null) return;

        // 母凋零之首
        if (data.getInt(TAG_MOTHER_COOLDOWN) <= 0) {
            data.putInt(TAG_MOTHER_COOLDOWN, MOTHER_INTERVAL);
            Vec3 direction = target.getEyePosition().subtract(wither.getEyePosition());
            if (direction.lengthSqr() > 0.01D) {
                MotherWitherSkull mother = new MotherWitherSkull(level, wither, direction);
                level.addFreshEntity(mother);
            }
            return;
        }

        // 普通头颅
        if (data.getInt(TAG_SKULL_COOLDOWN) <= 0) {
            data.putInt(TAG_SKULL_COOLDOWN, SKULL_INTERVAL);
            Vec3 direction = target.getEyePosition().subtract(wither.getEyePosition());
            if (direction.lengthSqr() > 0.01D) {
                WitherSkull skull = new WitherSkull(level, wither, direction.normalize());
                skull.setPos(wither.getX(), wither.getY() + wither.getBbHeight() * 0.6D, wither.getZ());
                level.addFreshEntity(skull);
            }
        }
    }

    // ------------------------------------------------------------------
    // 凋零威压（黑暗 + 周期性凋零 II），全程生效，瘫痪期间暂停
    // ------------------------------------------------------------------

    private static void tickAura(ServerLevel level, WitherBoss wither) {
        CompoundTag data = wither.getPersistentData();
        boolean witherReady = data.getInt(TAG_WITHER_COOLDOWN) <= 0;
        if (witherReady) {
            data.putInt(TAG_WITHER_COOLDOWN, AURA_WITHER_INTERVAL);
        }

        for (Player player : level.getEntitiesOfClass(Player.class,
                wither.getBoundingBox().inflate(AURA_RANGE), WitherEmpowerment::isValidTarget)) {
            // 不间断黑暗：持续刷新
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, AURA_DARKNESS_DURATION, 0, false, false, false));
            if (witherReady) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, AURA_WITHER_DURATION, 1, false, true, true));
            }
        }

        // 黑暗期间暴露 BOSS 位置：黑暗是范围施加的，被笼罩的玩家可直接看到凋零轮廓
        boolean anyDarkened = false;
        for (Player player : level.getEntitiesOfClass(Player.class,
                wither.getBoundingBox().inflate(AURA_RANGE), WitherEmpowerment::isValidTarget)) {
            if (player.hasEffect(MobEffects.DARKNESS)) {
                anyDarkened = true;
                break;
            }
        }
        if (anyDarkened) {
            wither.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));
        }
    }

    // ------------------------------------------------------------------
    // 回血与吸血，全程生效，瘫痪期间暂停回血
    // ------------------------------------------------------------------

    private static void tickRegen(ServerLevel level, WitherBoss wither) {
        if (wither.tickCount % REGEN_INTERVAL != 0) return;
        if (wither.getHealth() >= wither.getMaxHealth()) return;

        double ratio = REGEN_RATIO;
        if (wither.getPersistentData().getInt(TAG_REGEN_PENALTY) > 0) {
            ratio *= 1.0D - SMITE_PENALTY;
        }
        wither.heal((float) (wither.getMaxHealth() * ratio));
    }

    /** 亡灵杀手命中：降低凋零的回血速度。 */
    @SubscribeEvent
    public static void onWitherHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof WitherBoss wither)) return;
        if (wither.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        int smite = EnchantmentHelper.getEnchantmentLevel(
                wither.level().holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SMITE),
                attacker);
        if (smite <= 0) return;

        wither.getPersistentData().putInt(TAG_REGEN_PENALTY, SMITE_PENALTY_TICKS);
    }

    /** 击杀非亡灵生物时吸血。 */
    @SubscribeEvent
    public static void onWitherKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof WitherBoss wither)) return;
        LivingEntity victim = event.getEntity();
        if (victim == wither) return;
        // 非亡灵生物才提供吸血
        if (victim.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) return;
        if (wither.isDeadOrDying()) return;

        wither.heal((float) (wither.getMaxHealth() * LIFESTEAL_RATIO));
        if (wither.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    wither.getX(), wither.getY() + wither.getBbHeight() * 0.7D, wither.getZ(),
                    6, 0.6D, 0.4D, 0.6D, 0.02D);
        }
    }

    // ------------------------------------------------------------------
    // 子头颅伤害下调
    // ------------------------------------------------------------------

    /** 母头颅分裂出的子头颅伤害略低于普通头颅。 */
    @SubscribeEvent
    public static void onChildSkullDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof WitherSkull skull)) return;
        if (!skull.getPersistentData().getBoolean(TAG_CHILD_SKULL)) return;
        event.setAmount(event.getAmount() * CHILD_SKULL_DAMAGE_SCALE);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private static Player nearestTarget(ServerLevel level, WitherBoss wither, double range) {
        return level.getEntitiesOfClass(Player.class, wither.getBoundingBox().inflate(range),
                        player -> !player.isCreative() && !player.isSpectator())
                .stream()
                .min((first, second) -> Double.compare(wither.distanceToSqr(first), wither.distanceToSqr(second)))
                .orElse(null);
    }

    private static void tickCooldowns(WitherBoss wither) {
        decrement(wither, TAG_SKULL_COOLDOWN);
        decrement(wither, TAG_MOTHER_COOLDOWN);
        decrement(wither, TAG_CHARGE_COOLDOWN);
        decrement(wither, TAG_SUMMON_COOLDOWN);
        decrement(wither, TAG_REGEN_PENALTY);
        decrement(wither, TAG_WITHER_COOLDOWN);
    }

    private static void decrement(Entity entity, String key) {
        int cooldown = entity.getPersistentData().getInt(key);
        if (cooldown > 0) {
            entity.getPersistentData().putInt(key, cooldown - 1);
        }
    }

    /** 供外部读取当前护盾值（调试或 HUD 用）。 */
    public static float shieldOf(WitherBoss wither) {
        return wither.getPersistentData().getFloat(TAG_SHIELD);
    }

    /** 供外部读取护盾上限。 */
    public static float shieldMaxOf(WitherBoss wither) {
        return wither.getPersistentData().getFloat(TAG_SHIELD_MAX);
    }
}
