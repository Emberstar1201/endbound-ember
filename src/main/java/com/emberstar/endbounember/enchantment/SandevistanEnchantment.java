package com.emberstar.endbounember.enchantment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 斯安维斯坦：胸甲专属，最高 III 级，鞘翅不在可附魔物品标签内。
 * 穿戴附魔胸甲时潜行即启动「子弹时间」：范围内非玩家生物整只停止 tick、投射物定住，
 * 末影龙与凋灵只施加缓慢；自身获得移速与攻速加成。持续结束后进入冷却并消耗少量饥饿值。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SandevistanEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "sandevistan"));

    /** 各级持续时间（tick）：I 级 5 秒 / II 级 15 秒 / III 级 30 秒。 */
    private static final int[] DURATION_TICKS = { 100, 300, 600 };
    /** 各级冷却时间（tick）：I 级 10 秒 / II 级 30 秒 / III 级 65 秒。 */
    private static final int[] COOLDOWN_TICKS = { 200, 600, 1300 };
    /** 各级对 Boss 施加的缓慢等级（I / II / III 分别对应缓慢 I / II / III）。 */
    private static final int[] BOSS_SLOW_AMPLIFIER = { 0, 1, 2 };
    /** 各级自身移速加成（比例）。 */
    private static final double[] SELF_SPEED_BONUS = { 0.30D, 0.40D, 0.50D };
    /** 各级自身攻速加成（比例）。 */
    private static final double[] SELF_ATTACK_SPEED_BONUS = { 0.20D, 0.35D, 0.50D };
    /** 各级结束时消耗的饥饿值（鸡腿数）。 */
    private static final int[] HUNGER_COST = { 2, 2, 1 };

    /** 时缓影响范围：以激活者为中心的水平半宽（格），竖直方向不设限。 */
    private static final double RADIUS = 24.0D;
    /** Boss 缓慢效果的刷新时长（tick）。 */
    private static final int BOSS_SLOW_DURATION = 40;

    private static final ResourceLocation SPEED_ID = modifierId("sandevistan_speed");
    private static final ResourceLocation ATTACK_SPEED_ID = modifierId("sandevistan_attack_speed");

    /** 冷却截止时间戳（gameTime），随玩家持久化数据保存。 */
    private static final String TAG_COOLDOWN_UNTIL = "EndboundEmberSandevistanCooldown";
    /** 上一 tick 是否处于潜行，用于识别「刚按下潜行」这一边沿。 */
    private static final String TAG_WAS_SNEAKING = "EndboundEmberSandevistanSneaking";

    /** 当前激活中的时缓场：激活者 UUID → 场状态。 */
    private static final Map<UUID, ActiveField> ACTIVE = new HashMap<>();

    private SandevistanEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 一个激活中的时缓场。 */
    private static final class ActiveField {
        private final int level;
        private int ticksLeft;

        private ActiveField(int level, int ticksLeft) {
            this.level = level;
            this.ticksLeft = ticksLeft;
        }
    }

    /** 读取物品上的斯安维斯坦附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int level = levelOf(stack, DataComponents.ENCHANTMENTS);
        if (level > 0) return level;
        return levelOf(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static int levelOf(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /** 穿戴附魔胸甲时潜行（按下潜行的瞬间）启动子弹时间。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        if (ACTIVE.containsKey(uuid) && !wearsEnchantedChest(player)) {
            // 中途换下胸甲：立即结束，不再消耗饥饿值
            ACTIVE.remove(uuid);
            clearSelfModifiers(player);
        }
        if (player.isDeadOrDying() || player.isRemoved()) {
            ACTIVE.remove(uuid);
            clearSelfModifiers(player);
        }

        boolean sneaking = player.isShiftKeyDown();
        boolean wasSneaking = player.getPersistentData().getBoolean(TAG_WAS_SNEAKING);
        player.getPersistentData().putBoolean(TAG_WAS_SNEAKING, sneaking);
        if (!sneaking || wasSneaking) return;
        if (ACTIVE.containsKey(uuid)) return;
        if (player.isSpectator() || player.isDeadOrDying()) return;

        int level = levelOf(player.getItemBySlot(EquipmentSlot.CHEST));
        if (level <= 0) return;
        if (player.level().getGameTime() < cooldownUntil(player)) return;

        activate(player, level);
    }

    private static boolean wearsEnchantedChest(Player player) {
        return levelOf(player.getItemBySlot(EquipmentSlot.CHEST)) > 0;
    }

    private static long cooldownUntil(Player player) {
        return player.getPersistentData().getLong(TAG_COOLDOWN_UNTIL);
    }

    private static void activate(ServerPlayer player, int level) {
        int index = Math.min(Math.max(level, 1), 3) - 1;
        int duration = DURATION_TICKS[index];
        int cooldown = COOLDOWN_TICKS[index];

        ACTIVE.put(player.getUUID(), new ActiveField(level, duration));
        // 冷却从效果结束之后开始计算
        player.getPersistentData().putLong(TAG_COOLDOWN_UNTIL,
                player.level().getGameTime() + duration + cooldown);

        applySelfModifiers(player, level);

        ServerLevel serverLevel = player.serverLevel();
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                40, 0.6D, 0.9D, 0.6D, 0.03D);
    }

    /** 效果自然结束：清理自身增益、消耗饥饿值并播放结束特效。 */
    private static void finish(ServerPlayer player, ActiveField field) {
        clearSelfModifiers(player);

        int index = Math.min(Math.max(field.level, 1), 3) - 1;
        int cost = HUNGER_COST[index];
        int foodLevel = player.getFoodData().getFoodLevel();
        if (cost > 0 && foodLevel > 0) {
            player.getFoodData().setFoodLevel(Math.max(0, foodLevel - cost));
        }

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.5D, 0.7D, 0.5D, 0.02D);
    }

    /** 服务端统一计时，即使激活者中途下线也能把残留的时缓场清掉。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveField>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveField> entry = iterator.next();
            ActiveField field = entry.getValue();
            if (--field.ticksLeft > 0) continue;

            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) finish(player, field);
        }
    }

    /**
     * 时缓核心：范围内非玩家生物整只停止 tick（含 AI），投射物定住；
     * 末影龙与凋灵只施加缓慢，激活者自己发射的投射物保持原速。
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (ACTIVE.isEmpty()) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        ActiveField field = strongestField(serverLevel, entity);
        if (field == null) return;

        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        BOSS_SLOW_DURATION,
                        BOSS_SLOW_AMPLIFIER[Math.min(Math.max(field.level, 1), 3) - 1],
                        false, false, false));
            }
            return;
        }

        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner != null && ACTIVE.containsKey(owner.getUUID())) return;
            event.setCanceled(true);
            return;
        }

        if (entity instanceof LivingEntity living && living.isAlive()) {
            event.setCanceled(true);
        }
    }

    /** 返回覆盖该实体的最强（等级最高）时缓场，无则返回 null。 */
    private static ActiveField strongestField(ServerLevel level, Entity entity) {
        ActiveField best = null;
        for (Map.Entry<UUID, ActiveField> entry : ACTIVE.entrySet()) {
            Entity activator = level.getEntity(entry.getKey());
            if (activator == null) continue;
            if (Math.abs(activator.getX() - entity.getX()) > RADIUS) continue;
            if (Math.abs(activator.getZ() - entity.getZ()) > RADIUS) continue;
            if (best == null || entry.getValue().level > best.level) {
                best = entry.getValue();
            }
        }
        return best;
    }

    private static void applySelfModifiers(Player player, int level) {
        int index = Math.min(Math.max(level, 1), 3) - 1;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (speed.getModifier(SPEED_ID) != null) speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, SELF_SPEED_BONUS[index],
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            if (attackSpeed.getModifier(ATTACK_SPEED_ID) != null) attackSpeed.removeModifier(ATTACK_SPEED_ID);
            attackSpeed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_ID,
                    SELF_ATTACK_SPEED_BONUS[index], AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void clearSelfModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_ID) != null) speed.removeModifier(SPEED_ID);

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && attackSpeed.getModifier(ATTACK_SPEED_ID) != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_ID);
        }
    }
}
