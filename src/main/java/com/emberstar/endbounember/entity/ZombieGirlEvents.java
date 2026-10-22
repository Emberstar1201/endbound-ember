package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 丧尸娘的通用事件：属性注册、索敌保护、反击机制与增援机制。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public class ZombieGirlEvents {
    /** 增援触发后同一受保护对象的冷却时间（60 秒），避免连续刷兵。 */
    private static final long REINFORCE_COOLDOWN_TICKS = 1200L;
    /** 触发增援的血量阈值：受击后血量降至最大生命值的 85% 以下。 */
    private static final float REINFORCE_HEALTH_THRESHOLD = 0.85F;
    /** 增援队伍编制：4 名钻石剑战士 + 2 名投掷治疗药水的医疗兵。 */
    private static final int REINFORCE_FIGHTERS = 4;
    private static final int REINFORCE_HEALERS = 2;
    /** 每名医疗兵投掷的治疗药水数量与投掷间隔。 */
    private static final int HEAL_POTION_COUNT = 3;
    private static final long HEAL_THROW_INTERVAL = 40L;

    private static final Map<UUID, Long> REINFORCE_COOLDOWN = new HashMap<>();
    private static final List<HealTask> HEAL_TASKS = new ArrayList<>();

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ZOMBIE_GIRL.get(), createAttributes());
        event.put(ModEntities.DROWNED_GIRL.get(), createAttributes());
        event.put(ModEntities.HUMAN_GIRL.get(), createAttributes());
    }

    /** 苦力怕发现少女后主动逃离；少女加入世界时随机化生命值（25~50）。 */
    @SubscribeEvent
    public static void onMobJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Creeper creeper) {
            addCreeperEscapeGoal(creeper);
        }
        if (event.getEntity() instanceof ZombieGirl girl) {
            rollMaxHealth(girl);
        }
    }

    /** 三位少女的生命值在 25~50 之间随机；已随机过的个体（含治愈/感染转换的）不重复随机。 */
    private static void rollMaxHealth(ZombieGirl girl) {
        if (girl.getPersistentData().getBoolean(ZombieGirl.HEALTH_ROLLED_TAG)) return;
        AttributeInstance maxHealth = girl.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(25.0D + girl.getRandom().nextInt(26));
            girl.setHealth(girl.getMaxHealth());
        }
        girl.getPersistentData().putBoolean(ZombieGirl.HEALTH_ROLLED_TAG, true);
    }

    private static void addCreeperEscapeGoal(Creeper creeper) {
        if (creeper.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof CreeperAvoidZombieGirlGoal)) return;
        creeper.goalSelector.addGoal(1, new CreeperAvoidZombieGirlGoal(creeper));
    }

    private static final class CreeperAvoidZombieGirlGoal extends AvoidEntityGoal<ZombieGirl> {
        private CreeperAvoidZombieGirlGoal(Creeper creeper) {
            super(creeper, ZombieGirl.class, 10.0F, 1.15D, 1.35D);
        }
    }

    /** Phantom 的专用飞控不读取通用 MoveControl 目标点，因此在原版 tick 后修正其速度方向。 */
    @SubscribeEvent
    public static void onPhantomTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Phantom phantom) || phantom.level().isClientSide()) return;
        ZombieGirl girl = phantom.level().getEntitiesOfClass(ZombieGirl.class,
                phantom.getBoundingBox().inflate(12.0D), LivingEntity::isAlive).stream()
                .min((left, right) -> Double.compare(phantom.distanceToSqr(left), phantom.distanceToSqr(right)))
                .orElse(null);
        if (girl == null) return;
        Vec3 away = phantom.position().subtract(girl.position());
        if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
        away = away.normalize();
        Vec3 current = phantom.getDeltaMovement();
        phantom.setDeltaMovement(current.add(away.scale(0.12D)).scale(0.92D));
    }

    /**
     * 铁傀儡不是 Enemy，因此不能用 Enemy 类型判断；直接拦截铁傀儡选中两种实体。
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        // 所有少女共用 ZombieGirl 基类，禁止三类少女把彼此设为攻击目标。
        if (event.getEntity() instanceof ZombieGirl
                && event.getNewAboutToBeSetTarget() instanceof ZombieGirl) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof IronGolem
                && event.getNewAboutToBeSetTarget() instanceof ZombieGirl) {
            event.setCanceled(true);
        }
    }

    /**
     * 目标事件遗漏或铁傀儡已经锁定目标时，额外取消铁傀儡造成的伤害。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        // 三类少女都继承 ZombieGirl；阻止任何少女来源的伤害，涵盖近战和投射物（伤害实体与直接实体）。
        if (event.getEntity() instanceof ZombieGirl
                && (event.getSource().getEntity() instanceof ZombieGirl
                || event.getSource().getDirectEntity() instanceof ZombieGirl)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ZombieGirl
                && event.getSource().getEntity() instanceof IronGolem) {
            event.setCanceled(true);
        }
    }

    /**
     * 助战好感度：少女击杀敌对生物时增加好感度，无论是否已经驯服。
     * 击杀判定只在服务端生效，每日上限由实体自身的每日额度统一限制。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ZombieGirl girl) {
            girl.recordCombatAffection();
        }
    }

    /**
     * 反击机制与增援机制的统一入口（伤害结算后触发）：
     * 1) 反击：少女无论野生或驯服，被生物攻击后立即把攻击者设为目标；
     *    判定不限亡灵，因此幻魔者召唤的恼鬼等非亡灵生物也会被反击；
     * 2) 增援：玩家或少女被生物打到 85% 血量以下时，以受伤者为中心生成
     *    4 名钻石剑少女，并由 2 名医疗兵投掷喷溅型治疗药水。
     */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !attacker.isAlive() || !isHostileAttacker(attacker)) return;

        // 反击机制：玩家、同类与友好白名单不构成攻击者，已在 isHostileAttacker 中排除
        if (victim instanceof ZombieGirl girl) {
            girl.setTarget(attacker);
        }

        // 增援机制：受击后血量降至 85% 以下才触发（Post 事件伤害已结算，直接读取当前血量）
        if (victim.getHealth() >= victim.getMaxHealth() * REINFORCE_HEALTH_THRESHOLD) return;
        if (!(victim instanceof Player) && !(victim instanceof ZombieGirl)) return;
        tryTriggerReinforcement((ServerLevel) victim.level(), victim, attacker);
    }

    /** 反击与增援只针对敌对生物；玩家、同类、友好白名单及玩家所属实体（如驯服狼、铁傀儡）不触发。 */
    private static boolean isHostileAttacker(LivingEntity attacker) {
        if (attacker instanceof Player || attacker instanceof ZombieGirl) return false;
        if (ZombieGirl.isFriendlySoul(attacker.getType()) || ZombieGirl.isProtectedMaid(attacker.getType())) return false;
        // 玩家召唤/驯服的实体（狼、铁傀儡、雪傀儡等）攻击少女时不触发增援
        if (attacker instanceof net.minecraft.world.entity.OwnableEntity ownable) {
            if (ownable.getOwner() instanceof Player) return false;
        }
        return true;
    }

    private static void tryTriggerReinforcement(ServerLevel level, LivingEntity victim, LivingEntity attacker) {
        // 荒野中没有玩家的少女战斗不呼叫增援，避免无意义刷兵
        if (!(victim instanceof Player) && level.getNearestPlayer(victim, 32.0D) == null) return;
        long now = level.getGameTime();
        Long last = REINFORCE_COOLDOWN.get(victim.getUUID());
        if (last != null && now - last < REINFORCE_COOLDOWN_TICKS) return;
        REINFORCE_COOLDOWN.put(victim.getUUID(), now);
        if (REINFORCE_COOLDOWN.size() > 512) {
            REINFORCE_COOLDOWN.entrySet().removeIf(entry -> now - entry.getValue() >= REINFORCE_COOLDOWN_TICKS);
        }

        for (int i = 0; i < REINFORCE_FIGHTERS; i++) {
            ZombieGirl fighter = spawnReinforcementGirl(level, victim);
            if (fighter != null) {
                fighter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                fighter.setTarget(attacker);
            }
        }
        for (int i = 0; i < REINFORCE_HEALERS; i++) {
            ZombieGirl healer = spawnReinforcementGirl(level, victim);
            if (healer != null) {
                HEAL_TASKS.add(new HealTask(level, healer.getUUID(), victim.getUUID(),
                        now + 20L, HEAL_POTION_COUNT));
            }
        }
    }

    /** 在受保护对象附近 3~8 格内寻找可站立位置生成一名增援少女。 */
    @Nullable
    private static ZombieGirl spawnReinforcementGirl(ServerLevel level, LivingEntity center) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + level.random.nextDouble() * 5.0D;
            BlockPos origin = BlockPos.containing(
                    center.getX() + Math.cos(angle) * distance,
                    center.getY() + 1.0D,
                    center.getZ() + Math.sin(angle) * distance);
            BlockPos spawnPos = findStandablePos(level, origin);
            if (spawnPos == null) continue;
            ZombieGirl girl = ModEntities.ZOMBIE_GIRL.get().create(level);
            if (girl == null) return null;
            girl.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(girl)) continue;
            girl.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
            level.addFreshEntity(girl);
            return girl;
        }
        return null;
    }

    /** 从候选位置向下最多探 3 格，寻找有实体地面、头顶无遮挡的可站立位置。 */
    @Nullable
    private static BlockPos findStandablePos(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (level.getBlockState(candidate.below()).blocksMotion()
                    && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    /** 医疗兵按间隔向受伤对象投掷喷溅型治疗药水。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HEAL_TASKS.isEmpty()) return;
        List<HealTask> pending = new ArrayList<>();
        for (Iterator<HealTask> iterator = HEAL_TASKS.iterator(); iterator.hasNext();) {
            HealTask task = iterator.next();
            if (task.level.getGameTime() < task.nextThrow) continue;
            iterator.remove();
            throwHealingPotion(task);
            if (task.remaining > 0) {
                task.nextThrow += HEAL_THROW_INTERVAL;
                pending.add(task);
            }
        }
        HEAL_TASKS.addAll(pending);
    }

    private static void throwHealingPotion(HealTask task) {
        Entity healerEntity = task.level.getEntity(task.healer);
        Entity patientEntity = task.level.getEntity(task.patient);
        if (!(healerEntity instanceof ZombieGirl healer) || !healer.isAlive()
                || !(patientEntity instanceof LivingEntity patient) || !patient.isAlive()) {
            task.remaining = 0;
            return;
        }
        task.remaining--;
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HEALING));
        ThrownPotion potion = new ThrownPotion(task.level, healer);
        potion.setItem(stack);
        double dx = patient.getX() - healer.getX();
        double dz = patient.getZ() - healer.getZ();
        double dy = patient.getEyeY() - healer.getEyeY();
        double distance = Math.sqrt(dx * dx + dz * dz);
        potion.shoot(dx, dy + distance * 0.2D, dz, 0.75F, 8.0F);
        task.level.addFreshEntity(potion);
    }

    /** 一次治疗增援任务：医疗兵向受伤对象投掷数瓶喷溅型治疗药水。 */
    private static final class HealTask {
        private final ServerLevel level;
        private final UUID healer;
        private final UUID patient;
        private long nextThrow;
        private int remaining;

        private HealTask(ServerLevel level, UUID healer, UUID patient, long nextThrow, int remaining) {
            this.level = level;
            this.healer = healer;
            this.patient = patient;
            this.nextThrow = nextThrow;
            this.remaining = remaining;
        }
    }

    private static net.minecraft.world.entity.ai.attributes.AttributeSupplier createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.5D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D * 1.15D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .build();
    }
}
