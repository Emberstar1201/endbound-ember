package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.CraftsmanshipEnchantment;
import com.emberstar.endbounember.enchantment.DurabilityBoostEnchantment;
import com.emberstar.endbounember.enchantment.ElegantCatwalkEnchantment;
import com.emberstar.endbounember.enchantment.FallingStarGuidanceEnchantment;
import com.emberstar.endbounember.enchantment.LightweightEnchantment;
import com.emberstar.endbounember.enchantment.WindRippleEnchantment;
import com.emberstar.endbounember.world.ModAdvancements;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 星尘女巫的服务端生成、生命周期、粒子和奖励逻辑。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EmberStarSorceressEvents {
    private static final int CHECK_INTERVAL = 20;
    private static final double MIN_DISTANCE = 3.0D;
    private static final double MAX_DISTANCE = 10.0D;
    /** 同一玩家的刷新冷却：5 分钟，避免跑图时星尘魔女连续出现。 */
    private static final long SPAWN_COOLDOWN_TICKS = 5L * 60L * 20L;
    /** 冷却数据的存档名，写入主世界 SavedData，服务器重启后依然生效。 */
    private static final String DATA_NAME = "endbound_ember_sorceress_cooldowns";
    /** 触发“短暂指引”事件的概率。 */
    private static final float GUIDANCE_CHANCE = 0.25F;
    /** 触发“神秘试炼”事件的概率（在指引之后判定）。 */
    private static final float TRIAL_CHANCE = 0.20F;
    /** 神秘试炼的时限：60 秒。 */
    private static final long TRIAL_DURATION_TICKS = 60L * 20L;
    /** 神秘试炼要求击败的亡灵生物数量。 */
    private static final int TRIAL_REQUIRED_KILLS = 3;
    /** 消失赠礼中附魔书的候选附魔与最大等级。 */
    private static final List<Map.Entry<ResourceKey<Enchantment>, Integer>> BOOK_POOL = List.of(
            Map.entry(DurabilityBoostEnchantment.DURABILITY_BOOST, 3),
            Map.entry(CraftsmanshipEnchantment.KEY, 1),
            Map.entry(FallingStarGuidanceEnchantment.KEY, 3),
            Map.entry(LightweightEnchantment.KEY, 3),
            Map.entry(WindRippleEnchantment.KEY, 1),
            Map.entry(ElegantCatwalkEnchantment.KEY, 1));

    /** 进行中的神秘试炼：玩家 UUID → 试炼状态。试炼最多持续 60 秒，无需跨重启持久化。 */
    private static final Map<UUID, Trial> ACTIVE_TRIALS = new ConcurrentHashMap<>();

    private EmberStarSorceressEvents() {
    }

    /** 单个玩家的神秘试炼进度。 */
    private static final class Trial {
        private final long endTick;
        private int kills;

        private Trial(long endTick) {
            this.endTick = endTick;
        }
    }

    /** 玩家 UUID → 最近一次刷新游戏刻，持久化保存以保证冷却跨重启有效。 */
    private static final class CooldownData extends SavedData {
        private final Map<UUID, Long> lastSpawnTicks = new HashMap<>();

        private static CooldownData load(CompoundTag tag, HolderLookup.Provider provider) {
            CooldownData data = new CooldownData();
            ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                data.lastSpawnTicks.put(entry.getUUID("Id"), entry.getLong("Tick"));
            }
            return data;
        }

        /** 该玩家是否仍处于刷新冷却中。 */
        private boolean isOnCooldown(UUID player, long gameTime) {
            Long lastSpawn = lastSpawnTicks.get(player);
            return lastSpawn != null && gameTime - lastSpawn < SPAWN_COOLDOWN_TICKS;
        }

        /** 记录一次成功刷新，冷却从该时刻重新计时。 */
        private void markSpawned(UUID player, long gameTime) {
            lastSpawnTicks.put(player, gameTime);
            setDirty();
        }

        /** 清理已过期的记录，避免存档无限增长。 */
        private void prune(long gameTime) {
            if (lastSpawnTicks.values().removeIf(tick -> gameTime - tick >= SPAWN_COOLDOWN_TICKS)) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Long> entry : lastSpawnTicks.entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putUUID("Id", entry.getKey());
                item.putLong("Tick", entry.getValue());
                list.add(item);
            }
            tag.put("Players", list);
            return tag;
        }
    }

    private static CooldownData cooldowns(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CooldownData::new, CooldownData::load, null), DATA_NAME);
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        // Mob 构造时会创建寻路组件并读取 FOLLOW_RANGE，属性表必须包含它，否则创建实体直接崩溃。
        event.put(ModEntities.EMBERSTAR_SORCERESS.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .build());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EmberStarSorceress sorceress) {
            sorceress.setInvulnerable(true);
            sorceress.setNoAi(true);
            sorceress.setNoGravity(true);
            sorceress.noPhysics = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }

        long gameTime = event.getServer().overworld().getGameTime();
        CooldownData cooldowns = cooldowns(event.getServer());
        cooldowns.prune(gameTime);
        tickTrials(event.getServer(), gameTime);

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!isAllowedDimension(level)) {
                continue;
            }
            tickSorceresses(level);
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && level.random.nextFloat() < 0.10F) {
                    trySpawn(level, player, cooldowns, gameTime);
                }
            }
        }
    }

    private static void tickSorceresses(ServerLevel level) {
        // 必须覆盖整个世界范围：早期使用以原点为中心、半径 320 的包围盒，
        // 玩家在远离原点处生成的星尘魔女永远不会被检索到，导致既不会消失也不会掉落奖励。
        AABB worldBounds = new AABB(-30000000.0D, level.getMinBuildHeight(), -30000000.0D,
                30000000.0D, level.getMaxBuildHeight(), 30000000.0D);
        for (EmberStarSorceress sorceress : level.getEntitiesOfClass(EmberStarSorceress.class, worldBounds)) {
            if (sorceress.tickCount < EmberStarSorceress.LIFETIME_TICKS) {
                continue;
            }
            Vec3 pos = sorceress.position().add(0.0D, 0.9D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 32, 0.55D, 0.9D, 0.55D, 0.08D);
            level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 48, 0.7D, 1.0D, 0.7D, 0.3D);
            dropReward(level, sorceress);
            sorceress.discard();
        }
    }

    /** 注视判定范围（格） */
    private static final double GAZE_RANGE = 32.0D;
    /** 视线与目标方向的夹角阈值：约 20° */
    private static final double GAZE_MIN_DOT = 0.94D;
    /** 连续注视所需 tick 数（0.5 秒） */
    private static final int GAZE_REQUIRED_TICKS = 10;
    /** 玩家 UUID → 当前连续注视的 tick 数 */
    private static final Map<UUID, Integer> GAZE_TICKS = new ConcurrentHashMap<>();

    /**
     * 「不被世界承认的少女」：持续注视一只自然刷新的星尘魔女约半秒即可弹出。
     * 指令召唤的个体没有自然刷新标记，因此不会触发。
     */
    @SubscribeEvent
    public static void onGazeTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkGaze(player);
            }
        }
    }

    private static void checkGaze(ServerPlayer player) {
        UUID id = player.getUUID();
        if (ModAdvancements.hasDone(player, ModAdvancements.UNRECOGNIZED_GIRL)) {
            GAZE_TICKS.remove(id);
            return;
        }
        Vec3 look = player.getViewVector(1.0F);
        Vec3 eye = player.getEyePosition();
        boolean gazing = false;
        for (EmberStarSorceress sorceress : player.level().getEntitiesOfClass(EmberStarSorceress.class,
                player.getBoundingBox().inflate(GAZE_RANGE), EmberStarSorceress::isNaturalSpawn)) {
            if (!player.hasLineOfSight(sorceress)) {
                continue;
            }
            Vec3 direction = sorceress.getEyePosition().subtract(eye);
            if (direction.lengthSqr() < 1.0E-4D) {
                continue;
            }
            if (look.dot(direction.normalize()) >= GAZE_MIN_DOT) {
                gazing = true;
                break;
            }
        }
        if (!gazing) {
            GAZE_TICKS.remove(id);
            return;
        }
        int ticks = GAZE_TICKS.merge(id, 1, Integer::sum);
        if (ticks >= GAZE_REQUIRED_TICKS) {
            GAZE_TICKS.remove(id);
            ModAdvancements.award(player, ModAdvancements.UNRECOGNIZED_GIRL);
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer player, CooldownData cooldowns, long gameTime) {
        if (cooldowns.isOnCooldown(player.getUUID(), gameTime)) {
            return;
        }
        if (!level.getEntitiesOfClass(EmberStarSorceress.class, player.getBoundingBox().inflate(16.0D)).isEmpty()) {
            return;
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_DISTANCE + level.random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!isSafePosition(level, surface)) {
                continue;
            }

            EmberStarSorceress entity = ModEntities.EMBERSTAR_SORCERESS.get().create(level);
            if (entity == null) {
                return;
            }
            entity.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(entity, entity.getBoundingBox())) {
                continue;
            }
            level.addFreshEntity(entity);
            entity.markNaturalSpawn();
            cooldowns.markSpawned(player.getUUID(), gameTime);
            rollSorceressEvent(level, player, entity);
            return;
        }
    }

    /** 魔女生成后随机触发一次事件：短暂指引或神秘试炼，也可能什么都不发生。 */
    private static void rollSorceressEvent(ServerLevel level, ServerPlayer player, EmberStarSorceress sorceress) {
        float roll = level.random.nextFloat();
        if (roll < GUIDANCE_CHANCE) {
            grantBriefGuidance(level, player, sorceress);
        } else if (roll < GUIDANCE_CHANCE + TRIAL_CHANCE) {
            startMysteriousTrial(level, player, sorceress);
        }
    }

    /** 短暂指引：给予短时夜视与速度，附上一句星尘提示，并用粒子把魔女的星光引向玩家。 */
    private static void grantBriefGuidance(ServerLevel level, ServerPlayer player, EmberStarSorceress sorceress) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60 * 20, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 0, false, false));
        Component tip = Component.translatable("endbound_ember.sorceress.guidance." + level.random.nextInt(3));
        player.sendSystemMessage(Component.translatable("endbound_ember.sorceress.guidance", tip));

        Vec3 from = sorceress.position().add(0.0D, 1.2D, 0.0D);
        Vec3 to = player.position().add(0.0D, 1.0D, 0.0D);
        for (int i = 1; i <= 8; i++) {
            Vec3 point = from.lerp(to, i / 8.0D);
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 2, 0.05D, 0.05D, 0.05D, 0.01D);
        }
    }

    /** 神秘试炼：60 秒内击败 3 只亡灵生物，目标由魔女当场召唤到身边；完成后魔女留下额外赠礼。 */
    private static void startMysteriousTrial(ServerLevel level, ServerPlayer player, EmberStarSorceress sorceress) {
        ACTIVE_TRIALS.put(player.getUUID(), new Trial(level.getGameTime() + TRIAL_DURATION_TICKS));
        player.sendSystemMessage(Component.translatable("endbound_ember.sorceress.trial_start"));
        summonTrialTargets(level, sorceress, TRIAL_REQUIRED_KILLS);
    }

    /** 由魔女亲自召唤试炼目标：在自身周围生成指定数量的亡灵生物 */
    private static void summonTrialTargets(ServerLevel level, EmberStarSorceress sorceress, int count) {
        Vec3 center = sorceress.position().add(0.0D, 0.9D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 40, 1.2D, 0.6D, 1.2D, 0.25D);
        for (int i = 0; i < count; i++) {
            BlockPos spot = findSummonSpot(level, sorceress);
            if (spot == null) {
                continue;
            }
            Monster minion = level.random.nextBoolean()
                    ? EntityType.ZOMBIE.create(level)
                    : EntityType.SKELETON.create(level);
            if (minion == null) {
                continue;
            }
            minion.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            minion.setPersistenceRequired();
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.END_ROD, spot.getX() + 0.5D, spot.getY() + 1.0D,
                    spot.getZ() + 0.5D, 12, 0.3D, 0.5D, 0.3D, 0.05D);
        }
    }

    /** 在魔女周围 1~4 格内找一个可站立的落点，找不到就退回魔女脚下 */
    private static BlockPos findSummonSpot(ServerLevel level, EmberStarSorceress sorceress) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + level.random.nextDouble() * 3.0D;
            BlockPos feet = BlockPos.containing(sorceress.getX() + Math.cos(angle) * distance,
                    sorceress.getY(), sorceress.getZ() + Math.sin(angle) * distance);
            if (isSafePosition(level, feet)) {
                return feet;
            }
        }
        BlockPos fallback = sorceress.blockPosition();
        return isSafePosition(level, fallback) ? fallback : null;
    }

    /** 判断目标是否属于亡灵生物；本模组的两位少女算作伙伴，不计入试炼。 */
    private static boolean isUndead(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof ZombieGirl || entity instanceof DrownedGirl) return false;
        return entity instanceof net.minecraft.world.entity.monster.Zombie
                || entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                || entity instanceof net.minecraft.world.entity.monster.Phantom
                || entity instanceof net.minecraft.world.entity.monster.Zoglin
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.animal.horse.ZombieHorse
                || entity instanceof net.minecraft.world.entity.animal.horse.SkeletonHorse;
    }

    /** 试炼期间的击杀计数：只统计试炼玩家击杀的亡灵生物。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        Trial trial = ACTIVE_TRIALS.get(player.getUUID());
        if (trial == null) return;
        if (!isUndead(event.getEntity())) return;

        trial.kills++;
        player.displayClientMessage(Component.translatable("endbound_ember.sorceress.trial_progress",
                Math.min(trial.kills, TRIAL_REQUIRED_KILLS)), true);
    }

    /** 结算到期的试炼：达成目标则额外赠礼，失败仅提示。 */
    private static void tickTrials(MinecraftServer server, long gameTime) {
        if (ACTIVE_TRIALS.isEmpty()) return;

        Iterator<Map.Entry<UUID, Trial>> iterator = ACTIVE_TRIALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Trial> entry = iterator.next();
            Trial trial = entry.getValue();
            if (gameTime < trial.endTick) continue;
            iterator.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (trial.kills >= TRIAL_REQUIRED_KILLS) {
                player.sendSystemMessage(Component.translatable("endbound_ember.sorceress.trial_success"));
                dropAt(player, new ItemStack(EndboundEmber.UNOWNED_STARDUST.get()));
                dropAt(player, new ItemStack(Items.EXPERIENCE_BOTTLE, 8));
            } else {
                player.sendSystemMessage(Component.translatable("endbound_ember.sorceress.trial_fail"));
            }
        }
    }

    private static void dropAt(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), stack));
    }

    private static boolean isSafePosition(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return !below.getCollisionShape(level, feet.below()).isEmpty()
                && body.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && body.getCollisionShape(level, feet).isEmpty()
                && head.getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void dropReward(ServerLevel level, Entity entity) {
        ItemStack reward;
        switch (level.random.nextInt(6)) {
            case 0 -> reward = new ItemStack(Items.DIAMOND, 3);
            case 1 -> reward = new ItemStack(Items.NETHERITE_INGOT);
            case 2 -> reward = new ItemStack(EndboundEmber.EMBERSTAR.get());
            case 3 -> reward = randomEnchantedBook(level);
            case 4 -> reward = new ItemStack(EndboundEmber.UNOWNED_STARDUST.get());
            default -> reward = new ItemStack(EndboundEmber.STARLIGHT_STAR.get());
        }
        level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5D, entity.getZ(), reward));
    }

    /** 从候选池中随机抽取一本本模组附魔书，等级在合法范围内随机。 */
    private static ItemStack randomEnchantedBook(ServerLevel level) {
        Map.Entry<ResourceKey<Enchantment>, Integer> entry = BOOK_POOL.get(level.random.nextInt(BOOK_POOL.size()));
        int enchantLevel = 1 + level.random.nextInt(entry.getValue());
        return EndboundEmber.createEnchantedBook(level.registryAccess(), entry.getKey(), enchantLevel);
    }

    private static boolean isAllowedDimension(ServerLevel level) {
        // 时间乱流维度同样开放：星尘魔女会照常出现、触发指引与试炼
        return level.dimension() == Level.OVERWORLD
                || level.dimension() == Level.NETHER
                || level.dimension() == Level.END
                || TimeTurbulenceLevels.isTimeTurbulence(level);
    }
}
