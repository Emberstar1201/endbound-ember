package com.emberstar.endbounember.world;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.EndApproachesEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 末影龙强化。与旧版「末影龙强化」思路相近但机制全部重写：
 *
 * <ul>
 *   <li>晶核共鸣：场上每颗末影水晶提供 8% 减伤，10 颗时达到 80% 上限；虚空伤害、蓝火剑气与真实伤害无视该减伤。</li>
 *   <li>晶体重塑：被摧毁的水晶每隔 5 分钟在原有位置重塑一颗，同一时间只会重塑一颗。</li>
 *   <li>隐藏韧性：受到伤害会同步削减隐性韧性；韧性耗尽后末影龙坠地瘫痪 10 秒，期间失去全部减伤，只剩 20 点护甲。</li>
 *   <li>晶核滋养：水晶越多，龙的自愈速度越快。</li>
 *   <li>碎晶破绽：水晶数量减少时，龙进入 10 秒破绽期，受到伤害提高 55%。</li>
 *   <li>濒死狂怒：生命低于 45% 时受到伤害提高 30%。</li>
 *   <li>祭坛龙息：停靠时在脚下留下附带凋零的龙息云。</li>
 *   <li>末影回响：玩家投出的末影珍珠会引来一发追踪龙火球。</li>
 *   <li>龙裔召唤：生命首次低于 50% 时召唤 3 只加速的末影人。</li>
 *   <li>龙威自愈：附近没有玩家时恢复更快。</li>
 *   <li>终末咆哮：周期性对近身玩家施加黑暗并击退。</li>
 * </ul>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EnderDragonEmpowerment {
    private static final String TAG_OPENING_TICKS = "EndboundEmberDragonOpening";
    private static final String TAG_CRYSTAL_COUNT = "EndboundEmberDragonCrystalCount";
    private static final String TAG_SUMMONED = "EndboundEmberDragonSummoned";
    private static final String TAG_FIREBALL_COOLDOWN = "EndboundEmberDragonFireballCooldown";
    private static final String TAG_BREATH_COOLDOWN = "EndboundEmberDragonBreathCooldown";
    private static final String TAG_ROAR_COOLDOWN = "EndboundEmberDragonRoarCooldown";
    private static final String TAG_PEARL_COOLDOWN = "EndboundEmberDragonPearlCooldown";
    private static final String TAG_CRYSTAL_SITES = "EndboundEmberDragonCrystalSites";
    private static final String TAG_CRYSTAL_PENDING = "EndboundEmberDragonCrystalPending";
    private static final String TAG_POISE = "EndboundEmberDragonPoise";
    private static final String TAG_POISE_REGEN_DELAY = "EndboundEmberDragonPoiseRegenDelay";
    private static final String TAG_POISE_IMMUNE = "EndboundEmberDragonPoiseImmune";
    private static final String TAG_PARALYZE_TICKS = "EndboundEmberDragonParalyze";
    private static final String TAG_PARALYZE_LANDED = "EndboundEmberDragonParalyzeLanded";

    private static final double MAX_HEALTH = 1000.0D;
    private static final double ARMOR = 10.0D;
    /** 瘫痪期间失去一切减伤，只保留 20 点护甲。 */
    private static final double PARALYZE_ARMOR = 20.0D;

    /** 碎晶破绽持续 10 秒，期间受到的伤害提高 55%。 */
    private static final int OPENING_TICKS = 200;
    private static final float OPENING_BONUS = 0.55F;
    /** 生命低于 45% 时受到的伤害提高 30%。 */
    private static final float LOW_HEALTH_THRESHOLD = 0.45F;
    private static final float LOW_HEALTH_BONUS = 0.30F;
    /** 每颗水晶 8% 减伤，10 颗（末地全部水晶）达到 80% 上限。 */
    private static final float CRYSTAL_REDUCTION = 0.08F;
    private static final int CRYSTAL_CAP = 10;
    private static final float PROJECTILE_REDUCTION = 0.5F;

    /** 水晶被摧毁后每 5 分钟（6000 tick）重塑一颗，同一时间只会重塑一颗。 */
    private static final int CRYSTAL_RESPAWN_DELAY = 6000;
    /** 排队的重生点使用 -1 作为标记，等待当前重塑完成后才会接棒。 */
    private static final int CRYSTAL_QUEUED = -1;

    /** 隐藏韧性上限。韧性按实际损失的生命值等比削减。 */
    private static final float MAX_POISE = 600.0F;
    /** 韧性耗尽后瘫痪 10 秒。 */
    private static final int PARALYZE_TICKS = 200;
    /** 瘫痪开始时把龙抬到祭坛上方这个高度，再由原版着陆相位俯冲下来。 */
    private static final double PARALYZE_DROP_HEIGHT = 18.0D;
    /** 与祭坛落点的三维距离小于这个值就判定为落地，随即原地冻结。 */
    private static final double PARALYZE_LAND_DISTANCE = 1.5D;
    /** 最后一次受伤 5 秒后韧性才开始恢复。 */
    private static final int POISE_REGEN_DELAY = 100;
    /** 每秒恢复的韧性，30 秒回满。 */
    private static final float POISE_REGEN_PER_SECOND = MAX_POISE / 30.0F;
    /** 瘫痪结束后 15 秒内韧性不会被再次削减，避免被无限连控。 */
    private static final int POISE_IMMUNE_TICKS = 300;
    /** 每 40 tick 结算一次自愈：基础 0.15%，每颗水晶再加 0.1% 最大生命。 */
    private static final double BASE_REGEN = 0.0015D;
    private static final double CRYSTAL_REGEN = 0.001D;
    private static final double SOLITUDE_REGEN = 0.01D;

    private static final int FIREBALL_INTERVAL = 160;
    private static final int BREATH_INTERVAL = 60;
    private static final int ROAR_INTERVAL = 240;
    private static final int PEARL_INTERVAL = 60;

    /** 末影水晶全部位于中央岛，96 格范围足以覆盖所有柱子。 */
    private static final AABB CRYSTAL_AREA = new AABB(-96.0D, -64.0D, -96.0D, 96.0D, 320.0D, 96.0D);

    private EnderDragonEmpowerment() {
    }

    /** 龙刚进入世界时完成基础强化。 */
    @SubscribeEvent
    public static void onDragonJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;

        applyAttributes(dragon);
        if (!event.loadedFromDisk()) {
            dragon.setHealth(dragon.getMaxHealth());
        } else if (dragon.getHealth() > dragon.getMaxHealth()) {
            dragon.setHealth(dragon.getMaxHealth());
        }

        CompoundTag data = dragon.getPersistentData();
        if (!data.contains(TAG_POISE)) {
            data.putFloat(TAG_POISE, MAX_POISE);
        }
        // 重载时若仍处于瘫痪，恢复瘫痪驱动的状态，避免被原版 phase 打断
        if (data.getInt(TAG_PARALYZE_TICKS) > 0) {
            if (data.getBoolean(TAG_PARALYZE_LANDED)) {
                dragon.setNoAi(true);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            } else {
                dragon.setNoAi(false);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING);
            }
        }
    }

    /** 受击时的减伤与破绽判定。 */
    @SubscribeEvent
    public static void onDragonIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (dragon.level().isClientSide()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }

        float amount = event.getAmount();

        // 瘫痪：失去全部减伤，只保留 20 点护甲（护甲由 applyAttributes 换成 20 点）
        if (isParalyzed(dragon)) {
            return;
        }

        // 晶核共鸣：水晶越多，龙越坚硬；虚空伤害与真实伤害不受影响
        if (!piercesCrystalWard(event.getSource())) {
            int crystals = Math.min(CRYSTAL_CAP, countCrystals(dragon));
            crystals = EndApproachesEnchantment.effectiveCrystalCount(dragon, event.getSource(), crystals);
            amount *= 1.0F - crystals * CRYSTAL_REDUCTION;

            // 弹射物在共鸣下进一步衰减，但不再像旧版那样完全免疫
            if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                amount *= 1.0F - PROJECTILE_REDUCTION;
            }
        }

        // 碎晶破绽
        if (dragon.getPersistentData().getInt(TAG_OPENING_TICKS) > 0) {
            amount *= 1.0F + OPENING_BONUS;
        }

        // 濒死狂怒
        if (dragon.getHealth() <= dragon.getMaxHealth() * LOW_HEALTH_THRESHOLD) {
            amount *= 1.0F + LOW_HEALTH_BONUS;
        }

        event.setAmount(amount);
    }

    /**
     * 隐藏韧性：按实际造成的伤害同步削减，韧性耗尽即进入瘫痪。
     * 伤害越高掉得越快，但破绽、濒死狂怒这类增伤不会加速削韧。
     */
    @SubscribeEvent
    public static void onDragonDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (dragon.level().isClientSide()) return;
        if (dragon.isDeadOrDying()) return;
        // 掉出世界、/kill 之类不参与韧性
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }
        if (isParalyzed(dragon)) return;
        if (event.getNewDamage() <= 0.0F) return;

        CompoundTag data = dragon.getPersistentData();
        data.putInt(TAG_POISE_REGEN_DELAY, POISE_REGEN_DELAY);

        // 刚瘫痪结束的免疫期内不会被继续削韧
        if (data.getInt(TAG_POISE_IMMUNE) > 0) return;

        float poise = Math.max(0.0F, data.getFloat(TAG_POISE) - event.getNewDamage());
        data.putFloat(TAG_POISE, poise);
        if (poise > 0.0F) return;

        startParalyze(dragon);
    }

    /** 虚空伤害（星火不灭）、蓝火剑气与真实伤害（拂晓剑气）无视水晶提供的减伤。 */
    private static boolean piercesCrystalWard(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DawnEnchantment.DAMAGE_TYPE)
                || source.is(FreeWillEnchantments.BLUE_FLAME)
                || source.is(FreeWillEnchantments.VOID_SPARK);
    }

    /** 服务端每 tick 维护末影龙的战斗行为。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.END) continue;
            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) {
                if (entity instanceof EnderDragon dragon) {
                    tickDragon(level, dragon);
                }
            }
        }
    }

    private static void tickDragon(ServerLevel level, EnderDragon dragon) {
        if (dragon.isRemoved() || dragon.isDeadOrDying()) return;
        tickParalyze(level, dragon);
        applyAttributes(dragon);
        tickCrystalOpening(level, dragon);
        tickCrystalRegen(level, dragon);
        tickOpening(level, dragon);
        tickPoise(dragon);
        tickCooldowns(dragon);
        // 瘫痪期间不进行任何主动行为
        if (isParalyzed(dragon)) return;
        tickBreathCloud(level, dragon);
        tickSummon(level, dragon);
        tickRecovery(level, dragon);
        tickRoar(level, dragon);
        tickFireballs(level, dragon);
        tickPearlResponse(level, dragon);
    }

    private static void applyAttributes(EnderDragon dragon) {
        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            if (Math.abs(maxHealth.getBaseValue() - MAX_HEALTH) > 0.001D) {
                maxHealth.setBaseValue(MAX_HEALTH);
            }
            if (dragon.getHealth() > dragon.getMaxHealth()) {
                dragon.setHealth(dragon.getMaxHealth());
            }
        }
        double armorTarget = isParalyzed(dragon) ? PARALYZE_ARMOR : ARMOR;
        AttributeInstance armor = dragon.getAttribute(Attributes.ARMOR);
        if (armor != null && Math.abs(armor.getBaseValue() - armorTarget) > 0.001D) {
            armor.setBaseValue(armorTarget);
        }
    }

    private static boolean isParalyzed(EnderDragon dragon) {
        return dragon.getPersistentData().getInt(TAG_PARALYZE_TICKS) > 0;
    }

    /**
     * 韧性耗尽后把末影龙挪到祭坛正上方，交给原版着陆相位带躯体俯冲。
     * 位置只在这一刻设置一次，之后全程由原版驱动，避免手动改坐标导致躯体各部分错位。
     */
    private static void startParalyze(EnderDragon dragon) {
        // 已进入死亡流程时不接管，否则会覆盖 DYING 相位导致死亡动画无法播放
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) return;
        if (!(dragon.level() instanceof ServerLevel level)) return;

        Vec3 landing = altarLandingPoint(level, dragon);
        double dropY = landing.y + PARALYZE_DROP_HEIGHT;

        CompoundTag data = dragon.getPersistentData();
        data.putInt(TAG_PARALYZE_TICKS, PARALYZE_TICKS);
        data.putBoolean(TAG_PARALYZE_LANDED, false);
        data.putFloat(TAG_POISE, 0.0F);

        dragon.setNoAi(false);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setPos(landing.x, dropY, landing.z);
        // 渲染躯体各段用的是历史坐标，传送后要整体对齐，否则头尾会被旧坐标拉扯
        resetLatencyHistory(dragon, dropY);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING);

        level.playSound(null, landing.x, dropY, landing.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                landing.x, dropY + 1.0D, landing.z, 3, 1.5D, 1.0D, 1.5D, 0.0D);
    }

    /** 瘫痪计时与坠落：下坠交给原版，落地后原地冻结。 */
    private static void tickParalyze(ServerLevel level, EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        int ticks = data.getInt(TAG_PARALYZE_TICKS);
        if (ticks <= 0) return;

        // 致死后原版需要 phase tick 播放死亡动画，必须立刻交还控制权，否则软锁
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            data.putInt(TAG_PARALYZE_TICKS, 0);
            data.putBoolean(TAG_PARALYZE_LANDED, false);
            dragon.setNoAi(false);
            return;
        }

        if (data.getBoolean(TAG_PARALYZE_LANDED)) {
            // 落地后冻结：不再移动，碰撞部件停在上一个 tick 的位置，正好覆盖龙身
            dragon.setNoAi(true);
        } else {
            Vec3 landing = altarLandingPoint(level, dragon);
            if (dragon.position().distanceToSqr(landing) <= PARALYZE_LAND_DISTANCE * PARALYZE_LAND_DISTANCE) {
                landParalyzedDragon(level, dragon, landing);
            } else if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.LANDING) {
                // 着陆相位被其他行为顶掉时重新接管
                dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING);
            }
        }

        data.putInt(TAG_PARALYZE_TICKS, ticks - 1);
        if (ticks - 1 <= 0) {
            endParalyze(level, dragon);
        }
    }

    /**
     * 砸到祭坛上：就地冻结，不再改动坐标，保证可受击部件与躯体完全同步。
     * 使用非 sitting 相位，瘫痪期间持续受击不会触发原版 25% 最大生命值的起飞判定。
     */
    private static void landParalyzedDragon(ServerLevel level, EnderDragon dragon, Vec3 landing) {
        dragon.getPersistentData().putBoolean(TAG_PARALYZE_LANDED, true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setNoAi(true);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        level.playSound(null, landing.x, landing.y, landing.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                landing.x, landing.y + 1.0D, landing.z, 4, 2.5D, 0.5D, 2.5D, 0.0D);
    }

    /** 原版着陆相位使用的祭坛落点：中央祭坛地表上方一格。 */
    private static Vec3 altarLandingPoint(ServerLevel level, EnderDragon dragon) {
        return Vec3.atBottomCenterOf(level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EndPodiumFeature.getLocation(dragon.getFightOrigin())));
    }

    /** 渲染躯体各段用的历史坐标，传送后整体对齐，避免头尾被旧坐标拉扯。 */
    private static void resetLatencyHistory(EnderDragon dragon, double y) {
        float yRot = dragon.getYRot();
        for (double[] pos : dragon.positions) {
            pos[0] = yRot;
            pos[1] = y;
        }
        // 置空指针，让原版在下一 tick 用当前坐标整段重写历史
        dragon.posPointer = -1;
    }

    /** 瘫痪结束：交还原版控制，韧性回满并获得短暂免削韧窗口。 */
    private static void endParalyze(ServerLevel level, EnderDragon dragon) {
        CompoundTag data = dragon.getPersistentData();
        data.putInt(TAG_PARALYZE_TICKS, 0);
        data.putBoolean(TAG_PARALYZE_LANDED, false);
        data.putFloat(TAG_POISE, MAX_POISE);
        data.putInt(TAG_POISE_IMMUNE, POISE_IMMUNE_TICKS);
        dragon.setNoAi(false);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.3F);
    }

    /** 韧性恢复：停止受伤 5 秒后开始回满，大约 30 秒恢复完成。 */
    private static void tickPoise(EnderDragon dragon) {
        if (isParalyzed(dragon)) return;
        CompoundTag data = dragon.getPersistentData();
        decrement(dragon, TAG_POISE_IMMUNE);
        int delay = data.getInt(TAG_POISE_REGEN_DELAY);
        if (delay > 0) {
            data.putInt(TAG_POISE_REGEN_DELAY, delay - 1);
            return;
        }
        float poise = data.getFloat(TAG_POISE);
        if (poise >= MAX_POISE) return;
        data.putFloat(TAG_POISE, Math.min(MAX_POISE, poise + POISE_REGEN_PER_SECOND / 20.0F));
    }

    /** 水晶被摧毁时开启破绽期。通过数量变化判断，避免依赖额外的方块/实体事件。 */
    private static void tickCrystalOpening(ServerLevel level, EnderDragon dragon) {
        int current = countCrystals(dragon);
        int previous = dragon.getPersistentData().getInt(TAG_CRYSTAL_COUNT);
        if (current < previous) {
            dragon.getPersistentData().putInt(TAG_OPENING_TICKS, OPENING_TICKS);
            level.sendParticles(ParticleTypes.END_ROD,
                    dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.5D, dragon.getZ(),
                    40, 2.0D, 1.5D, 2.0D, 0.08D);
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                    SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        dragon.getPersistentData().putInt(TAG_CRYSTAL_COUNT, current);
    }

    /**
     * 晶体重塑：记住每颗水晶的位置，被摧毁后每 5 分钟重塑一颗。
     * 同一时间只会重塑一颗，其余缺失点以 {@link #CRYSTAL_QUEUED} 排队等待接棒。
     * 每 20 tick 结算一次，兼作再生点的扫描节拍。
     */
    private static void tickCrystalRegen(ServerLevel level, EnderDragon dragon) {
        if (dragon.tickCount % 20 != 0) return;

        CompoundTag data = dragon.getPersistentData();
        ListTag sites = data.getList(TAG_CRYSTAL_SITES, Tag.TAG_COMPOUND);
        ListTag pending = data.getList(TAG_CRYSTAL_PENDING, Tag.TAG_COMPOUND);
        List<EndCrystal> alive = level.getEntitiesOfClass(EndCrystal.class, CRYSTAL_AREA, Entity::isAlive);

        // 把新见到的水晶位置登记为再生锚点
        for (EndCrystal crystal : alive) {
            if (!containsPoint(sites, crystal.getX(), crystal.getY(), crystal.getZ())) {
                sites.add(spotTag(crystal.getX(), crystal.getY(), crystal.getZ()));
            }
        }
        if (sites.isEmpty()) {
            data.put(TAG_CRYSTAL_SITES, sites);
            return;
        }

        // 推进倒计时，归零的水晶重新生成
        ListTag stillPending = new ListTag();
        boolean respawned = false;
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            int delay = entry.getInt("Delay");
            // 排队中的重生点本轮不推进
            if (delay < 0) {
                stillPending.add(entry);
                continue;
            }
            delay -= 20;
            if (delay > 0) {
                entry.putInt("Delay", delay);
                stillPending.add(entry);
                continue;
            }
            double x = entry.getDouble("X");
            double y = entry.getDouble("Y");
            double z = entry.getDouble("Z");
            EndCrystal crystal = new EndCrystal(level, x, y, z);
            crystal.setShowBottom(true);
            level.addFreshEntity(crystal);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 30, 0.6D, 0.6D, 0.6D, 0.05D);
            level.playSound(null, x, y, z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 2.0F, 1.0F);
            respawned = true;
        }

        // 缺失的水晶加入队列：暂无重塑中的水晶才从它开始计时，否则等待接棒
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag site = sites.getCompound(i);
            double x = site.getDouble("X");
            double y = site.getDouble("Y");
            double z = site.getDouble("Z");
            if (containsPoint(alive, x, y, z)) continue;
            if (containsEntry(stillPending, x, y, z)) continue;

            CompoundTag entry = spotTag(x, y, z);
            entry.putInt("Delay", CRYSTAL_QUEUED);
            stillPending.add(entry);
        }

        // 一颗重塑完成后（或当前无人重塑时）让最早排队的一颗开始 5 分钟倒计时
        if (respawned || !hasActivePending(stillPending)) {
            for (int i = 0; i < stillPending.size(); i++) {
                CompoundTag entry = stillPending.getCompound(i);
                if (entry.getInt("Delay") < 0) {
                    entry.putInt("Delay", CRYSTAL_RESPAWN_DELAY);
                    break;
                }
            }
        }

        data.put(TAG_CRYSTAL_SITES, sites);
        data.put(TAG_CRYSTAL_PENDING, stillPending);
    }

    private static boolean hasActivePending(ListTag pending) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.getCompound(i).getInt("Delay") >= 0) return true;
        }
        return false;
    }

    private static CompoundTag spotTag(double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        return tag;
    }

    private static boolean containsPoint(ListTag sites, double x, double y, double z) {
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag site = sites.getCompound(i);
            if (isSameSpot(site.getDouble("X"), site.getDouble("Y"), site.getDouble("Z"), x, y, z)) return true;
        }
        return false;
    }

    private static boolean containsPoint(List<EndCrystal> crystals, double x, double y, double z) {
        for (EndCrystal crystal : crystals) {
            if (isSameSpot(crystal.getX(), crystal.getY(), crystal.getZ(), x, y, z)) return true;
        }
        return false;
    }

    private static boolean containsEntry(ListTag pending, double x, double y, double z) {
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (isSameSpot(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"), x, y, z)) return true;
        }
        return false;
    }

    private static boolean isSameSpot(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.abs(ax - bx) < 0.75D && Math.abs(ay - by) < 0.75D && Math.abs(az - bz) < 0.75D;
    }

    /** 破绽期计时与提示粒子。 */
    private static void tickOpening(ServerLevel level, EnderDragon dragon) {
        int ticks = dragon.getPersistentData().getInt(TAG_OPENING_TICKS);
        if (ticks <= 0) return;
        dragon.getPersistentData().putInt(TAG_OPENING_TICKS, ticks - 1);
        if (ticks % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.5D, dragon.getZ(),
                    6, dragon.getBbWidth() * 0.6D, 1.0D, dragon.getBbWidth() * 0.6D, 0.02D);
        }
    }

    /** 停靠祭坛时在地面留下凋零龙息。 */
    private static void tickBreathCloud(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getInt(TAG_BREATH_COOLDOWN) > 0) return;
        if (!dragon.getPhaseManager().getCurrentPhase().isSitting()) return;

        dragon.getPersistentData().putInt(TAG_BREATH_COOLDOWN, BREATH_INTERVAL);
        AreaEffectCloud cloud = new AreaEffectCloud(level,
                dragon.getX(), dragon.getY() - 2.0D, dragon.getZ());
        cloud.setOwner(dragon);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(4.5F);
        cloud.setDuration(120);
        cloud.setWaitTime(10);
        cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
        level.addFreshEntity(cloud);
    }

    /** 生命首次低于 50% 时召唤末影人援军。 */
    private static void tickSummon(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getBoolean(TAG_SUMMONED)) return;
        if (dragon.getHealth() > dragon.getMaxHealth() * 0.5F) return;
        dragon.getPersistentData().putBoolean(TAG_SUMMONED, true);

        for (int i = 0; i < 3; i++) {
            EnderMan enderman = EntityType.ENDERMAN.create(level);
            if (enderman == null) continue;
            double x = level.random.nextInt(17) - 8;
            double z = level.random.nextInt(17) - 8;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
            enderman.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            enderman.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
            level.addFreshEntity(enderman);
        }
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    /** 晶核滋养：水晶越多回血越快；附近没有玩家时额外恢复。 */
    private static void tickRecovery(ServerLevel level, EnderDragon dragon) {
        if (dragon.tickCount % 40 != 0) return;
        if (dragon.getHealth() >= dragon.getMaxHealth()) return;

        int crystals = Math.min(CRYSTAL_CAP, countCrystals(dragon));
        double ratio = BASE_REGEN + CRYSTAL_REGEN * crystals;
        boolean playerNearby = !level.getEntitiesOfClass(Player.class,
                dragon.getBoundingBox().inflate(24.0D), player -> !player.isSpectator()).isEmpty();
        if (!playerNearby) {
            ratio += SOLITUDE_REGEN;
        }
        dragon.heal((float) (dragon.getMaxHealth() * ratio));
    }

    /** 终末咆哮：周期性对近身玩家施加黑暗并击退。 */
    private static void tickRoar(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getInt(TAG_ROAR_COOLDOWN) > 0) return;

        var players = level.getEntitiesOfClass(Player.class,
                dragon.getBoundingBox().inflate(24.0D), player -> !player.isCreative() && !player.isSpectator());
        if (players.isEmpty()) return;

        dragon.getPersistentData().putInt(TAG_ROAR_COOLDOWN, ROAR_INTERVAL);
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, true, true));
            Vec3 push = player.position().subtract(dragon.position());
            Vec3 flat = new Vec3(push.x, 0.0D, push.z);
            if (flat.lengthSqr() > 0.01D) {
                flat = flat.normalize().scale(1.1D);
                player.push(flat.x, 0.6D, flat.z);
            }
        }
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 0.9F);
    }

    /** 空中阶段周期性向最近玩家发射三发散射龙火球。 */
    private static void tickFireballs(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getInt(TAG_FIREBALL_COOLDOWN) > 0) return;
        if (dragon.getPhaseManager().getCurrentPhase().isSitting()) return;

        Player target = nearestTarget(level, dragon, 56.0D);
        if (target == null) return;

        dragon.getPersistentData().putInt(TAG_FIREBALL_COOLDOWN, FIREBALL_INTERVAL);
        Vec3 base = target.getEyePosition().subtract(dragon.getEyePosition()).normalize();
        for (int i = -1; i <= 1; i++) {
            Vec3 dir = new Vec3(
                    base.x + i * 0.06D + level.random.nextDouble() * 0.02D,
                    base.y + level.random.nextDouble() * 0.04D,
                    base.z + i * 0.06D).normalize();
            shootFireball(level, dragon, dir);
        }
    }

    /** 末影回响：玩家投出末影珍珠后，龙朝珍珠落点回敬一发火球。 */
    private static void tickPearlResponse(ServerLevel level, EnderDragon dragon) {
        if (dragon.getPersistentData().getInt(TAG_PEARL_COOLDOWN) > 0) return;

        ThrownEnderpearl pearl = level.getEntitiesOfClass(ThrownEnderpearl.class,
                        dragon.getBoundingBox().inflate(64.0D),
                        candidate -> candidate.isAlive() && candidate.getOwner() instanceof Player)
                .stream()
                .findFirst()
                .orElse(null);
        if (pearl == null) return;

        dragon.getPersistentData().putInt(TAG_PEARL_COOLDOWN, PEARL_INTERVAL);
        Vec3 aim = pearl.position().subtract(dragon.getEyePosition());
        if (aim.lengthSqr() < 0.01D) return;
        shootFireball(level, dragon, aim.normalize());
        level.sendParticles(ParticleTypes.PORTAL,
                pearl.getX(), pearl.getY(), pearl.getZ(), 30, 0.4D, 0.4D, 0.4D, 0.1D);
    }

    private static void shootFireball(ServerLevel level, EnderDragon dragon, Vec3 direction) {
        DragonFireball fireball = new DragonFireball(level, dragon, direction);
        fireball.setPos(dragon.getX(), dragon.getY() + 1.0D, dragon.getZ());
        level.addFreshEntity(fireball);
    }

    private static Player nearestTarget(ServerLevel level, EnderDragon dragon, double range) {
        return level.getEntitiesOfClass(Player.class, dragon.getBoundingBox().inflate(range),
                        player -> !player.isCreative() && !player.isSpectator())
                .stream()
                .min((first, second) -> Double.compare(dragon.distanceToSqr(first), dragon.distanceToSqr(second)))
                .orElse(null);
    }

    private static int countCrystals(EnderDragon dragon) {
        return dragon.level().getEntitiesOfClass(EndCrystal.class, CRYSTAL_AREA, Entity::isAlive).size();
    }

    private static void tickCooldowns(EnderDragon dragon) {
        decrement(dragon, TAG_FIREBALL_COOLDOWN);
        decrement(dragon, TAG_BREATH_COOLDOWN);
        decrement(dragon, TAG_ROAR_COOLDOWN);
        decrement(dragon, TAG_PEARL_COOLDOWN);
    }

    private static void decrement(Entity entity, String key) {
        int cooldown = entity.getPersistentData().getInt(key);
        if (cooldown > 0) {
            entity.getPersistentData().putInt(key, cooldown - 1);
        }
    }
}
