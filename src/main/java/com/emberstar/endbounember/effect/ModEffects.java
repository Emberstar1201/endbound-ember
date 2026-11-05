package com.emberstar.endbounember.effect;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.item.DawnAmuletItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 本模组的状态效果。 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, EndboundEmber.MODID);

    /** 判罚之雷：BOSS 战期间按难度间隔向持有者降下固定 5 点伤害的落雷 */
    public static final DeferredHolder<MobEffect, MobEffect> JUDGEMENT_THUNDER =
            MOB_EFFECTS.register("judgement_thunder", JudgementThunder::new);

    /** 时间变慢：仅作客户端秒针减速与状态提示的标记，本身不结算任何效果 */
    public static final DeferredHolder<MobEffect, MobEffect> TIME_SLOW =
            MOB_EFFECTS.register("time_slow", () -> new TimeMarker(0x9FE8FF));

    /** 时间暂停：同上，客户端据此把秒针完全停住 */
    public static final DeferredHolder<MobEffect, MobEffect> TIME_STOP =
            MOB_EFFECTS.register("time_stop", () -> new TimeMarker(0x6E7BFF));

    private ModEffects() {
    }

    /** 无实际结算的标记类效果，光环渲染读取它来决定秒针速度 */
    private static final class TimeMarker extends MobEffect {
        TimeMarker(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }

    public static final class JudgementThunder extends MobEffect {
        /** 落雷伤害：默认固定 5 点，可被 BOSS 词条覆写 */
        private static final float STRIKE_DAMAGE = 5.0F;
        /** 简单 / 普通 / 困难下的落雷间隔（tick） */
        private static final int EASY_INTERVAL = 200;
        private static final int NORMAL_INTERVAL = 150;
        private static final int HARD_INTERVAL = 40;
        /** 记录在每个玩家身上的落雷倒计时 */
        private static final String TIMER_TAG = "EndboundEmberJudgementThunder";
        /** BOSS 词条写入的间隔倍率、单次伤害与「被雷击后缓慢」标记 */
        public static final String INTERVAL_SCALE_TAG = "EndboundEmberThunderIntervalScale";
        public static final String DAMAGE_TAG = "EndboundEmberThunderDamage";
        public static final String SLOW_TAG = "EndboundEmberThunderSlow";
        /** 被雷击后的缓慢时长（tick） */
        private static final int SLOW_DURATION = 40;

        JudgementThunder() {
            super(MobEffectCategory.HARMFUL, 0xFFE96B);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) {
                return true;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return true;
            }
            int baseInterval = intervalFor(level.getDifficulty());
            if (baseInterval <= 0) {
                return true;
            }
            CompoundTag data = entity.getPersistentData();
            float scale = data.contains(INTERVAL_SCALE_TAG) ? data.getFloat(INTERVAL_SCALE_TAG) : 1.0F;
            int interval = Math.max(1, Math.round(baseInterval * scale));
            int timer = data.getInt(TIMER_TAG);
            if (timer <= 0) {
                // 进入战斗后的第一个间隔才开始落雷
                data.putInt(TIMER_TAG, interval);
            } else if (timer == 1) {
                data.putInt(TIMER_TAG, interval);
                strike(level, entity, data);
            } else {
                data.putInt(TIMER_TAG, timer - 1);
            }
            return true;
        }

        /** 和平难度不落雷 */
        private static int intervalFor(Difficulty difficulty) {
            return switch (difficulty) {
                case PEACEFUL -> 0;
                case EASY -> EASY_INTERVAL;
                case NORMAL -> NORMAL_INTERVAL;
                case HARD -> HARD_INTERVAL;
            };
        }

        private static void strike(ServerLevel level, LivingEntity entity, CompoundTag data) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(entity.getX(), entity.getY(), entity.getZ());
                // 只保留落雷的视觉与音效，伤害由下面单独结算
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            float damage = data.contains(DAMAGE_TAG) ? data.getFloat(DAMAGE_TAG) : STRIKE_DAMAGE;
            entity.hurt(level.damageSources().lightningBolt(), damage);
            // 雷霆君主：被雷击后短暂缓慢（晨曦护符可免除）
            if (data.getBoolean(SLOW_TAG) && !isAmuletWearer(entity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, 1));
            }
        }

        private static boolean isAmuletWearer(LivingEntity entity) {
            return entity instanceof Player player && DawnAmuletItem.isEquipped(player);
        }
    }
}
