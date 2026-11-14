package com.emberstar.endbounember.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 钢筋铁骨（原「飞轮效应」）：内置能力，默认按键 R（可在按键绑定中修改）。
 * 朝准心方向高速突进，期间免疫伤害并可穿过生物；撞墙会提前停下。
 * 强化点：扫掠命中、动能余速、动能冲击、动能释放。
 * 充能上限 3 层（需完成「绝处逢生」解锁，未解锁时为 1 层），每 5 秒恢复 1 层。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FlywheelDash {
    private static final String TAG_ROOT = "FlywheelData";
    private static final String TAG_CHARGES = "charges";
    private static final String TAG_RECHARGE_START = "rechargeStart";

    /** 冲刺距离（格）。 */
    private static final double DASH_DISTANCE = 8.0D;
    /** 完成「绝处逢生」后的充能上限：可连续使用三次。 */
    private static final int MAX_CHARGES = 3;
    /** 未完成「绝处逢生」时的充能上限：单次使用。 */
    private static final int BASE_CHARGES = 1;
    /** 每 5 秒恢复 1 层充能。 */
    private static final int CHARGE_RECHARGE_TICKS = 100;
    /** 「绝处逢生」：BOSS 血量占比不高于该值时被冲击终结才算达成。 */
    private static final double EXECUTE_HEALTH_RATIO = 0.10D;
    /** 「绝处逢生」：生命上限达到该值的生物视作 BOSS（覆盖模组 BOSS）。 */
    private static final double BOSS_MAX_HEALTH = 150.0D;

    /** 每 tick 移动距离。 */
    private static final double DASH_SPEED = 1.5D;
    /** 最短冲刺时长，保证手感。 */
    private static final int MIN_DASH_TICKS = 4;
    /** 视线垂直分量上限，避免一路飞上天空。 */
    private static final double MAX_VERTICAL = 0.5D;

    /** 动能余速：冲刺结束后的滑行 tick 数。 */
    private static final int COAST_TICKS = 8;
    /** 动能余速：滑行初始速度相对冲刺速度的比例。 */
    private static final double COAST_SPEED_RATIO = 0.55D;
    /** 冲刺收尾的额外无敌帧。 */
    private static final int END_INVULNERABLE_TICKS = 5;

    /** 动能冲击：伤害区间（越早命中越高）。 */
    private static final double IMPACT_MIN_DAMAGE = 2.0D;
    private static final double IMPACT_MAX_DAMAGE = 4.0D;
    /** 动能冲击：额外击退强度。 */
    private static final double IMPACT_KNOCKBACK = 0.75D;
    /** 动能冲击：短暂减速时长（tick）。 */
    private static final int IMPACT_SLOWNESS_TICKS = 40;
    /** 动能冲击：多目标逐个衰减系数与下限。 */
    private static final double IMPACT_DECAY_PER_TARGET = 0.25D;
    private static final double IMPACT_DECAY_MIN = 0.35D;

    /** 动能释放：冲刺结束后的近战增伤窗口（tick）。 */
    private static final int RELEASE_WINDOW_TICKS = 40;
    /** 动能释放：首次近战伤害倍率。 */
    private static final float RELEASE_DAMAGE_MULTIPLIER = 1.5F;
    /** 动能释放：额外击退强度。 */
    private static final double RELEASE_KNOCKBACK = 0.8D;

    private static final Map<UUID, DashState> DASHING = new HashMap<>();
    /** 动能释放窗口剩余 tick，0/缺失表示不可用。 */
    private static final Map<UUID, Integer> RELEASE_WINDOWS = new HashMap<>();

    private FlywheelDash() {
    }

    /** 单次冲刺的运行时状态。 */
    private static final class DashState {
        private final Vec3 direction;
        private final int totalTicks;
        private final boolean wasInvulnerable;
        private final Set<UUID> hitEntities = new HashSet<>();
        private int ticksElapsed;
        private int coastTicks;
        private int endInvulnerableTicks;
        private boolean ending;

        private DashState(Vec3 direction, int totalTicks, boolean wasInvulnerable) {
            this.direction = direction;
            this.totalTicks = totalTicks;
            this.wasInvulnerable = wasInvulnerable;
        }
    }

    /** 客户端按键请求入口：校验通过后消耗一层充能开始冲刺。 */
    public static void tryDash(ServerPlayer player) {
        if (!Config.FLYWHEEL_DASH_ENABLED.get()) return;
        if (player.isPassenger()) return;

        int charges = chargeCount(player);
        if (charges <= 0) {
            player.displayClientMessage(
                    Component.translatable("endbound_ember.flywheel_effect.cooldown_active")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 满层时按下第一次冲刺才开始计算充能，避免空闲时白白消耗计时
        if (charges >= maxCharges(player)) setRechargeStart(player, player.level().getGameTime());
        setChargeCount(player, charges - 1);

        Vec3 direction = dashDirection(player);
        int totalTicks = Math.max(MIN_DASH_TICKS,
                (int) Math.ceil(DASH_DISTANCE / DASH_SPEED));

        DashState state = new DashState(direction, totalTicks, player.isInvulnerable());
        DASHING.put(player.getUUID(), state);

        applyMotion(player, direction.scale(DASH_SPEED));
        spawnBurstParticles(player.serverLevel(), player);
    }

    /**
     * 计算冲刺方向：水平方向归一为满速，垂直分量单独限制在 ±MAX_VERTICAL，
     * 避免抬头/低头时实际位移速度不满。
     */
    private static Vec3 dashDirection(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        Vec3 horizontal = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        double y = Mth.clamp(look.y, -MAX_VERTICAL, MAX_VERTICAL);
        return horizontal.add(0.0D, y, 0.0D);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!player.isAlive()) {
            DASHING.remove(player.getUUID());
            RELEASE_WINDOWS.remove(player.getUUID());
            return;
        }

        DashState state = DASHING.get(player.getUUID());
        if (state != null) updateDash(player, state);
        tickReleaseWindow(player);
        tickRecharge(player);
    }

    private static void updateDash(ServerPlayer player, DashState state) {
        if (state.ending) {
            state.endInvulnerableTicks++;
            player.setInvulnerable(true);
            if (state.endInvulnerableTicks >= END_INVULNERABLE_TICKS) {
                stopDash(player, state);
            }
            return;
        }

        // 冲刺位移结束，进入动能余速滑行
        if (state.ticksElapsed >= state.totalTicks) {
            updateCoast(player, state);
            return;
        }

        state.ticksElapsed++;

        if (isInsideWall(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            player.setInvulnerable(false);
            player.hurt(player.damageSources().generic(), 1.0F);
            player.setInvulnerable(true);
            beginEnding(player, state);
            return;
        }

        Vec3 movement = state.direction.scale(DASH_SPEED);
        applyMotion(player, movement);
        damageCollidedEntities(player, state, movement);
        spawnDashParticles(player.serverLevel(), player, state);

        if (state.ticksElapsed % 4 == 0) {
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS,
                    0.6F, 1.0F + level.random.nextFloat() * 0.3F);
        }
    }

    /** 动能余速：冲刺结束后保留一部分速度并逐步衰减，便于衔接跳跃与墙跳。 */
    private static void updateCoast(ServerPlayer player, DashState state) {
        if (isInsideWall(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            beginEnding(player, state);
            return;
        }

        state.coastTicks++;
        if (state.coastTicks > COAST_TICKS) {
            beginEnding(player, state);
            return;
        }

        double ratio = COAST_SPEED_RATIO * (1.0D - (double) state.coastTicks / COAST_TICKS);
        Vec3 movement = state.direction.scale(DASH_SPEED * ratio);
        // 向下滑行时限制垂直速度，避免直接砸进地面
        if (movement.y < -0.2D) {
            movement = new Vec3(movement.x, -0.2D, movement.z);
        }
        applyMotion(player, movement);
        damageCollidedEntities(player, state, movement);
    }

    /**
     * 扫掠命中：沿本 tick 的位移路径采样检测，避免高速移动直接穿过生物导致漏判。
     */
    private static void damageCollidedEntities(ServerPlayer player, DashState state, Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-6D) return;

        Vec3 start = player.position();
        Vec3 end = start.add(movement);
        AABB area = new AABB(start, end).inflate(1.5D);

        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !state.hitEntities.contains(entity.getUUID()))) {
            if (!sweepsPast(start, end, target)) continue;

            float damage = impactDamage(state);
            double healthRatio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
            if (target.hurt(player.damageSources().fellOutOfWorld(), damage)) {
                // 沿冲刺方向击退并附加短暂减速
                Vec3 dir = state.direction;
                target.push(dir.x * IMPACT_KNOCKBACK, 0.28D, dir.z * IMPACT_KNOCKBACK);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        IMPACT_SLOWNESS_TICKS, 1), player);
                state.hitEntities.add(target.getUUID());

                if (!target.isAlive()) tryAwardLastStand(player, target, healthRatio);
            }
        }
    }

    /** 采样判断位移路径是否穿过目标包围盒。 */
    private static boolean sweepsPast(Vec3 start, Vec3 end, LivingEntity target) {
        AABB box = target.getBoundingBox().inflate(0.2D);
        Vec3 from = start.add(0.0D, 0.9D, 0.0D);
        Vec3 to = end.add(0.0D, 0.9D, 0.0D);
        int steps = Mth.clamp(Mth.ceil(from.distanceTo(to) / 0.4D), 2, 32);
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            if (box.distanceToSqr(point) < 0.36D) return true;
        }
        return false;
    }

    /** 动能冲击伤害：冲刺越早命中越高，多目标依次衰减。 */
    private static float impactDamage(DashState state) {
        double progress = Mth.clamp((double) state.ticksElapsed / Math.max(1, state.totalTicks), 0.0D, 1.0D);
        double base = Mth.lerp(1.0D - progress, IMPACT_MIN_DAMAGE, IMPACT_MAX_DAMAGE);
        double decay = Math.max(IMPACT_DECAY_MIN, 1.0D - IMPACT_DECAY_PER_TARGET * state.hitEntities.size());
        return (float) (base * decay);
    }

    /** 进入收尾阶段：保留短暂无敌，并开启动能释放窗口。 */
    private static void beginEnding(ServerPlayer player, DashState state) {
        state.ending = true;
        state.endInvulnerableTicks = 0;
        RELEASE_WINDOWS.put(player.getUUID(), RELEASE_WINDOW_TICKS);
        player.displayClientMessage(
                Component.translatable("endbound_ember.flywheel_effect.release_ready")
                        .withStyle(ChatFormatting.AQUA),
                true);
    }

    /** 动能释放：冲刺结束后的首次近战命中获得增伤与额外击退。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getEntity() != player) return;

        LivingEntity target = event.getEntity();
        if (target == player || target.level() != player.level()) return;

        Integer remaining = RELEASE_WINDOWS.get(player.getUUID());
        if (remaining == null || remaining <= 0) return;
        RELEASE_WINDOWS.remove(player.getUUID());

        event.setNewDamage(event.getNewDamage() * RELEASE_DAMAGE_MULTIPLIER);

        Vec3 look = player.getLookAngle();
        target.push(look.x * RELEASE_KNOCKBACK, 0.32D, look.z * RELEASE_KNOCKBACK);
        target.hurtMarked = true;

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                12, 0.4D, 0.4D, 0.4D, 0.2D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void tickReleaseWindow(ServerPlayer player) {
        Integer remaining = RELEASE_WINDOWS.get(player.getUUID());
        if (remaining == null) return;
        if (remaining <= 1) {
            RELEASE_WINDOWS.remove(player.getUUID());
        } else {
            RELEASE_WINDOWS.put(player.getUUID(), remaining - 1);
        }
    }

    private static void stopDash(ServerPlayer player, DashState state) {
        DASHING.remove(player.getUUID());
        if (!state.wasInvulnerable) player.setInvulnerable(false);

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                10, 0.3D, 0.3D, 0.3D, 0.05D);
    }

    private static void applyMotion(ServerPlayer player, Vec3 movement) {
        player.setDeltaMovement(movement);
        if (player.isSprinting()) {
            player.getFoodData().setExhaustion(Math.max(0.0F, player.getFoodData().getExhaustionLevel() - 0.1F));
        }
        player.setInvulnerable(true);
        // 告知客户端位置已被服务端修正，避免被判定为异常移动
        player.hurtMarked = true;
    }

    /** 脚、身体中心、头部任一位置被阻挡即视为撞墙。 */
    private static boolean isInsideWall(Player player) {
        BlockPos[] positions = {
                player.blockPosition(),
                BlockPos.containing(player.getX(), player.getY() + 0.9D, player.getZ()),
                BlockPos.containing(player.getX(), player.getY() + 1.6D, player.getZ())
        };
        for (BlockPos pos : positions) {
            BlockState state = player.level().getBlockState(pos);
            if (state.blocksMotion()) return true;
        }
        return false;
    }

    private static void spawnDashParticles(ServerLevel level, ServerPlayer player, DashState state) {
        double x = player.getX();
        double y = player.getY() + 0.8D;
        double z = player.getZ();

        double angle = state.ticksElapsed * 0.8D;
        double radius = 0.6D;

        level.sendParticles(ParticleTypes.END_ROD,
                x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                x + Math.cos(angle + Math.PI) * radius, y, z + Math.sin(angle + Math.PI) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnBurstParticles(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.1D);
    }

    /** 完成「绝处逢生」后充能上限提升到 3 层，否则保持单层。 */
    private static int maxCharges(ServerPlayer player) {
        return ModAdvancements.hasDone(player, ModAdvancements.LAST_STAND) ? MAX_CHARGES : BASE_CHARGES;
    }

    /** 充能层数与计时起点保存在玩家持久化数据中，跨维度与重生都能保留。 */
    private static int chargeCount(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        int max = maxCharges(player);
        return data.contains(TAG_CHARGES) ? Mth.clamp(data.getInt(TAG_CHARGES), 0, max) : max;
    }

    private static void setChargeCount(ServerPlayer player, int charges) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(TAG_CHARGES, Mth.clamp(charges, 0, maxCharges(player)));
        player.getPersistentData().put(TAG_ROOT, data);
    }

    private static long rechargeStart(Player player) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        return data.contains(TAG_RECHARGE_START) ? data.getLong(TAG_RECHARGE_START)
                : player.level().getGameTime();
    }

    private static void setRechargeStart(Player player, long time) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putLong(TAG_RECHARGE_START, time);
        player.getPersistentData().put(TAG_ROOT, data);
    }

    /** 每 5 秒恢复 1 层充能，恢复后提示当前层数。 */
    private static void tickRecharge(ServerPlayer player) {
        if (!Config.FLYWHEEL_DASH_ENABLED.get()) return;

        int max = maxCharges(player);
        int charges = chargeCount(player);
        if (charges >= max) return;

        long start = rechargeStart(player);
        long elapsed = player.level().getGameTime() - start;
        if (elapsed < CHARGE_RECHARGE_TICKS) return;

        int gained = (int) Math.min(max - charges, elapsed / CHARGE_RECHARGE_TICKS);
        int after = charges + gained;
        setChargeCount(player, after);
        setRechargeStart(player, start + (long) gained * CHARGE_RECHARGE_TICKS);

        if (after >= max) {
            player.displayClientMessage(
                    Component.translatable("endbound_ember.flywheel_effect.ready")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else {
            player.displayClientMessage(
                    Component.translatable("endbound_ember.flywheel_effect.charge", after, MAX_CHARGES)
                            .withStyle(ChatFormatting.AQUA),
                    true);
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 2.0F);
    }

    /** 「绝处逢生」：BOSS 残血时被钢筋铁骨的冲击终结。 */
    private static void tryAwardLastStand(ServerPlayer player, LivingEntity target, double healthRatio) {
        if (healthRatio > EXECUTE_HEALTH_RATIO || !isBoss(target)) return;
        ModAdvancements.award(player, ModAdvancements.LAST_STAND);
    }

    /** 原版 BOSS 或生命上限足够高的生物都算 BOSS，便于覆盖模组 BOSS。 */
    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden
                || entity.getMaxHealth() >= BOSS_MAX_HEALTH;
    }
}
