package com.emberstar.endbounember.enchantment;

import java.util.Optional;
import java.util.Set;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 人权剑的四项新效果：晨映之花、希望之花、我们逝去我们永恒、星火不灭。
 * 四项效果都是挂在本模组的 {@code free_will_swords} 标签上、随附魔等级生效。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FreeWillEnchantments {
    /** 晨映之花：剑气额外 +250%。 */
    public static final ResourceKey<Enchantment> DAWN_BLOOM = key("dawn_bloom");
    /** 希望之花：低血量增伤、提升抗性、免疫一次即死。 */
    public static final ResourceKey<Enchantment> HOPE_BLOOM = key("hope_bloom");
    /** 我们逝去，我们永恒：人权剑自带的蓝火剑气与驱散光环。 */
    public static final ResourceKey<Enchantment> WE_FALL_WE_REMAIN = key("we_fall_we_remain");
    /** 星火不灭：每次攻击追加目标最大生命值的虚空伤害。 */
    public static final ResourceKey<Enchantment> ETERNAL_SPARK = key("eternal_spark");

    /** 蓝火剑气使用的伤害类型（已在 bypasses_armor 标签中，无视护甲）。 */
    public static final ResourceKey<DamageType> BLUE_FLAME = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "blue_flame"));
    /** 白火（晨光）剑气使用的伤害类型，正常结算护甲。 */
    public static final ResourceKey<DamageType> FREE_WILL_LIGHT = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_light"));
    /** 星火不灭追加的虚空伤害，无视护甲、保护附魔与抗性。 */
    public static final ResourceKey<DamageType> VOID_SPARK = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "void_spark"));

    /** 晨映之花生效后的剑气倍率（100% + 250%）。 */
    public static final double DAWN_BLOOM_MULTIPLIER = 3.5D;
    /** 蓝火剑气倍率。 */
    public static final double BLUE_FLAME_MULTIPLIER = 10.0D;
    /** 蓝火剑气必定暴击，按 1.5 倍结算。 */
    public static final double BLUE_CRIT_MULTIPLIER = 1.5D;

    /** 希望之花的低血量判定线。 */
    public static final double HOPE_THRESHOLD = 0.5D;
    /** 希望之花的近战增伤倍率。 */
    public static final double HOPE_DAMAGE_MULTIPLIER = 1.5D;
    /** 抗性提升 II（0 为 I 级）。 */
    public static final int HOPE_RESISTANCE_AMPLIFIER = 1;
    /** 免疫即死的冷却：15 分钟。 */
    public static final int INSTANT_KILL_COOLDOWN_TICKS = 18000;

    /** 星火不灭 1~3 级对应的最大生命值比例，索引 0 不使用。 */
    public static final double[] ETERNAL_SPARK_RATIOS = {0.0D, 0.05D, 0.10D, 0.15D};
    /** 蓝火状态下生物远离玩家的判定半径。 */
    public static final double FLEE_RADIUS = 12.0D;
    /** 星火不灭扩散特效的半径（格）。 */
    private static final double SPARK_SPREAD_RADIUS = 5.0D;

    private static final String INSTANT_KILL_COOLDOWN_TAG = "EndboundEmberHopeBloomCooldown";
    private static final int HOPE_RESISTANCE_DURATION = 100;
    /** 驱散光环不作用的 BOSS 生物（原版 BOSS 全家桶）。 */
    private static final Set<EntityType<?>> BOSSES = Set.of(EntityType.WITHER, EntityType.ENDER_DRAGON,
            EntityType.ELDER_GUARDIAN, EntityType.WARDEN);

    private FreeWillEnchantments() {
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, name));
    }

    /** 读取物品上的指定附魔等级（武器看 ENCHANTMENTS，附魔书看 STORED_ENCHANTMENTS）。 */
    public static int levelOf(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        int level = levelIn(stack, DataComponents.ENCHANTMENTS, key);
        if (level > 0) return level;
        return levelIn(stack, DataComponents.STORED_ENCHANTMENTS, key);
    }

    private static int levelIn(ItemStack stack, DataComponentType<ItemEnchantments> type,
            ResourceKey<Enchantment> key) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> entryKey = entry.getKey().unwrapKey();
            if (entryKey.isPresent() && entryKey.get().equals(key)) return entry.getIntValue();
        }
        return 0;
    }

    public static boolean hasDawnBloom(ItemStack stack) {
        return levelOf(stack, DAWN_BLOOM) > 0;
    }

    public static boolean hasHopeBloom(ItemStack stack) {
        return levelOf(stack, HOPE_BLOOM) > 0;
    }

    public static boolean hasWeFallWeRemain(ItemStack stack) {
        return levelOf(stack, WE_FALL_WE_REMAIN) > 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem sword)) return;

        // 希望之花：低血量时持续获得抗性提升 II
        boolean lowHealth = player.getHealth() <= player.getMaxHealth() * HOPE_THRESHOLD;
        if (hasHopeBloom(weapon) && lowHealth && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    HOPE_RESISTANCE_DURATION, HOPE_RESISTANCE_AMPLIFIER, true, false, true));
        }

        // 我们逝去，我们永恒：这把人权剑自身常驻的技能，非 BOSS 生物远离玩家
        boolean blue = sword.isEnhanced() && hasWeFallWeRemain(weapon);
        if (blue && player.tickCount % 10 == 0) repelMobs(player);
    }

    /** 让周围非 BOSS、非友方的生物远离玩家。 */
    private static void repelMobs(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(FLEE_RADIUS),
                candidate -> !BOSSES.contains(candidate.getType())
                        && DawnEnchantment.canHit(player, candidate))) {
            Vec3 away = mob.position().subtract(player.position());
            if (away.lengthSqr() < 0.0001D) away = new Vec3(1.0D, 0.0D, 0.0D);
            away = away.normalize().scale(6.0D);
            mob.setTarget(null);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY() + away.y, mob.getZ() + away.z, 1.4D);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer victim) {
            handleInstantKillImmunity(event, victim);
        }

        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        if (source.getDirectEntity() != attacker) return;
        // 剑气伤害与虚空追加伤害已经结算完毕，不再参与近战加成
        if (source.is(VOID_SPARK) || source.is(BLUE_FLAME) || source.is(FREE_WILL_LIGHT)
                || source.is(DawnEnchantment.DAMAGE_TYPE)) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem)) return;

        applyHopeBloom(event, attacker, weapon);
    }

    /** 希望之花：免疫一次即死伤害，冷却 15 分钟。 */
    private static void handleInstantKillImmunity(LivingIncomingDamageEvent event, ServerPlayer player) {
        if (!event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        ItemStack weapon = player.getMainHandItem();
        if (!hasHopeBloom(weapon)) return;

        long gameTime = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        if (data.getLong(INSTANT_KILL_COOLDOWN_TAG) > gameTime) return;

        event.setCanceled(true);
        data.putLong(INSTANT_KILL_COOLDOWN_TAG, gameTime + INSTANT_KILL_COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("endbound_ember.hope_bloom.saved"), true);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.2F, 1.4F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY(0.8D), player.getZ(), 40, 0.6D, 0.8D, 0.6D, 0.25D);
        }
    }

    /**
     * 星火不灭：每次近战命中追加一段虚空伤害，数值为目标最大生命值的 5%/10%/15%。
     * 追加伤害单独结算，因此不会被护甲、保护附魔或抗性削减。
     */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        if (source.getDirectEntity() != attacker) return;
        // 剑气与虚空追加伤害本身不再触发星火不灭
        if (source.is(VOID_SPARK) || source.is(BLUE_FLAME) || source.is(FREE_WILL_LIGHT)
                || source.is(DawnEnchantment.DAMAGE_TYPE)) return;

        LivingEntity target = event.getEntity();
        if (!target.isAlive()) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem)) return;

        int level = levelOf(weapon, ETERNAL_SPARK);
        if (level <= 0) return;

        int index = Math.min(level, ETERNAL_SPARK_RATIOS.length - 1);
        float extra = (float) (target.getMaxHealth() * ETERNAL_SPARK_RATIOS[index]);
        if (extra <= 0.0F) return;

        target.hurt(target.level().damageSources().source(VOID_SPARK, attacker), extra);

        if (target.level() instanceof ServerLevel levelObj) {
            levelObj.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY(0.6D), target.getZ(),
                    20, 0.4D, 0.6D, 0.4D, 0.06D);
            levelObj.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /**
     * 星火不灭的旧版特效：被星火燃尽的目标死亡时，灵魂火以双重扩散环向外炸开。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (dead == attacker) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem)) return;
        if (levelOf(weapon, ETERNAL_SPARK) <= 0) return;
        if (!(dead.level() instanceof ServerLevel level)) return;

        spawnSparkSpread(level, dead.position(), SPARK_SPREAD_RADIUS);
    }

    /** 旧版星火扩散动画：0.6 / 1.0 两道末地烛光环，外环叠加灵魂火，中心爆燃。 */
    private static void spawnSparkSpread(ServerLevel level, Vec3 center, double radius) {
        int perRing = 24;
        for (double ringScale : new double[]{0.6D, 1.0D}) {
            double r = radius * ringScale;
            for (int i = 0; i < perRing; i++) {
                double angle = 2.0D * Math.PI * i / perRing;
                double x = center.x + r * Math.cos(angle);
                double z = center.z + r * Math.sin(angle);
                level.sendParticles(ParticleTypes.END_ROD,
                        x, center.y + 0.2D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (ringScale >= 1.0D) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, center.y + 0.2D, z, 1, 0.0D, 0.05D, 0.0D, 0.05D);
                }
            }
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.5D, center.z, 16, 0.3D, 0.3D, 0.3D, 0.1D);
    }

    /** 希望之花：低血量时近战伤害提升 50%。 */
    private static void applyHopeBloom(LivingIncomingDamageEvent event, ServerPlayer attacker, ItemStack weapon) {
        if (!hasHopeBloom(weapon)) return;
        if (attacker.getHealth() > attacker.getMaxHealth() * HOPE_THRESHOLD) return;
        event.setAmount((float) (event.getAmount() * HOPE_DAMAGE_MULTIPLIER));
    }
}
