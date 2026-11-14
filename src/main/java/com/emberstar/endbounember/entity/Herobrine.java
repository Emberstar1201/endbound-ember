package com.emberstar.endbounember.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.DoomPillarBlock;
import com.emberstar.endbounember.block.DoomPillarBlockEntity;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.emberstar.endbounember.effect.ModEffects;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.item.DawnAmuletItem;
import com.emberstar.endbounember.item.AnahitaFirstlightLiddellItem;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;
import com.emberstar.endbounember.mixin.RangedAttributeAccessor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义 Boss「Herobrine」。
 * <p>
 * 基础面板：1500 生命值、24 护甲值，人形（Steve 比例）模型。
 * 战斗方式按距离切换：贴近时改用近战挥击，拉开距离时改成连射弓箭。
 * <p>原版 {@code MAX_HEALTH} 属性上限是 1024，这里在属性注册时由
 * {@link #raiseMaxHealthCap()} 放开到 1500。
 */
public class Herobrine extends Monster implements RangedAttackMob {
    /** 生命上限（原版属性上限不足以容纳，见 raiseMaxHealthCap） */
    public static final double MAX_HEALTH = 1500.0D;
    /** 护甲值 */
    public static final double ARMOR = 24.0D;

    /** 基础减伤：40%，不减免虚空伤害 */
    private static final double BASE_DAMAGE_REDUCTION = 0.40D;
    /** 受击后的无敌帧（tick） */
    private static final int HURT_INVULNERABLE_TICKS = 60;
    /** 单次受击的伤害上限：不满足豁免条件时最多结算 150 点 */
    private static final float DAMAGE_CAP = 150.0F;
    /** 亡灵再生：每次回复的生命值 */
    private static final float REGEN_AMOUNT = 10.0F;
    /** 机制5：场上每存在一个亡灵精锐，再生量追加的点数、间隔缩短的 tick 数 */
    private static final float REGEN_BONUS_PER_MINION = 8.0F;
    private static final int REGEN_INTERVAL_PER_MINION = 2;
    /** 亡灵杀手：每级拖延的再生 tick 数，以及计入的上限等级 */
    private static final int SMITE_REGEN_DELAY_PER_LEVEL = 75;
    private static final int SMITE_MAX_LEVEL = 12;
    /** 通用受击抑制：每次被玩家命中拖延回血的 tick 数，与 60 tick 受击无敌配套 */
    private static final int HIT_REGEN_DELAY = 40;
    /** 回血暂停时长的累计上限，防止长时间叠出极端暂停 */
    private static final int REGEN_PAUSE_CAP = 600;
    /** 小怪回血加成的最大计入数量，封顶小怪乘区 */
    private static final int REGEN_MINION_CAP = 3;
    /** 呼唤亡灵：召唤间隔（tick），10 秒一波 */
    private static final int SUMMON_INTERVAL = 200;
    /** 判罚之雷 buff 的持续时间（tick）与刷新阈值 */
    private static final int THUNDER_DURATION = 12_000;
    private static final int THUNDER_REFRESH_THRESHOLD = 400;
    /** 召唤物的实体标签：和平难度下保留（见 MonsterPeacefulMixin） */
    public static final String BOSS_MINION_TAG = "endbound_ember_boss_minion";
    /** 千军之主：精锐死亡后留下亡魂区的标记 */
    private static final String SOUL_ZONE_TAG = "EndboundEmberSoulZone";
    /** 词条造成玩家治疗量下降时，记录在玩家身上的比例 */
    private static final String HEAL_REDUCTION_TAG = "EndboundEmberHealReduction";
    /** 箭雨统帅：中箭后无法疾跑的剩余 tick */
    private static final String NO_SPRINT_TAG = "EndboundEmberNoSprint";
    /** 中箭后的禁跑时长（tick） */
    private static final int NO_SPRINT_DURATION = 40;
    /** 疫病之主：凋零光环的间隔与持续时间（tick） */
    private static final int WITHER_AURA_INTERVAL = 120;
    private static final int WITHER_AURA_DURATION = 60;
    /** 虚影游魂：瞬移间隔（tick）、距离（格）与落点失明时长（tick） */
    private static final int TELEPORT_INTERVAL = 100;
    private static final double TELEPORT_DISTANCE = 8.0D;
    private static final int TELEPORT_BLIND_DURATION = 30;
    /** 词条切换的最小间隔（tick），避免血量在档位边界反复抖动 */
    private static final int TITLE_SWITCH_COOLDOWN = 40;
    /** 回退词条所需的滞回宽度（单位：档）：血量要低于「当前档位 - 0.2 档」才回退 */
    private static final float TITLE_REGRESS_HYSTERESIS = 0.2F;

    /** 预警类技能：两次施放之间的间隔（tick） */
    private static final int TELEGRAPH_INTERVAL = 120;
    /** 雷罚预警：预警圈持续时间（tick）与结算半径（格） */
    private static final int THUNDER_WARNING_TICKS = 30;
    private static final double THUNDER_WARNING_RADIUS = 2.6D;
    /** 下界流星：预警持续时间（tick，与流星实体共用）与结算半径（格） */
    private static final int METEOR_WARNING_TICKS = NetherMeteor.FALL_TICKS;
    private static final double METEOR_WARNING_RADIUS = 3.4D;
    /** 两类预警技能结算的伤害 */
    private static final float TELEGRAPH_THUNDER_DAMAGE = 5.0F;
    private static final float TELEGRAPH_METEOR_DAMAGE = 8.0F;
    /** 预警圈的重绘间隔（tick），用来控制粒子包的数量 */
    private static final int WARNING_REDRAW_INTERVAL = 2;

    /** 幻影分身：施放间隔、单次数量、同时存在上限、持续时长（tick）与单体生命 */
    private static final int CLONE_INTERVAL = 300;
    private static final int CLONE_COUNT = 3;
    private static final int CLONE_MAX = 5;
    private static final int CLONE_LIFETIME = 240;
    private static final double CLONE_HEALTH = 80.0D;
    /** 幻影分身的实体标签：用来区分本体与分身 */
    public static final String PHANTOM_TAG = "endbound_ember_herobrine_phantom";

    /** 第一阶段：常规词条轮换 */
    private static final int PHASE_ONE = 1;
    /** 第二阶段：亡灵末日，固定在专属词条并召出亡者之柱 */
    private static final int PHASE_TWO = 2;
    /** 二阶段开场的无敌帧（tick），给玩家一点反应时间 */
    private static final int PHASE_TWO_GRACE_TICKS = 60;
    /** 二阶段呼唤亡灵时场上精锐的上限，覆盖词条提供的召唤上限 */
    private static final int PHASE_TWO_SUMMON_CAP = 20;
    /** 亡者之柱：同时存活上限、补召间隔（tick）与召唤半径（格） */
    private static final int DOOM_PILLAR_COUNT = 5;
    private static final int DOOM_PILLAR_RESPAWN_INTERVAL = 200;
    private static final double DOOM_PILLAR_RADIUS = 7.5D;
    /** 只剩 1 根柱子时的分摊比例，每多存活 1 根按 {@link #DOOM_PILLAR_SHARE_STEP} 递增 */
    private static final float DOOM_PILLAR_SHARE = 0.6F;
    /** 每多一根存活柱子带来的分摊比例增量：1 根 60%、2 根 75%、3 根 90% */
    private static final float DOOM_PILLAR_SHARE_STEP = 0.15F;
    /** 指示线：间隔、持续时间与重绘间隔（tick） */
    private static final int PILLAR_HINT_INTERVAL = 240;
    private static final int PILLAR_HINT_DURATION = 12;
    private static final int PILLAR_BEAM_REDRAW = 12;
    /** 指示线的终点高度（格），对齐三格高柱子的顶部 */
    private static final double PILLAR_BEAM_HEIGHT = 2.7D;
    /** 不死执念锁血时给全场玩家的黑暗时长（tick） */
    private static final int LOCK_DARKNESS_DURATION = 100;
    /** 召唤精锐时给玩家挂虚弱的时长（tick） */
    private static final int SUMMON_WEAKNESS_DURATION = 60;
    /** 万灵之主夺走的装备与已被夺取的玩家，存档键 */
    private static final String PLUNDER_LOOT_TAG = "EndboundEmberPlunderLoot";
    private static final String PLUNDER_PLAYERS_TAG = "EndboundEmberPlunderPlayers";
    /** 死亡后向地下搜索箱子的最大深度（格） */
    private static final int CHEST_SEARCH_DEPTH = 8;
    /** 精妙背包模组命名空间：这些物品永远不夺走 */
    private static final String BACKPACK_NAMESPACE = "sophisticatedbackpacks";

    /** 近战判定半径（格）：目标在这个距离内改为近战 */
    private static final double MELEE_RANGE = 4.0D;
    /** 近战切远程的触发距离（格） */
    private static final double RANGED_ENTER_RANGE = 4.5D;
    /** 远程切回近战的距离（格），与上面留出滞回，避免贴边时来回切换 */
    private static final double MELEE_ENTER_RANGE = 3.5D;
    /** 近战出手间隔（tick） */
    private static final int MELEE_COOLDOWN = 20;
    /** 连射时每支箭的间隔（tick）：20 tick（1 秒）内正好射出 10 支 */
    private static final int BURST_INTERVAL = 2;
    /** 每支箭的基础伤害 */
    private static final double ARROW_DAMAGE = 4.5D;

    /** 远程走位：距离小于此值时主动后撤，避免被贴脸 */
    private static final double RANGED_KEEP_MIN = 7.0D;
    /** 远程走位：距离大于此值时主动逼近，避免站在射程外空放 */
    private static final double RANGED_KEEP_MAX = 20.0D;
    /** 远程走位的路径重算间隔（tick） */
    private static final int STRAFE_REPATH_INTERVAL = 10;
    /** 侧向绕圈换方向的 tick 区间 */
    private static final int STRAFE_FLIP_MIN = 40;
    private static final int STRAFE_FLIP_MAX = 80;

    /** 击败后掉落的经验总量 */
    private static final int TOTAL_EXPERIENCE = 150_000;
    /** 单颗经验球携带的经验：避免 15 万点经验被拆成两万多颗小球 */
    private static final int EXPERIENCE_ORB_SIZE = 500;
    /** 死亡动画：升空阶段的时长（tick），5 秒 */
    private static final int DEATH_ASCEND_TICKS = 100;
    /** 死亡动画：升空高度（格），自尸体位置向上 */
    private static final double DEATH_ASCEND_HEIGHT = 18.0D;
    /** 死亡动画：化为烟雾后继续弥漫、并把经验落完的时长（tick） */
    private static final int DEATH_DISSOLVE_TICKS = 80;
    /** 死亡动画总时长（tick） */
    private static final int DEATH_DURATION = DEATH_ASCEND_TICKS + DEATH_DISSOLVE_TICKS;

    /** 同步给客户端：当前是否处于远程（拉弓）姿态，用于渲染手臂动作 */
    private static final EntityDataAccessor<Boolean> DATA_RANGED_STANCE = SynchedEntityData
            .defineId(Herobrine.class, EntityDataSerializers.BOOLEAN);

    private static boolean maxHealthCapRaised;
    /** 死亡过程中已经发出的经验数量 */
    private int experienceDropped;
    /** 死亡瞬间的 y 坐标，用来计算浮空高度 */
    private double deathBaseY;

    /** 当前这轮连射还剩下的箭数 */
    private int burstShotsLeft;
    /** 距离下一发箭还需要等待的 tick */
    private int shotDelay;
    /** 连射结束后的真空期剩余 tick */
    private int restDelay;
    /** 亡灵再生被亡灵杀手拖延的剩余 tick */
    private int regenPauseTicks;
    /** 距离下一波呼唤亡灵还有多少 tick */
    private int summonCooldown;
    /** 词条轮换的档位总数：每损失 10% 最大生命值推进一档，正好轮完一圈（亡灵末日不参与轮换） */
    private static final int TITLE_STAGES = HerobrineTitle.MYRIAD_LORD.ordinal() + 1;

    /** 当前词条（头衔）与它的强化数值 */
    private HerobrineTitle title = HerobrineTitle.UNDEAD_LORD;
    private HerobrineTitle.Stats titleStats = HerobrineTitle.UNDEAD_LORD.stats();
    /** 已经消耗掉的血量档位（每档 = 10% 最大生命值），血量回升时会回退 */
    private int titleStage;
    /** 词条切换后的冷却，防止血量在档位边界来回抖动 */
    private int titleSwitchCooldown;
    /** 不死执念的锁血是否还没用掉 */
    private boolean lockAvailable;
    /** 本场战斗是否已经用过锁血：词条回退后不会重新发放 */
    private boolean lockUsed;
    /** 虚影游魂：距离下次瞬移还有多少 tick */
    private int teleportCooldown;

    /** 预警类技能：距离下次施放的 tick、当前生效中的预警、两种预警的交替开关 */
    private int telegraphCooldown;
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private boolean nextTelegraphMeteor;

    /** 幻影分身：距离下次施放的 tick */
    private int cloneCooldown;
    /** 分身自身的剩余存活 tick（本体不使用） */
    private int phantomLife;

    /** 战斗阶段：1 = 常规词条轮换，2 = 亡灵末日 */
    private int phase = PHASE_ONE;
    /** 二阶段召出的亡者之柱位置，按召唤顺序保存 */
    private final List<BlockPos> doomPillars = new ArrayList<>();
    /** 距离下次指示线的 tick */
    private int pillarHintCooldown = PILLAR_HINT_INTERVAL;
    /** 指示线剩余显示 tick */
    private int pillarHintTicks;
    /** 距离下一次亡者之柱补召的 tick */
    private int pillarRespawnCooldown = DOOM_PILLAR_RESPAWN_INTERVAL;

    /** 万灵之主夺走的玩家装备，击败后原样装进地下箱子 */
    private final List<ItemStack> plunderLoot = new ArrayList<>();
    /** 已经被夺走过装备的玩家，避免重复夺取 */
    private final Set<UUID> plunderedPlayers = new HashSet<>();

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.endbound_ember.herobrine"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    public Herobrine(EntityType<? extends Herobrine> entityType, Level level) {
        super(entityType, level);
        // 默认近战姿态，手持附魔钻石剑；远程时切换为弓
        this.setItemSlot(EquipmentSlot.MAINHAND, createMeleeSword());
        // 武器只作为外观，击败后不掉落
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.bossEvent.setDarkenScreen(false);
        this.refreshBossBarName();
        this.registerGoals();
    }

    /** 血条标题：本体名 + 当前头衔；彩蛋词条用金色区分 */
    private void refreshBossBarName() {
        Component base = this.getCustomName() != null
                ? this.getCustomName()
                : Component.translatable("entity.endbound_ember.herobrine");
        Component titleName = Component.translatable(this.title.translationKey());
        if (this.title.isComposite()) {
            titleName = titleName.copy().withStyle(ChatFormatting.GOLD);
        }
        this.bossEvent.setName(base.copy()
                .append(Component.literal("  "))
                .append(titleName));
    }

    /** 切换词条：刷新数值、移速与血条名 */
    public void setTitle(HerobrineTitle title) {
        this.title = title;
        this.titleStats = title.stats();
        // 锁血一场只发一次：词条回退再回来也不会重新可用
        this.lockAvailable = this.titleStats.lockHealth && !this.lockUsed;
        this.teleportCooldown = 0;
        this.getAttribute(Attributes.MOVEMENT_SPEED)
                .setBaseValue(0.3D * this.titleStats.moveSpeedScale);
        this.refreshBossBarName();
    }

    public HerobrineTitle getTitle() {
        return this.title;
    }

    /** 每场战斗固定从「亡灵领主」开场，之后按枚举顺序依次替换 */
    public void rollStartingTitle() {
        this.setStartingTitle(HerobrineTitle.UNDEAD_LORD);
    }

    /** 以给定词条作为起点：之后每损失 10% 最大生命值就按枚举顺序换成下一个 */
    public void setStartingTitle(HerobrineTitle title) {
        this.titleStage = title.ordinal();
        this.setTitle(title);
    }

    /**
     * 血量驱动词条轮换：以「每损失 10% 最大生命值」为一档，掉血推进、回血回退。
     * <p>回退留了 {@link #TITLE_REGRESS_HYSTERESIS} 档的滞回，并加了
     * {@link #TITLE_SWITCH_COOLDOWN} 的最小切换间隔，避免再生与伤害在档位边界来回抖动。</p>
     *
     * @param force 无视切换冷却，仅用于死亡瞬间补上最后一档词条
     */
    private void tickTitleByHealth(boolean force) {
        // 第二阶段固定在「亡灵末日」，不再随血量轮换
        if (this.phase >= PHASE_TWO) {
            if (this.title != HerobrineTitle.NECROTIC_DOOM) {
                this.setTitle(HerobrineTitle.NECROTIC_DOOM);
            }
            return;
        }
        if (this.titleSwitchCooldown > 0) {
            this.titleSwitchCooldown--;
        }
        float lostRatio = 1.0F - this.getHealth() / this.getMaxHealth();
        int stage = Math.min(TITLE_STAGES - 1, (int) Math.floor(lostRatio * TITLE_STAGES + 1.0E-4D));
        int target;
        if (stage > this.titleStage) {
            target = stage;
        } else if (stage < this.titleStage
                && lostRatio * TITLE_STAGES < this.titleStage - TITLE_REGRESS_HYSTERESIS) {
            target = stage;
        } else {
            return;
        }
        if (!force && this.titleSwitchCooldown > 0) {
            return;
        }
        this.titleStage = target;
        this.setTitle(HerobrineTitle.values()[target]);
        this.titleSwitchCooldown = TITLE_SWITCH_COOLDOWN;
        // 万灵之主登场：立刻夺走正在观战玩家的装备
        if (this.titleStats.plunderGear) {
            this.plunderAllViewers();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("EndboundEmberTitle", this.title.id());
        tag.putInt("EndboundEmberTitleStage", this.titleStage);
        tag.putBoolean("EndboundEmberLockUsed", this.lockUsed);
        tag.putInt("EndboundEmberPhantomLife", this.phantomLife);
        tag.putInt("EndboundEmberPhase", this.phase);
        tag.putInt("EndboundEmberPillarRespawnCooldown", this.pillarRespawnCooldown);
        ListTag pillarList = new ListTag();
        for (BlockPos pos : this.doomPillars) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            pillarList.add(entry);
        }
        tag.put("EndboundEmberDoomPillars", pillarList);
        ListTag plunderList = new ListTag();
        for (ItemStack stack : this.plunderLoot) {
            if (!stack.isEmpty()) {
                plunderList.add(stack.save(this.level().registryAccess()));
            }
        }
        tag.put(PLUNDER_LOOT_TAG, plunderList);
        ListTag plunderPlayers = new ListTag();
        for (UUID id : this.plunderedPlayers) {
            plunderPlayers.add(StringTag.valueOf(id.toString()));
        }
        tag.put(PLUNDER_PLAYERS_TAG, plunderPlayers);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("EndboundEmberTitleStage")) {
            this.titleStage = tag.getInt("EndboundEmberTitleStage");
        }
        this.lockUsed = tag.getBoolean("EndboundEmberLockUsed");
        if (tag.contains("EndboundEmberTitle")) {
            this.setTitle(HerobrineTitle.byId(tag.getString("EndboundEmberTitle")));
        }
        // setTitle 会按当前词条重置锁血，这里按本场是否用过重新计算
        this.lockAvailable = this.titleStats.lockHealth && !this.lockUsed;
        if (tag.contains("EndboundEmberPhantomLife")) {
            this.phantomLife = tag.getInt("EndboundEmberPhantomLife");
        }
        this.phase = tag.contains("EndboundEmberPhase") ? tag.getInt("EndboundEmberPhase") : PHASE_ONE;
        this.pillarRespawnCooldown = tag.contains("EndboundEmberPillarRespawnCooldown")
                ? tag.getInt("EndboundEmberPillarRespawnCooldown") : DOOM_PILLAR_RESPAWN_INTERVAL;
        this.doomPillars.clear();
        ListTag pillarList = tag.getList("EndboundEmberDoomPillars", Tag.TAG_COMPOUND);
        for (int i = 0; i < pillarList.size(); i++) {
            CompoundTag entry = pillarList.getCompound(i);
            this.doomPillars.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        // 分身读档时属性会回落到本体面板，这里压回分身的血量
        if (this.isPhantom()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CLONE_HEALTH);
            this.setHealth(Math.min(this.getHealth(), (float) CLONE_HEALTH));
        }
        this.plunderLoot.clear();
        ListTag plunderList = tag.getList(PLUNDER_LOOT_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < plunderList.size(); i++) {
            ItemStack.parse(this.level().registryAccess(), plunderList.getCompound(i))
                    .ifPresent(this.plunderLoot::add);
        }
        this.plunderedPlayers.clear();
        ListTag plunderPlayers = tag.getList(PLUNDER_PLAYERS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < plunderPlayers.size(); i++) {
            try {
                this.plunderedPlayers.add(UUID.fromString(plunderPlayers.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** 近战武器：带附魔光效的钻石剑（仅外观，不掉落） */
    private static ItemStack createMeleeSword() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return sword;
    }

    /** 属性表：1500 生命、24 护甲，其余沿用怪物基础值 */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    /** 放开原版生命值属性上限，使其能承载 1500 点生命 */
    public static void raiseMaxHealthCap() {
        if (maxHealthCapRaised) {
            return;
        }
        maxHealthCapRaised = true;
        ((RangedAttributeAccessor) (Object) Attributes.MAX_HEALTH.value()).endboundEmber$setMaxValue(MAX_HEALTH);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HerobrineAttackGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RANGED_STANCE, false);
    }

    /** 客户端渲染用：是否处于拉弓姿态 */
    public boolean isRangedStance() {
        return this.entityData.get(DATA_RANGED_STANCE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // 幻影分身只走自己的消散计时与射击，不推进词条、召唤、落雷等本体技能
        if (this.isPhantom()) {
            this.tickPhantomLife();
            if (!this.isRemoved()) {
                this.tickRangedCombat();
            }
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        // 死亡动画期间（分批发经验的那段时间）不再切换姿态，也不再射箭
        if (!this.isAlive()) {
            return;
        }
        this.tickTitleByHealth(false);
        this.tickPhantomReveal();
        this.tickRegeneration();
        this.tickJudgementThunder();
        this.tickSummonUndead();
        this.tickPhantomTeleport();
        this.tickTelegraphs();
        this.tickPhantomClones();
        this.tickDoomPillars();
        this.tickRangedCombat();
    }

    /** 按距离切换近战 / 远程姿态，并推进连射节奏（分身共用） */
    private void tickRangedCombat() {
        LivingEntity target = this.getTarget();
        double distanceSqr = target == null ? Double.MAX_VALUE : this.distanceToSqr(target);
        boolean ranged = this.isRangedStance()
                ? distanceSqr > MELEE_ENTER_RANGE * MELEE_ENTER_RANGE
                : distanceSqr > RANGED_ENTER_RANGE * RANGED_ENTER_RANGE;
        if (ranged != this.isRangedStance()) {
            this.entityData.set(DATA_RANGED_STANCE, ranged);
            // 姿态切换时同步手持物品：远程用弓，近战用附魔钻石剑
            this.setItemSlot(EquipmentSlot.MAINHAND,
                    ranged ? new ItemStack(Items.BOW) : createMeleeSword());
        }
        // 连射节奏由实体自身推进，不依赖 Goal 的 start/stop，
        // 避免 Goal 被重新启动时把连射进度清零，导致只剩零星单发。
        this.tickRangedAttack(target, ranged);
    }

    /**
     * 远程连射的节奏控制：以 {@link #BURST_INTERVAL} tick 一发的速度打满
     * {@link HerobrineTitle.Stats#burstSize} 支箭，随后进入
     * {@link HerobrineTitle.Stats#burstRest} tick 的真空期，再开始下一轮。
     */
    private void tickRangedAttack(@Nullable LivingEntity target, boolean ranged) {
        // 切换近战或丢失目标时中止当前连射，并把真空期一并清空，
        // 这样下次拉开距离拉弓时能立刻开火。
        if (!ranged || target == null || !target.isAlive()
                || this.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE) {
            this.burstShotsLeft = 0;
            this.shotDelay = 0;
            this.restDelay = 0;
            return;
        }
        if (this.shotDelay > 0) {
            this.shotDelay--;
        }
        if (this.restDelay > 0) {
            this.restDelay--;
        }
        if (this.shotDelay > 0) {
            return;
        }
        if (this.burstShotsLeft <= 0) {
            // 真空期未结束前不开火
            if (this.restDelay > 0) {
                return;
            }
            this.burstShotsLeft = this.titleStats.burstSize;
        }
        this.performRangedAttack(target, 1.0F);
        this.burstShotsLeft--;
        if (this.burstShotsLeft <= 0) {
            // 一轮打完，进入真空期
            this.restDelay = this.titleStats.burstRest;
            this.shotDelay = 0;
        } else {
            this.shotDelay = BURST_INTERVAL;
        }
    }

    /**
     * 基础减伤 40% + 词条追加减伤（默认 20%），并附带 60 tick 无敌帧。
     * <p>虚空伤害不受减免；亡灵杀手的伤害无视这层减伤，并按等级拖延亡灵再生。
     * 携带「不死执念」词条时，每场可以锁血 1 点一次并回复 25%。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        int smiteLevel = getSmiteLevel(source);
        boolean smite = smiteLevel > 0;
        boolean voidDamage = isVoidDamage(source);
        float finalAmount = amount;
        if (!voidDamage && !smite) {
            finalAmount = (float) (amount * (1.0D - totalDamageReduction()));
        }
        // 单次限伤：除非攻击者手持人权剑（含强化版）或佩戴晨曦护符，否则一次最多结算 150 点
        if (!ignoresDamageCap(source)) {
            finalAmount = Math.min(finalAmount, DAMAGE_CAP);
        }
        // 不死执念：这一击会致命时把血量锁在 1 点
        // 柱子分摊改到 LivingDamageEvent.Pre 结算，这里按当前存活柱子预估真正落到本体的伤害
        boolean locked = false;
        if (this.lockAvailable && !voidDamage) {
            float projected = this.projectDamageAfterPillars(finalAmount);
            if (this.getHealth() - projected <= 0.0F) {
                finalAmount = Math.max(0.0F, this.getHealth() - 1.0F);
                this.lockAvailable = false;
                this.lockUsed = true;
                locked = true;
            }
        }
        boolean damaged = super.hurt(source, finalAmount);
        if (damaged) {
            // Boss 自身受击后固定 60 tick 无敌帧；额外削减交由停滞穿透事件统一处理
            this.invulnerableTime = HURT_INVULNERABLE_TICKS;
            // 通用受击抑制：任何玩家命中都会短暂拖延回血，不再只有亡灵杀手能压住再生
            if (source.getEntity() instanceof Player) {
                this.regenPauseTicks = Math.min(REGEN_PAUSE_CAP, this.regenPauseTicks + HIT_REGEN_DELAY);
            }
            if (smite) {
                this.regenPauseTicks = Math.min(REGEN_PAUSE_CAP,
                        this.regenPauseTicks + SMITE_REGEN_DELAY_PER_LEVEL * Math.min(smiteLevel, SMITE_MAX_LEVEL));
            }
        }
        if (locked) {
            this.heal(this.getMaxHealth() * 0.25F);
            if (this.titleStats.darknessOnLock) {
                for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
                    applyPressure(player, MobEffects.DARKNESS, LOCK_DARKNESS_DURATION, 0);
                }
            }
        }
        return damaged;
    }

    /** 当前总减伤：基础 40% + 词条追加减伤 */
    private double totalDamageReduction() {
        return BASE_DAMAGE_REDUCTION + this.titleStats.damageReduction;
    }

    /** 虚空伤害不参与减伤 */
    private static boolean isVoidDamage(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(FreeWillEnchantments.VOID_SPARK);
    }

    /** 攻击者手持人权剑（含强化版）或佩戴晨曦护符时无视单次限伤 */
    private static boolean ignoresDamageCap(DamageSource source) {
        if (!(source.getEntity() instanceof Player attacker)) {
            return false;
        }
        return attacker.getMainHandItem().getItem() instanceof SwordOfTheFreeWillItem
                || attacker.getOffhandItem().getItem() instanceof SwordOfTheFreeWillItem
                || DawnAmuletItem.isEquipped(attacker)
                || AnahitaFirstlightLiddellItem.isEquipped(attacker);
    }

    /** 攻击者手持武器上的亡灵杀手等级 */
    private static int getSmiteLevel(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return 0;
        }
        return EnchantmentHelper.getEnchantmentLevel(
                attacker.level().holderLookup(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE),
                attacker);
    }

    /**
     * 机制4 亡灵再生：按词条间隔回复生命；任何玩家的命中都会短暂拖延回血，
     * 亡灵杀手命中后按等级大幅拖延。
     * <p>机制5：场上存活的亡灵精锐提供回复加成（每次 +8、间隔 -2 tick），最多计入 3 只。</p>
     */
    private void tickRegeneration() {
        if (this.regenPauseTicks > 0) {
            this.regenPauseTicks--;
            return;
        }
        int minions = Math.min(this.countNearbyElites(), REGEN_MINION_CAP);
        int interval = Math.max(1, this.titleStats.regenInterval - REGEN_INTERVAL_PER_MINION * minions);
        if (this.tickCount % interval == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(REGEN_AMOUNT + REGEN_BONUS_PER_MINION * minions);
        }
    }

    /** 统计附近存活亡灵精锐的数量，召唤上限与再生加成共用这一口径 */
    private int countNearbyElites() {
        return this.level().getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(24.0D),
                minion -> minion.isAlive() && minion.getTags().contains(BOSS_MINION_TAG)).size();
    }

    /**
     * 机制1 判罚之雷：BOSS 战期间为参与战斗的玩家维持判罚之雷 buff。
     * <p>「疫病之主」改为取消落雷、定期施加凋零；词条带来的间隔与伤害倍率
     * 通过玩家的持久化数据交给 {@link ModEffects.JudgementThunder} 读取。</p>
     */
    private void tickJudgementThunder() {
        boolean witherTick = this.tickCount % WITHER_AURA_INTERVAL == 0;
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            if (!player.isAlive()) {
                continue;
            }
            if (this.titleStats.thunderDisabled) {
                if (player.hasEffect(ModEffects.JUDGEMENT_THUNDER)) {
                    player.removeEffect(ModEffects.JUDGEMENT_THUNDER);
                }
                if (this.titleStats.witherAura && witherTick) {
                    applyPressure(player, MobEffects.WITHER, WITHER_AURA_DURATION, 0);
                }
            } else {
                // 词条可能因回血回退，参数每 tick 同步一次，避免沿用上一个头衔的落雷强度
                writeThunderPressure(player);
                MobEffectInstance current = player.getEffect(ModEffects.JUDGEMENT_THUNDER);
                if (current == null || current.getDuration() < THUNDER_REFRESH_THRESHOLD) {
                    player.addEffect(new MobEffectInstance(ModEffects.JUDGEMENT_THUNDER,
                            THUNDER_DURATION, 0, false, false, true));
                }
            }
            writeHealReduction(player);
        }
    }

    /** 把当前词条的落雷参数写到玩家身上，供判罚之雷效果读取 */
    private void writeThunderPressure(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putFloat(ModEffects.JudgementThunder.INTERVAL_SCALE_TAG, this.titleStats.thunderIntervalScale);
        data.putFloat(ModEffects.JudgementThunder.DAMAGE_TAG, this.titleStats.thunderDamage);
        data.putBoolean(ModEffects.JudgementThunder.SLOW_TAG, this.titleStats.slowOnThunder);
    }

    /** 把当前词条的治疗削减比例写到玩家身上，供治疗事件读取 */
    private void writeHealReduction(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (this.titleStats.healReduction > 0.0F) {
            data.putFloat(HEAL_REDUCTION_TAG, this.titleStats.healReduction);
        } else if (data.contains(HEAL_REDUCTION_TAG)) {
            data.remove(HEAL_REDUCTION_TAG);
        }
    }

    /** 给玩家施加词条压力；携带晨曦护符或本模组光环时完全免除 */
    private static void applyPressure(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        if (!player.isAlive() || isCurseImmune(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }

    /** 机制2 呼唤亡灵：每 10 秒原地召唤一批亡灵精锐 */
    private void tickSummonUndead() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            this.summonCooldown = 0;
            return;
        }
        if (this.summonCooldown > 0) {
            this.summonCooldown--;
            return;
        }
        this.summonCooldown = SUMMON_INTERVAL;
        int spawned = this.summonUndeadElites(target);
        // 血肉贪食：真的召出精锐的那一波才给玩家挂虚弱
        if (spawned > 0 && this.titleStats.weaknessOnSummon) {
            for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
                applyPressure(player, MobEffects.WEAKNESS, SUMMON_WEAKNESS_DURATION, 0);
            }
        }
    }

    /** 返回本波实际召唤出的数量 */
    private int summonUndeadElites(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return 0;
        }
        // 二阶段改用固定上限 20，避免词条叠加后场上精锐无限堆积
        int cap = this.phase >= PHASE_TWO ? PHASE_TWO_SUMMON_CAP : this.titleStats.summonCap;
        int nearby = this.countNearbyElites();
        if (nearby >= cap) {
            return 0;
        }
        int count = this.titleStats.summonMin
                + this.getRandom().nextInt(this.titleStats.summonMax - this.titleStats.summonMin + 1);
        // 单次召唤也不允许越过上限
        count = Math.min(count, cap - nearby);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            boolean skeleton = this.getRandom().nextBoolean();
            Monster minion = createElite(level, skeleton);
            if (minion == null) {
                continue;
            }
            spawnElite(level, minion, skeleton, target);
            spawned++;
        }
        level.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.HOSTILE, 1.6F, 0.6F);
        return spawned;
    }

    /** 僵尸变种 / 骷髅变种各取一种 */
    @Nullable
    private Monster createElite(ServerLevel level, boolean skeleton) {
        EntityType<? extends Monster> type;
        if (skeleton) {
            type = switch (this.getRandom().nextInt(3)) {
                case 0 -> EntityType.STRAY;
                case 1 -> EntityType.BOGGED;
                default -> EntityType.SKELETON;
            };
        } else {
            type = switch (this.getRandom().nextInt(3)) {
                case 0 -> EntityType.HUSK;
                case 1 -> EntityType.DROWNED;
                default -> EntityType.ZOMBIE;
            };
        }
        return type.create(level);
    }

    private void spawnElite(ServerLevel level, Monster minion, boolean skeleton, LivingEntity target) {
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 1.5D + this.getRandom().nextDouble() * 1.5D;
        minion.moveTo(this.getX() + Math.cos(angle) * radius, this.getY(),
                this.getZ() + Math.sin(angle) * radius,
                this.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(minion)) {
            minion.moveTo(this.getX(), this.getY(), this.getZ(), minion.getYRot(), 0.0F);
        }
        this.equipElite(minion, skeleton);
        minion.setPersistenceRequired();
        minion.addTag(BOSS_MINION_TAG);
        if (this.titleStats.soulZone) {
            minion.addTag(SOUL_ZONE_TAG);
        }
        minion.setTarget(target);
        level.addFreshEntity(minion);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                minion.getX(), minion.getY() + 0.6D, minion.getZ(), 12, 0.4D, 0.7D, 0.4D, 0.02D);
    }

    /** 虚影游魂：每 5 秒瞬移到目标周围，落点附近的玩家短暂失明 */
    private void tickPhantomTeleport() {
        if (!this.titleStats.teleport) {
            return;
        }
        if (++this.teleportCooldown < TELEPORT_INTERVAL) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            this.teleportCooldown = 0;
            return;
        }
        this.teleportCooldown = 0;
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        double x = target.getX() + Math.cos(angle) * TELEPORT_DISTANCE;
        double z = target.getZ() + Math.sin(angle) * TELEPORT_DISTANCE;
        double y = target.getY();
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(),
                30, 0.5D, 1.0D, 0.5D, 0.03D);
        if (!this.randomTeleport(x, y, z, true)) {
            return;
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0D, this.getZ(),
                40, 0.6D, 1.2D, 0.6D, 0.04D);
        level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.6F);
        if (this.titleStats.blindOnTeleport) {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    this.getBoundingBox().inflate(4.0D))) {
                applyPressure(player, MobEffects.BLINDNESS, TELEPORT_BLIND_DURATION, 0);
            }
        }
    }

    /**
     * 预警类技能：先在地面画出预警圈给玩家留出躲避时间，再结算落雷 / 下界流星。
     * <p>雷霆君主获得「雷罚预警」，不死执念获得「下界流星」，万灵之主两者兼有并交替施放。</p>
     */
    private void tickTelegraphs() {
        this.tickActiveTelegraphs();
        if (!this.titleStats.telegraphThunder && !this.titleStats.netherMeteor) {
            this.telegraphCooldown = 0;
            return;
        }
        if (this.telegraphCooldown > 0) {
            this.telegraphCooldown--;
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.telegraphCooldown = TELEGRAPH_INTERVAL;
        // 同时拥有两种预警时交替施放，避免只有落雷或只有流星
        boolean meteor = this.titleStats.netherMeteor
                && (!this.titleStats.telegraphThunder || this.nextTelegraphMeteor);
        this.nextTelegraphMeteor = !this.nextTelegraphMeteor;
        Vec3 pos = groundPosition(level, target.position());
        this.telegraphs.add(new Telegraph(pos, meteor,
                meteor ? METEOR_WARNING_TICKS : THUNDER_WARNING_TICKS));
        if (meteor) {
            // 落地前先放一颗真正会掉下来的岩块，给玩家明确的视觉预警
            level.addFreshEntity(new NetherMeteor(level, pos));
        }
        level.playSound(null, BlockPos.containing(pos), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.HOSTILE, 0.8F, 0.6F);
    }

    /** 推进已经起手的预警：重绘预警圈，时间到了就结算 */
    private void tickActiveTelegraphs() {
        if (this.telegraphs.isEmpty()) {
            return;
        }
        for (Iterator<Telegraph> it = this.telegraphs.iterator(); it.hasNext();) {
            Telegraph telegraph = it.next();
            telegraph.ticksLeft--;
            this.renderWarning(telegraph);
            if (telegraph.ticksLeft <= 0) {
                this.resolveTelegraph(telegraph);
                it.remove();
            }
        }
    }

    /** 地面预警圈：脚下画一圈灵魂火；坠落的岩块本身由 {@link NetherMeteor} 显示 */
    private void renderWarning(Telegraph telegraph) {
        if (!(this.level() instanceof ServerLevel level)
                || telegraph.ticksLeft % WARNING_REDRAW_INTERVAL != 0) {
            return;
        }
        Vec3 pos = telegraph.pos;
        double radius = telegraph.meteor ? METEOR_WARNING_RADIUS : THUNDER_WARNING_RADIUS;
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + Math.cos(angle) * radius, pos.y + 0.05D, pos.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.1D, pos.z,
                1, 0.1D, 0.05D, 0.1D, 0.0D);
    }

    /** 预警结束：雷罚直接落雷，下界流星砸出范围伤害、击退与火焰 */
    private void resolveTelegraph(Telegraph telegraph) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = telegraph.pos;
        double radius = telegraph.meteor ? METEOR_WARNING_RADIUS : THUNDER_WARNING_RADIUS;
        if (telegraph.meteor) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5D, pos.z,
                    2, 0.4D, 0.2D, 0.4D, 0.0D);
            level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5D, pos.z,
                    40, 1.2D, 0.6D, 1.2D, 0.06D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.8D, pos.z,
                    24, 1.0D, 0.7D, 1.0D, 0.03D);
            level.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE, 1.6F, 0.7F);
            igniteImpact(level, pos);
        } else {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(pos.x, pos.y, pos.z);
                // 只保留落雷的视觉与音效，伤害由下面单独结算
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        AABB area = new AABB(pos.x - radius, pos.y - 1.0D, pos.z - radius,
                pos.x + radius, pos.y + 2.5D, pos.z + radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (telegraph.meteor) {
                player.hurt(level.damageSources().explosion(this, this), TELEGRAPH_METEOR_DAMAGE);
                Vec3 away = player.position().subtract(pos);
                player.push(away.x * 0.6D, 0.45D, away.z * 0.6D);
            } else {
                player.hurt(level.damageSources().lightningBolt(), TELEGRAPH_THUNDER_DAMAGE);
            }
        }
    }

    /** 流星砸落后在地面留下几团火，是否破坏方块沿用 mobGriefing */
    private static void igniteImpact(ServerLevel level, Vec3 center) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) {
                    continue;
                }
                BlockPos firePos = origin.offset(dx, 0, dz);
                if (level.getBlockState(firePos).isAir()
                        && level.getBlockState(firePos.below()).isSolid()) {
                    level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                }
            }
        }
    }

    /** 把预警圈贴到目标脚下的地面：从目标位置向下找第一个可站立的方块 */
    private static Vec3 groundPosition(ServerLevel level, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos.x, pos.y + 0.5D, pos.z).mutable();
        for (int i = 0; i < 8; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()) {
                return new Vec3(cursor.getX() + 0.5D, cursor.getY() + 1.0D, cursor.getZ() + 0.5D);
            }
            if (cursor.getY() <= level.getMinBuildHeight()) {
                break;
            }
            cursor.move(0, -1, 0);
        }
        return new Vec3(pos.x, pos.y, pos.z);
    }

    private void tickPhantomReveal() {
        if (!this.titleStats.phantomClones) {
            return;
        }
        boolean phantomNearby = !this.level().getEntitiesOfClass(Herobrine.class,
                this.getBoundingBox().inflate(128.0D), Herobrine::isPhantom).isEmpty();
        if (phantomNearby) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
        }
    }

    /**
     * 幻影分身：虚影游魂词条下定期分裂出若干个持弓幻影，和本体一起向目标射击。
     * <p>幻影只继承了本体的射击逻辑，没有词条、召唤与掉落，血量耗尽或到期后自动消散。</p>
     */
    private void tickPhantomClones() {
        if (!this.titleStats.phantomClones) {
            this.cloneCooldown = 0;
            return;
        }
        if (this.cloneCooldown > 0) {
            this.cloneCooldown--;
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.cloneCooldown = CLONE_INTERVAL;
        int alive = level.getEntitiesOfClass(Herobrine.class, this.getBoundingBox().inflate(48.0D),
                Herobrine::isPhantom).size();
        int count = Math.min(CLONE_COUNT, CLONE_MAX - alive);
        for (int i = 0; i < count; i++) {
            this.spawnPhantomClone(level, target, i, count);
        }
    }

    /** 在目标周围环形位置召出幻影 */
    private void spawnPhantomClone(ServerLevel level, LivingEntity target, int index, int count) {
        Herobrine phantom = ModEntities.HEROBRINE.get().create(level);
        if (phantom == null) {
            return;
        }
        double angle = Math.PI * 2.0D * index / count + this.getRandom().nextDouble() * 0.5D;
        double radius = 5.0D + this.getRandom().nextDouble() * 3.0D;
        double x = target.getX() + Math.cos(angle) * radius;
        double z = target.getZ() + Math.sin(angle) * radius;
        phantom.moveTo(x, target.getY(), z, this.getRandom().nextFloat() * 360.0F, 0.0F);
        phantom.addTag(PHANTOM_TAG);
        phantom.setTarget(target);
        phantom.setPersistenceRequired();
        phantom.phantomLife = CLONE_LIFETIME;
        phantom.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CLONE_HEALTH);
        phantom.setHealth((float) CLONE_HEALTH);
        level.addFreshEntity(phantom);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, target.getY() + 1.0D, z,
                24, 0.4D, 1.0D, 0.4D, 0.03D);
        level.playSound(null, BlockPos.containing(x, target.getY(), z), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    /** 分身标记：带 {@link #PHANTOM_TAG} 的都是幻影，不参与本体的一切技能与掉落 */
    public boolean isPhantom() {
        return this.getTags().contains(PHANTOM_TAG);
    }

    /** 幻影分身的生命周期：只做消散计时，不推进词条与召唤 */
    private void tickPhantomLife() {
        if (this.phantomLife <= 0) {
            this.phantomLife = CLONE_LIFETIME;
        }
        if (--this.phantomLife <= 0) {
            this.poof();
            return;
        }
        if (this.level() instanceof ServerLevel level && this.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(),
                    3, 0.3D, 0.7D, 0.3D, 0.01D);
        }
    }

    /** 幻影消散：灵魂烟尘 + 音效后直接移除，不触发本体的死亡动画与战利品 */
    private void poof() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(),
                    30, 0.5D, 1.0D, 0.5D, 0.03D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                    14, 0.5D, 0.9D, 0.5D, 0.02D);
            level.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        this.discard();
    }

    /** 一次带预警的攻击：落点、剩余预警时间与类型 */
    private static final class Telegraph {
        private final Vec3 pos;
        private final boolean meteor;
        private int ticksLeft;

        private Telegraph(Vec3 pos, boolean meteor, int warningTicks) {
            this.pos = pos;
            this.meteor = meteor;
            this.ticksLeft = warningTicks;
        }
    }

    /** 「亡灵领主」头衔：精锐穿附魔钻石全套，手持钻石剑 / 高等级附魔弓 */
    private void equipElite(Monster minion, boolean skeleton) {
        HolderGetter<Enchantment> lookup = this.level().holderLookup(Registries.ENCHANTMENT);
        equipArmor(minion, EquipmentSlot.HEAD, Items.DIAMOND_HELMET, lookup);
        equipArmor(minion, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, lookup);
        equipArmor(minion, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, lookup);
        equipArmor(minion, EquipmentSlot.FEET, Items.DIAMOND_BOOTS, lookup);
        if (skeleton) {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(lookup.getOrThrow(Enchantments.POWER), 4);
            bow.enchant(lookup.getOrThrow(Enchantments.PUNCH), 2);
            minion.setItemSlot(EquipmentSlot.MAINHAND, bow);
        } else {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(lookup.getOrThrow(Enchantments.SHARPNESS), 3);
            minion.setItemSlot(EquipmentSlot.MAINHAND, sword);
        }
        minion.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static void equipArmor(Monster minion, EquipmentSlot slot, Item item,
            HolderGetter<Enchantment> lookup) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(lookup.getOrThrow(Enchantments.PROTECTION), 3);
        stack.enchant(lookup.getOrThrow(Enchantments.UNBREAKING), 2);
        minion.setItemSlot(slot, stack);
        minion.setDropChance(slot, 0.0F);
    }

    /** 和平难度下也保留本体，允许在和平模式开启 BOSS 战 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * 自定义死亡动画：不再沿用原版的倒地演示。
     * <p>第一阶段竖直升空 18 格，第二阶段在原地炸成烟雾（实体模型隐藏），
     * 经验在烟雾中分批落下，烟雾散尽时实体已被移除。
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.deathTime <= DEATH_ASCEND_TICKS) {
            this.ascendWhileDying(serverLevel);
        } else {
            this.dissolveIntoSmoke(serverLevel);
        }
        if (this.deathTime >= DEATH_DURATION && !this.isRemoved()) {
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    /** 第一阶段：竖直缓缓升空，脚下拖出灵魂火与灰烬 */
    private void ascendWhileDying(ServerLevel level) {
        double step = DEATH_ASCEND_HEIGHT / DEATH_ASCEND_TICKS;
        double targetY = this.deathBaseY + DEATH_ASCEND_HEIGHT;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.getX(), Math.min(targetY, this.getY() + step), this.getZ());
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 0.15D, this.getZ(), 4, 0.35D, 0.1D, 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.ASH,
                this.getX(), this.getY() + 0.05D, this.getZ(), 2, 0.4D, 0.1D, 0.4D, 0.01D);
    }

    /** 第二阶段：实体化成烟雾并隐藏模型，经验在烟雾中陆续落下 */
    private void dissolveIntoSmoke(ServerLevel level) {
        int elapsed = this.deathTime - DEATH_ASCEND_TICKS;
        double x = this.getX();
        double y = this.getY() + this.getBbHeight() * 0.5D;
        double z = this.getZ();
        if (elapsed == 1) {
            // 化烟瞬间：隐藏模型、清空手持武器，避免出现悬空的剑
            this.setInvisible(true);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 3, 0.6D, 0.5D, 0.6D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 120, 1.6D, 1.4D, 1.6D, 0.05D);
            level.sendParticles(ParticleTypes.SOUL, x, y, z, 80, 1.4D, 1.2D, 1.4D, 0.03D);
            level.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        // 烟雾持续弥漫，直到经验全部落完
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 10, 0.9D, 0.9D, 0.9D, 0.03D);
        level.sendParticles(ParticleTypes.SOUL, x, y, z, 4, 0.8D, 0.9D, 0.8D, 0.02D);
        this.dropExperience(level);
    }

    /** 把 15 万点经验按帧均匀撒下，每颗球携带 {@link #EXPERIENCE_ORB_SIZE} 点 */
    private void dropExperience(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        int remaining = TOTAL_EXPERIENCE - this.experienceDropped;
        int perTick = (TOTAL_EXPERIENCE + DEATH_DISSOLVE_TICKS - 1) / DEATH_DISSOLVE_TICKS;
        int amount = Math.min(perTick, remaining);
        // 升空位置可能卡在方块里（例如低矮洞穴），这种情况下把经验落在死亡原地
        double dropY = this.getY() + this.getBbHeight() * 0.5D;
        if (!level.getBlockState(BlockPos.containing(this.getX(), this.getY() + 0.5D, this.getZ())).isAir()) {
            dropY = this.deathBaseY + 0.5D;
        }
        while (amount > 0) {
            int chunk = Math.min(EXPERIENCE_ORB_SIZE, amount);
            amount -= chunk;
            this.experienceDropped += chunk;
            double ox = (this.getRandom().nextDouble() - 0.5D) * 1.2D;
            double oz = (this.getRandom().nextDouble() - 0.5D) * 1.2D;
            level.addFreshEntity(new ExperienceOrb(level, this.getX() + ox, dropY, this.getZ() + oz, chunk));
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        // 仪式召唤出来的 Boss 不自然消失
        this.setPersistenceRequired();
        // 每次生成固定从「亡灵领主」开场（读档时由 readAdditionalSaveData 覆盖）
        this.rollStartingTitle();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // 分身不参与血条、夺装备与判罚之雷，避免玩家看到第二根 BOSS 血条
        if (this.isPhantom()) {
            return;
        }
        this.bossEvent.addPlayer(player);
        // 万灵之主：开战瞬间夺走玩家的盔甲与饰品，收进本体的战利品清单
        if (this.isAlive() && this.titleStats.plunderGear && !this.plunderedPlayers.contains(player.getUUID())) {
            this.plunderPlayer(player);
        }
        // 进入 BOSS 战时给玩家挂上判罚之雷（疫病之主改为凋零光环，不挂雷）
        if (this.isAlive() && !this.titleStats.thunderDisabled) {
            writeThunderPressure(player);
            player.addEffect(new MobEffectInstance(ModEffects.JUDGEMENT_THUNDER,
                    THUNDER_DURATION, 0, false, false, true));
        }
    }

    /** 万灵之主登场：夺走所有正在观战、且还没被夺取过的玩家的装备 */
    private void plunderAllViewers() {
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            if (player.isAlive() && !this.plunderedPlayers.contains(player.getUUID())) {
                this.plunderPlayer(player);
            }
        }
    }

    /** 万灵之主：夺走玩家的盔甲与饰品，收进本体的战利品清单 */
    private void plunderPlayer(ServerPlayer player) {
        List<ItemStack> taken = new ArrayList<>();
        // 盔甲四件套
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            takeFromSlot(player, slot, taken, stack -> true);
        }
        // 饰品：主手、副手与背包里的光环 / 护符 / 星星
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            takeFromSlot(player, slot, taken, Herobrine::isAccessory);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isAccessory(stack) && !isPlunderProtected(stack)) {
                taken.add(stack.copy());
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        // Curios 饰品栏
        if (ModList.get().isLoaded("curios")) {
            taken.addAll(CuriosCompat.extractMatching(player,
                    stack -> isAccessory(stack) && !isPlunderProtected(stack)));
        }
        if (taken.isEmpty()) {
            return;
        }
        this.plunderLoot.addAll(taken);
        this.plunderedPlayers.add(player.getUUID());
        player.displayClientMessage(Component.translatable("entity.endbound_ember.herobrine.plunder")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    /** 从指定装备槽取走符合条件的物品，白名单与背包模组物品会被放过 */
    private static void takeFromSlot(ServerPlayer player, EquipmentSlot slot, List<ItemStack> taken,
            Predicate<ItemStack> filter) {
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty() || !filter.test(stack) || isPlunderProtected(stack)) {
            return;
        }
        taken.add(stack.copy());
        player.setItemSlot(slot, ItemStack.EMPTY);
    }

    /** 配置白名单中的物品，以及精妙背包模组的背包、两个光环、晨曦护符与生命之星，永远不会被夺走 */
    private static boolean isPlunderProtected(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        // 固定保护物品均在此拦截，避免被装备槽或背包夺取逻辑取走
        if (stack.is(EndboundEmber.STARDUST_WITCH_HALO.get())
                || stack.is(EndboundEmber.TIME_HALO.get())
                || stack.is(EndboundEmber.DAWN_AMULET.get())
                || stack.is(EndboundEmber.LIFE_STAR.get())) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals("sophisticatedbackpacks")) {
            return true;
        }
        for (String protectedId : Config.HEROBRINE_PLUNDER_PROTECTED.get()) {
            if (protectedId.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** 万灵之主会盯上的饰品：终界之星、生命之星（两个光环与晨曦护符不在剥夺范围内） */
    private static boolean isAccessory(ItemStack stack) {
        return stack.is(EndboundEmber.END_STAR.get())
                || stack.is(EndboundEmber.LIFE_STAR.get());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (this.isPhantom()) {
            return;
        }
        this.bossEvent.removePlayer(player);
        clearPlayerPressure(player);
    }

    /** 解除本词条挂在玩家身上的所有压力标记 */
    private static void clearPlayerPressure(ServerPlayer player) {
        player.removeEffect(ModEffects.JUDGEMENT_THUNDER);
        CompoundTag data = player.getPersistentData();
        data.remove(HEAL_REDUCTION_TAG);
        data.remove(NO_SPRINT_TAG);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.refreshBossBarName();
    }

    @Override
    public void die(DamageSource damageSource) {
        // 分身死亡只化作烟尘，不执行本体的升空动画、战利品与装备归还
        if (this.isPhantom()) {
            this.poof();
            return;
        }
        // 死亡逻辑可能二次进入（前科：掉落物翻倍），全程只允许执行一次
        if (this.dead) {
            return;
        }
        // 第一阶段血条打空：不死亡，转入第二阶段「亡灵末日」
        // 掉出世界与 /kill 属于强制抹除，直接走正常死亡流程，避免 Boss 卡在虚空里
        if (this.phase < PHASE_TWO && !bypassesPhaseTwo(damageSource)) {
            this.beginPhaseTwo();
            return;
        }
        // 死亡瞬间补齐最后一档词条：低血量斩杀时也不会漏掉万灵之主的夺取与箱子
        this.tickTitleByHealth(true);
        // 本体倒下时，场上残留的幻影分身与亡者之柱一并清除
        for (Herobrine phantom : this.level().getEntitiesOfClass(Herobrine.class,
                this.getBoundingBox().inflate(64.0D), Herobrine::isPhantom)) {
            phantom.poof();
        }
        this.removeDoomPillars();
        // 本体完全死亡时，召唤的亡灵精锐一同陪葬
        for (Monster minion : this.level().getEntitiesOfClass(Monster.class,
                this.getBoundingBox().inflate(64.0D),
                minion -> minion.isAlive() && minion.getTags().contains(BOSS_MINION_TAG))) {
            minion.kill();
        }
        // 结束 BOSS 战时一并解除留在玩家身上的压力
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            clearPlayerPressure(player);
        }
        this.bossEvent.removeAllPlayers();
        // 万灵之主：把夺走的装备埋进死亡点地下的箱子。
        // 词条可能因回血回退，这里只要还有夺来的装备就照样归还
        if ((this.titleStats.plunderGear || !this.plunderLoot.isEmpty())
                && this.level() instanceof ServerLevel serverLevel) {
            this.spawnPlunderChest(serverLevel);
        }
        // 钻石、下界合金锭与永恒图腾
        this.dropBossLoot();
        // 记录死亡位置并关闭重力，死亡动画期间由 tickDeath 手动抬升
        this.deathBaseY = this.getY();
        this.setNoGravity(true);
        super.die(damageSource);
    }

    /** 强制抹除类伤害不触发二阶段，直接按正常死亡处理 */
    private static boolean bypassesPhaseTwo(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL);
    }

    /** BOSS 战在和平难度下照常进行：原版会在和平难度禁止怪物攻击玩家类型 */
    @Override
    public boolean canAttackType(EntityType<?> type) {
        if (type == EntityType.PLAYER && this.level().getDifficulty() == Difficulty.PEACEFUL) {
            return true;
        }
        return super.canAttackType(type);
    }

    /**
     * 第一阶段血条打空：回满血、切到「亡灵末日」，并在周围召出亡者之柱。
     * <p>柱子存活期间会分摊本体受到的大部分伤害，玩家需要先击碎柱子。</p>
     */
    private void beginPhaseTwo() {
        this.phase = PHASE_TWO;
        this.titleStage = TITLE_STAGES;
        this.telegraphs.clear();
        this.telegraphCooldown = TELEGRAPH_INTERVAL;
        this.cloneCooldown = CLONE_INTERVAL;
        // 场上残留的幻影分身一并收回，二阶段不再分裂分身
        for (Herobrine phantom : this.level().getEntitiesOfClass(Herobrine.class,
                this.getBoundingBox().inflate(64.0D), Herobrine::isPhantom)) {
            phantom.poof();
        }
        this.setHealth(this.getMaxHealth());
        this.invulnerableTime = PHASE_TWO_GRACE_TICKS;
        this.setTitle(HerobrineTitle.NECROTIC_DOOM);
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setProgress(1.0F);
        if (this.level() instanceof ServerLevel level) {
            this.spawnDoomPillars(level, DOOM_PILLAR_COUNT);
            Vec3 eye = this.getEyePosition();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, eye.x, eye.y, eye.z, 4,
                    1.0D, 1.0D, 1.0D, 0.0D);
            level.sendParticles(ParticleTypes.SOUL, eye.x, eye.y, eye.z, 120,
                    1.5D, 2.0D, 1.5D, 0.08D);
            level.playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // 屏幕中央的转阶段提示
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("entity.endbound_ember.herobrine.phase_two.title")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("entity.endbound_ember.herobrine.phase_two.subtitle")));
        }
        this.announcePillars();
    }

    /** 在本体周围的地面上补召指定数量的亡者之柱 */
    private void spawnDoomPillars(ServerLevel level, int count) {
        double baseAngle = this.random.nextDouble() * Math.PI * 2.0D;
        int attempts = count * 3;
        for (int i = 0; i < attempts && count > 0; i++) {
            double angle = baseAngle + Math.PI * 2.0D * i * 0.61803398875D;
            double x = this.getX() + Math.cos(angle) * DOOM_PILLAR_RADIUS;
            double z = this.getZ() + Math.sin(angle) * DOOM_PILLAR_RADIUS;
            BlockPos base = this.findPillarBase(level, BlockPos.containing(x, this.getY(), z));
            if (base == null) {
                continue;
            }
            DoomPillarBlock.placePillar(level, base);
            this.doomPillars.add(base.immutable());
            count--;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    base.getX() + 0.5D, base.getY() + 0.5D, base.getZ() + 0.5D, 40,
                    0.4D, 1.2D, 0.4D, 0.04D);
        }
    }

    /**
     * 从给定位置向上下搜索一个可以放柱子的落脚点：要求脚下是实体方块，且柱身三格是空气。
     *
     * @return 可放置的方块坐标，找不到时返回 null
     */
    @Nullable
    private BlockPos findPillarBase(ServerLevel level, BlockPos origin) {
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos base = origin.offset(0, dy, 0);
            if (base.getY() <= level.getMinBuildHeight() || base.getY() + 2 >= level.getMaxBuildHeight()) {
                continue;
            }
            if (!level.getBlockState(base.below()).isSolid()) {
                continue;
            }
            boolean clear = true;
            for (int h = 0; h < 3; h++) {
                if (!level.getBlockState(base.above(h)).canBeReplaced()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return base;
            }
        }
        return null;
    }

    /** 二阶段每 tick：清理被打碎的柱子、周期性补召并低频绘制指示线 */
    private void tickDoomPillars() {
        if (this.phase < PHASE_TWO || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.doomPillars.removeIf(pos ->
                !(level.getBlockEntity(pos) instanceof DoomPillarBlockEntity pillar)
                        || !pillar.isAlivePillar());
        if (this.pillarRespawnCooldown > 0) {
            this.pillarRespawnCooldown--;
        }
        if (this.pillarRespawnCooldown <= 0) {
            int missing = DOOM_PILLAR_COUNT - this.doomPillars.size();
            if (missing > 0) {
                this.spawnDoomPillars(level, missing);
                this.announcePillars();
            }
            this.pillarRespawnCooldown = DOOM_PILLAR_RESPAWN_INTERVAL;
        }
        if (this.doomPillars.isEmpty()) {
            this.pillarHintTicks = 0;
            this.pillarHintCooldown = PILLAR_HINT_INTERVAL;
        }
        if (this.pillarHintCooldown > 0) {
            this.pillarHintCooldown--;
        }
        if (!this.doomPillars.isEmpty() && this.pillarHintCooldown <= 0) {
            this.pillarHintCooldown = PILLAR_HINT_INTERVAL;
            this.pillarHintTicks = PILLAR_HINT_DURATION;
            this.announcePillars();
        }
        if (this.pillarHintTicks <= 0 || this.doomPillars.isEmpty()) {
            return;
        }
        this.pillarHintTicks--;
        if (this.pillarHintTicks % PILLAR_BEAM_REDRAW != 0) {
            return;
        }
        for (BlockPos pos : this.doomPillars) {
            this.drawPillarBeam(level, pos);
        }
    }

    /** 从本体眼睛拉一条末地烛粒子线到柱子顶部，告诉玩家柱子在哪里 */
    private void drawPillarBeam(ServerLevel level, BlockPos pos) {
        Vec3 from = this.getEyePosition();
        Vec3 to = new Vec3(pos.getX() + 0.5D, pos.getY() + PILLAR_BEAM_HEIGHT, pos.getZ() + 0.5D);
        Vec3 delta = to.subtract(from);
        int steps = Math.max(2, (int) Math.ceil(delta.length() * 2.0D));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(delta.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z,
                4, 0.25D, 0.2D, 0.25D, 0.01D);
    }

    /** 用动作栏提示玩家柱子数量与分摊机制 */
    private void announcePillars() {
        Component message = Component.translatable(
                "entity.endbound_ember.herobrine.doom_pillar_hint", this.doomPillars.size());
        for (ServerPlayer player : List.copyOf(this.bossEvent.getPlayers())) {
            player.displayClientMessage(message, true);
        }
    }

    /**
     * 把伤害按当前存活柱子数量分摊给柱子。
     *
     * @return 分摊后真正落到本体身上的伤害
     */
    private float splitDamageToPillars(float amount) {
        if (amount <= 0.0F) {
            return amount;
        }
        List<DoomPillarBlockEntity> alive = this.aliveDoomPillars();
        if (alive.isEmpty()) {
            return amount;
        }
        float absorbed = amount * pillarShare(alive.size());
        float each = absorbed / alive.size();
        for (DoomPillarBlockEntity pillar : alive) {
            pillar.applyDamage(each);
        }
        return amount - absorbed;
    }

    /** 预估分摊后落到本体的伤害，仅用于锁血判定，不真正扣柱子血量 */
    private float projectDamageAfterPillars(float amount) {
        if (this.phase < PHASE_TWO) {
            return amount;
        }
        int alive = this.aliveDoomPillars().size();
        return alive <= 0 ? amount : amount * (1.0F - pillarShare(alive));
    }

    /** 存活柱子的分摊比例：1 根 60%、2 根 75%、3 根 90%，更多按 90% 封顶 */
    private static float pillarShare(int aliveCount) {
        return Math.min(0.9F, DOOM_PILLAR_SHARE + DOOM_PILLAR_SHARE_STEP * (aliveCount - 1));
    }

    /** 收集仍能分摊伤害的柱子，同时清掉已经失效的记录 */
    private List<DoomPillarBlockEntity> aliveDoomPillars() {
        List<DoomPillarBlockEntity> alive = new ArrayList<>();
        if (!(this.level() instanceof ServerLevel level) || this.doomPillars.isEmpty()) {
            return alive;
        }
        for (Iterator<BlockPos> it = this.doomPillars.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (level.getBlockEntity(pos) instanceof DoomPillarBlockEntity pillar
                    && pillar.isAlivePillar()) {
                alive.add(pillar);
            } else {
                it.remove();
            }
        }
        return alive;
    }

    /** 清掉场上所有亡者之柱（本体死亡或重置时调用） */
    private void removeDoomPillars() {
        if (this.level() instanceof ServerLevel level) {
            for (BlockPos pos : this.doomPillars) {
                DoomPillarBlock.removePillar(level, pos);
            }
        }
        this.doomPillars.clear();
    }

    /** 万灵之主被击败：在死亡位置地下埋一个箱子归还全部装备 */
    private void spawnPlunderChest(ServerLevel level) {
        BlockPos origin = this.blockPosition();
        BlockPos chestPos = findBuriedChestPos(level, origin);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : this.plunderLoot) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (slot < chest.getContainerSize()) {
                    chest.setItem(slot++, stack);
                } else {
                    Containers.dropItemStack(level, chestPos.getX() + 0.5D, chestPos.getY() + 1.0D,
                            chestPos.getZ() + 0.5D, stack);
                }
            }
            chest.setChanged();
        }
        this.plunderLoot.clear();
        this.plunderedPlayers.clear();
        level.sendParticles(ParticleTypes.SOUL, origin.getX() + 0.5D, origin.getY() + 0.5D,
                origin.getZ() + 0.5D, 40, 0.8D, 1.0D, 0.8D, 0.02D);
    }

    /** 击败掉落：二阶段 Boss 的奖励，包含晨曦之星与大量珍稀材料 */
    private void dropBossLoot() {
        if (!(this.level() instanceof ServerLevel level)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        this.dropLootStack(level, new ItemStack(Items.DIAMOND, 48 + this.random.nextInt(33)));
        this.dropLootStack(level, new ItemStack(Items.NETHERITE_INGOT, 48 + this.random.nextInt(33)));
        this.dropLootStack(level, new ItemStack(Items.NETHER_STAR, 3 + this.random.nextInt(3)));
        this.dropLootStack(level, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4 + this.random.nextInt(5)));
        this.dropLootStack(level, new ItemStack(Items.ECHO_SHARD, 8 + this.random.nextInt(9)));
        this.dropLootStack(level, new ItemStack(EndboundEmber.ETERNAL_TOTEM.get()));
        // 晨曦之星改为由 Herobrine 掉落
        this.dropLootStack(level, new ItemStack(EndboundEmber.DAWN_STAR.get()));
    }

    /** 单份战利品：从死亡点上方竖直落下，清零随机初速，仅加微小水平偏移避免几份叠在一起 */
    private void dropLootStack(ServerLevel level, ItemStack stack) {
        double ox = (this.random.nextDouble() - 0.5D) * 0.6D;
        double oz = (this.random.nextDouble() - 0.5D) * 0.6D;
        ItemEntity item = new ItemEntity(level,
                this.getX() + ox, this.getY() + 0.5D, this.getZ() + oz, stack);
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(20);
        level.addFreshEntity(item);
    }

    /** 从死亡点向下找到第一个实体方块，把箱子埋在那个位置 */
    private static BlockPos findBuriedChestPos(ServerLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        for (int i = 0; i < CHEST_SEARCH_DEPTH; i++) {
            pos.move(0, -1, 0);
            if (pos.getY() <= level.getMinBuildHeight()) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            // 基岩等无法破坏的方块，改埋在其上方一格；上方也不空则回退到死亡位置
            if (state.getDestroySpeed(level, pos) < 0.0F) {
                BlockPos above = pos.above();
                return level.getBlockState(above).isAir() ? above : origin.immutable();
            }
            return pos.immutable();
        }
        return origin.immutable();
    }

    /** 远程攻击：预判玩家走位后连射一支箭 */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = this.getMainHandItem();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), 1.0F, weapon);
        double distance = Math.sqrt(this.distanceToSqr(target));
        Vec3 predicted = target.position()
                .add(target.getDeltaMovement().scale(Math.min(distance / 3.0D, 12.0D)));
        double dx = predicted.x - this.getX();
        double dy = predicted.y + target.getBbHeight() * 0.6D - arrow.getY();
        double dz = predicted.z - this.getZ();
        arrow.shoot(dx, dy, dz, 1.8F, 1.0F);
        arrow.setBaseDamage(ARROW_DAMAGE);
        // 箭雨统帅：中箭后短暂无法疾跑
        if (this.titleStats.noSprintOnArrow) {
            arrow.getPersistentData().putBoolean(NO_SPRINT_TAG, true);
        }
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F,
                1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    /**
     * 距离决定战斗模式：近处贴脸挥击，远处边游走边连射。
     * <p>远程的具体射击节奏由 {@link Herobrine#tickRangedAttack} 负责，
     * 这里只负责走位与朝向，避免 Goal 生命周期重置射速状态。
     */
    protected static final class HerobrineAttackGoal extends Goal {
        private final Herobrine mob;
        private int attackCooldown;
        /** 距离下次重算走位路径还有多少 tick */
        private int repathCooldown;
        /** 当前侧向绕圈方向还能保持多少 tick */
        private int strafeTicks;
        /** 绕圈方向：1 为顺时针、-1 为逆时针 */
        private double strafeSign = 1.0D;

        HerobrineAttackGoal(Herobrine mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return this.mob.isAlive() && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.attackCooldown = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            this.mob.setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }
            double distanceSqr = this.mob.distanceToSqr(target);
            if (distanceSqr <= MELEE_RANGE * MELEE_RANGE) {
                // 近战：贴着目标走，进入触及范围后挥击
                this.mob.setAggressive(true);
                this.mob.getNavigation().moveTo(target, 1.2D);
                if (distanceSqr <= 9.0D && this.attackCooldown <= 0) {
                    this.mob.doHurtTarget(target);
                    this.attackCooldown = MELEE_COOLDOWN;
                }
            } else {
                // 远程：保持中距离并侧向游走，射击节奏由实体自身的 tick 推进
                this.mob.setAggressive(false);
                this.tickRangedMovement(target);
            }
        }

        /**
         * 远程走位：太远就主动逼近，太近就后撤拉开，中距离则绕着目标侧向走，
         * 避免像木桩一样站着挨打。
         */
        private void tickRangedMovement(LivingEntity target) {
            if (this.repathCooldown > 0) {
                this.repathCooldown--;
                return;
            }
            this.repathCooldown = STRAFE_REPATH_INTERVAL;
            Vec3 mobPos = this.mob.position();
            Vec3 toTarget = target.position().subtract(mobPos);
            double distance = toTarget.horizontalDistance();
            double speed = 1.1D;
            if (distance > RANGED_KEEP_MAX) {
                this.mob.getNavigation().moveTo(target, speed);
                return;
            }
            if (distance < RANGED_KEEP_MIN) {
                Vec3 away = new Vec3(toTarget.x, 0.0D, toTarget.z).normalize().scale(-4.0D);
                this.mob.getNavigation().moveTo(mobPos.x + away.x, mobPos.y, mobPos.z + away.z, speed);
                return;
            }
            if (--this.strafeTicks <= 0) {
                this.strafeTicks = STRAFE_FLIP_MIN
                        + this.mob.getRandom().nextInt(STRAFE_FLIP_MAX - STRAFE_FLIP_MIN + 1);
                this.strafeSign = this.mob.getRandom().nextBoolean() ? 1.0D : -1.0D;
            }
            Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z).normalize();
            Vec3 side = new Vec3(-flat.z, 0.0D, flat.x).scale(4.0D * this.strafeSign);
            this.mob.getNavigation().moveTo(mobPos.x + side.x, mobPos.y, mobPos.z + side.z, speed);
        }
    }

    /** 词条带来的玩家侧压力，统一在这里处理；佩戴晨曦护符的玩家全部免除 */
    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        /** 亡灵精锐与 Herobrine 互不仇恨：误伤不会引发内讧 */
        @SubscribeEvent
        public static void onChangeTarget(LivingChangeTargetEvent event) {
            LivingEntity changer = event.getEntity();
            LivingEntity newTarget = event.getNewAboutToBeSetTarget();
            boolean targetIsBoss = newTarget instanceof Herobrine;
            boolean targetIsMinion = newTarget != null && newTarget.getTags().contains(BOSS_MINION_TAG);
            if ((changer.getTags().contains(BOSS_MINION_TAG) && targetIsBoss)
                    || (changer instanceof Herobrine && targetIsMinion)) {
                event.setCanceled(true);
            }
        }

        /** 千军之主：精锐死亡后留下 3 秒亡魂区（缓慢 I） */
        @SubscribeEvent
        public static void onMinionDeath(LivingDeathEvent event) {
            if (!event.getEntity().getTags().contains(SOUL_ZONE_TAG)) {
                return;
            }
            if (!(event.getEntity().level() instanceof ServerLevel level)) {
                return;
            }
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    event.getEntity().getBoundingBox().inflate(4.0D))) {
                applyPressure(player, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            }
        }

        /**
         * 第二阶段亡者之柱分摊最终伤害。
         * <p>必须放在 LivingDamageEvent.Pre（LOWEST 优先级）：此时护甲、附魔与所有
         * LivingIncomingDamageEvent 里追加的伤害（千破·青溟、拂晓附魔、难度馈赠等）都已结算完毕，
         * 否则追加伤害会绕过柱子分摊直接打到本体；同时该事件只在伤害真正生效时触发，
         * 不会出现 Boss 处于无敌帧、柱子却被白白扣血的情况。</p>
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onDamagePre(LivingDamageEvent.Pre event) {
            if (!(event.getEntity() instanceof Herobrine boss)) {
                return;
            }
            if (boss.isPhantom() || boss.phase < PHASE_TWO || boss.level().isClientSide()) {
                return;
            }
            if (isVoidDamage(event.getSource())) {
                return;
            }
            event.setNewDamage(boss.splitDamageToPillars(event.getNewDamage()));
        }

        /** 破晓之敌对毕业装玩家增伤；箭雨统帅的箭会让玩家短暂无法疾跑 */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (event.getSource().getEntity() instanceof Herobrine boss
                    && event.getEntity() instanceof ServerPlayer player) {
                if (boss.titleStats.baneOfDawn && holdsDawnGear(player)) {
                    event.setAmount(event.getAmount() * 1.1F);
                }
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player
                    && event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                    && arrow.getPersistentData().getBoolean(NO_SPRINT_TAG)
                    && !isCurseImmune(player)) {
                player.getPersistentData().putInt(NO_SPRINT_TAG, NO_SPRINT_DURATION);
            }
        }

        /** 中箭后的禁跑计时 */
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            int ticks = player.getPersistentData().getInt(NO_SPRINT_TAG);
            if (ticks <= 0) {
                return;
            }
            player.getPersistentData().putInt(NO_SPRINT_TAG, ticks - 1);
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }

        /** 词条削减玩家治疗量（瘟疫 / 亡灵诅咒） */
        @SubscribeEvent
        public static void onHeal(LivingHealEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            float reduction = player.getPersistentData().getFloat(HEAL_REDUCTION_TAG);
            if (reduction <= 0.0F || isCurseImmune(player)) {
                return;
            }
            event.setAmount(event.getAmount() * (1.0F - reduction));
        }
    }

    /** 是否免疫亡灵诅咒：携带晨曦护符（含上位护符），或佩戴本模组的光环 */
    private static boolean isCurseImmune(ServerPlayer player) {
        return DawnAmuletItem.isEquipped(player)
                || AnahitaFirstlightLiddellItem.isEquipped(player)
                || wearsHalo(player);
    }

    /** 光环属于头盔类护甲，也可能被放进饰品栏，两种位置都算携带 */
    private static boolean wearsHalo(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.is(EndboundEmber.STARDUST_WITCH_HALO.get()) || stack.is(EndboundEmber.TIME_HALO.get())) {
                return true;
            }
        }
        if (!ModList.get().isLoaded("curios")) {
            return false;
        }
        return CuriosCompat.isEquipped(player, EndboundEmber.STARDUST_WITCH_HALO.get())
                || CuriosCompat.isEquipped(player, EndboundEmber.TIME_HALO.get());
    }

    /** 是否持有晨曦护符（含上位护符）或破晓武器（主手 / 副手） */
    private static boolean holdsDawnGear(ServerPlayer player) {
        return DawnAmuletItem.isEquipped(player)
                || AnahitaFirstlightLiddellItem.isEquipped(player)
                || DawnEnchantment.levelOf(player.getMainHandItem()) > 0
                || DawnEnchantment.levelOf(player.getOffhandItem()) > 0;
    }
}
