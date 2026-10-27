package com.emberstar.endbounember.item;

import java.util.Collection;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.entity.DawnSwordQiEntity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 人权剑 / 人权剑·星火永燃。
 *
 * <p>本体不带耐久，每次普通攻击命中都会随斩击挥出剑气；携带「晨映之花」时剑气伤害提升至 350%，
 * 强化版独有固有技能「人的意志」，剑气自动化为必定暴击、无视护甲的蓝火剑气。</p>
 */
public class SwordOfTheFreeWillItem extends Item {
    /** 剑气最大飞行距离（格）。 */
    public static final double QI_RANGE = 30.0D;
    /** 剑气命中判定的横向容差（格）。 */
    public static final double QI_HIT_TOLERANCE = 1.0D;

    private static final int BASE_COLOR = 0xF2E2B8;
    private static final int ENHANCED_COLOR = 0x6FB8FF;
    /** 首次击杀凋灵后写入玩家持久数据的标记。 */
    private static final String OBTAINED_TAG = "EndboundEmberFreeWillObtained";
    /** 玩家夜间击杀凋灵的次数，用于第二次夜间击杀时授予后续成就。 */
    private static final String NIGHT_WITHER_KILLS_TAG = "EndboundEmberNightWitherKills";
    private static final ResourceLocation FIRST_WITHER_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "we_fall_we_remain");
    private static final ResourceLocation SECOND_WITHER_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "so_called_dawn_will_be_free");
    /** 上一次挥出剑气的时间点，用于同一次横扫命中多个目标时只挥出一道剑气。 */
    private static final String LAST_QI_TICK_TAG = "EndboundEmberLastQiTick";

    private static final ResourceLocation ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_attack_speed");
    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_armor_toughness");

    /** 持剑时受到伤害的减免比例。 */
    public static final double DAMAGE_REDUCTION = 0.25D;
    /** 星火不灭抵挡致命伤害的冷却：300 秒。 */
    public static final int ETERNAL_SPARK_COOLDOWN_TICKS = 6000;
    private static final String SPARK_COOLDOWN_TAG = "EndboundEmberEternalSparkCooldown";

    private final boolean enhanced;

    public SwordOfTheFreeWillItem(boolean enhanced, Properties properties) {
        super(properties);
        this.enhanced = enhanced;
    }

    /** 是否为强化后的「人权剑·星火永燃」。 */
    public boolean isEnhanced() {
        return this.enhanced;
    }

    /** 攻击伤害 / 攻速 / 护甲 / 盔甲韧性均为相对玩家基础值的偏移量，仅在数值非零时写入。 */
    public static ItemAttributeModifiers attributes(double attackDamageBonus, double attackSpeedBonus,
            double armorBonus, double armorToughnessBonus) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, modifier(ATTACK_DAMAGE_ID, attackDamageBonus),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, modifier(ATTACK_SPEED_ID, attackSpeedBonus),
                        EquipmentSlotGroup.MAINHAND);
        if (armorBonus != 0.0D) {
            builder.add(Attributes.ARMOR, modifier(ARMOR_ID, armorBonus), EquipmentSlotGroup.MAINHAND);
        }
        if (armorToughnessBonus != 0.0D) {
            builder.add(Attributes.ARMOR_TOUGHNESS, modifier(ARMOR_TOUGHNESS_ID, armorToughnessBonus),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    private static AttributeModifier modifier(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(this.enhanced ? ENHANCED_COLOR : BASE_COLOR));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        // 拂晓成长数值由 DawnEnchantment 统一追加。
        if (this.enhanced) {
            tooltip.add(Component.translatable("item.endbound_ember.sword_of_the_free_will_enhanced.human_will")
                    .withStyle(style -> style.withColor(ENHANCED_COLOR)));
        }
    }

    /**
     * 首次进入背包时写入旧版人权剑的全部内置附魔。
     * 只在该物品还没有任何附魔时写入，避免覆盖玩家后续自行添加的附魔。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.isEmpty()) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            bless(mutable, level, "minecraft:sharpness", 12);
            bless(mutable, level, "minecraft:smite", 12);
            bless(mutable, level, "minecraft:looting", 12);
            bless(mutable, level, "minecraft:knockback", 2);
            bless(mutable, level, DawnEnchantment.KEY, 1);
            bless(mutable, level, FreeWillEnchantments.ETERNAL_SPARK, 1);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    private static void bless(ItemEnchantments.Mutable mutable, Level level, ResourceKey<Enchantment> key, int value) {
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key)
                .ifPresent(holder -> mutable.set(holder, value));
    }

    private static void bless(ItemEnchantments.Mutable mutable, Level level, String id, int value) {
        bless(mutable, level, ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id)), value);
    }

    /**
     * 随普通攻击挥出剑气：白火为 100%（晨映之花 350%），
     * 强化版固有技能使剑气固定为 1000% 且必定暴击、无视护甲的蓝火。
     */
    private void castQi(ServerLevel level, ServerPlayer player, ItemStack stack) {
        boolean blue = this.enhanced;
        double multiplier = blue
                ? FreeWillEnchantments.BLUE_FLAME_MULTIPLIER
                : (FreeWillEnchantments.hasDawnBloom(stack)
                        ? FreeWillEnchantments.DAWN_BLOOM_MULTIPLIER
                        : 1.0D);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier);
        if (blue) damage = (float) (damage * FreeWillEnchantments.BLUE_CRIT_MULTIPLIER);

        ResourceKey<DamageType> type = blue
                ? FreeWillEnchantments.BLUE_FLAME
                : FreeWillEnchantments.FREE_WILL_LIGHT;
        DamageSource source = level.damageSources().source(type, player);

        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = eye.add(direction.scale(QI_RANGE));

        // 一次性取回剑气路径附近的生物，再按「到视线的垂距」判定命中
        AABB search = new AABB(eye, end).inflate(QI_HIT_TOLERANCE + 1.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                candidate -> DawnEnchantment.canHit(player, candidate))) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
            double along = toTarget.dot(direction);
            double half = target.getBbWidth() * 0.5D + QI_HIT_TOLERANCE;
            if (along < -half || along > QI_RANGE + half) continue;
            if (toTarget.subtract(direction.scale(along)).length() > half) continue;
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        }

        DawnSwordQiEntity qi = new DawnSwordQiEntity(level, player, eye, direction, QI_RANGE);
        qi.setSoulFire(blue);
        level.addFreshEntity(qi);
    }

    /** 凋灵掉落：首次击杀给人权剑并点亮第一阶段成就，之后夜间第二次击杀点亮终章成就并停止掉落。 */
    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        /** 持剑时受到伤害减免 25%；星火不灭抵挡一次致命伤害，冷却 300 秒。 */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().isClientSide) return;
            ItemStack weapon = player.getMainHandItem();
            if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem)) return;

            float amount = (float) (event.getAmount() * (1.0D - DAMAGE_REDUCTION));
            long gameTime = player.level().getGameTime();
            boolean lethal = amount >= player.getHealth();
            if (lethal && !event.getSource().is(DamageTypes.GENERIC_KILL)
                    && FreeWillEnchantments.levelOf(weapon, FreeWillEnchantments.ETERNAL_SPARK) > 0
                    && player.getPersistentData().getLong(SPARK_COOLDOWN_TAG) <= gameTime) {
                // 星火不灭：抵挡本次致命伤害并进入冷却
                event.setCanceled(true);
                player.getPersistentData().putLong(SPARK_COOLDOWN_TAG,
                        gameTime + ETERNAL_SPARK_COOLDOWN_TICKS);
                player.displayClientMessage(
                        Component.translatable("endbound_ember.eternal_spark.saved"), true);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            player.getX(), player.getY(0.8D), player.getZ(), 40, 0.6D, 0.8D, 0.6D, 0.08D);
                    level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                            SoundSource.PLAYERS, 1.2F, 0.6F);
                }
                return;
            }
            event.setAmount(amount);
        }

        /** 近战命中后随斩击挥出剑气。 */
        @SubscribeEvent
        public static void onLivingDamaged(LivingDamageEvent.Post event) {
            if (event.getEntity().level().isClientSide) return;
            var source = event.getSource();
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
            if (source.getDirectEntity() != attacker) return;
            // 剑气与星火不灭的追加伤害不再触发剑气
            if (source.is(FreeWillEnchantments.VOID_SPARK) || source.is(FreeWillEnchantments.BLUE_FLAME)
                    || source.is(FreeWillEnchantments.FREE_WILL_LIGHT)
                    || source.is(DawnEnchantment.DAMAGE_TYPE)) return;

            ItemStack weapon = attacker.getMainHandItem();
            if (!(weapon.getItem() instanceof SwordOfTheFreeWillItem sword)) return;
            if (!(attacker.level() instanceof ServerLevel level)) return;
            // 同一次横扫命中多个目标时只挥出一道剑气
            long gameTime = level.getGameTime();
            var data = attacker.getPersistentData();
            if (data.getLong(LAST_QI_TICK_TAG) == gameTime) return;
            data.putLong(LAST_QI_TICK_TAG, gameTime);

            sword.castQi(level, attacker, weapon);
        }

        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (event.getEntity().getType() != EntityType.WITHER) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            Collection<ItemEntity> drops = event.getDrops();
            var data = player.getPersistentData();
            // 终章成就完成后，人权剑与晨曦之星都不再掉落
            if (hasAdvancement(player, SECOND_WITHER_ADVANCEMENT)) return;

            if (!data.getBoolean(OBTAINED_TAG)) {
                // 首次击杀：先点亮「我们逝去，我们永恒」，再掉落人权剑
                data.putBoolean(OBTAINED_TAG, true);
                awardAdvancement(player, FIRST_WITHER_ADVANCEMENT);
                drops.add(spawnDrop(level, event.getEntity(),
                        new ItemStack(EndboundEmber.SWORD_OF_THE_FREE_WILL.get())));
                return;
            }
            // 旧存档已经拿过人权剑，但成就未点亮时补发第一阶段成就
            if (!hasAdvancement(player, FIRST_WITHER_ADVANCEMENT)) {
                awardAdvancement(player, FIRST_WITHER_ADVANCEMENT);
                return;
            }
            if (!level.isNight()) return;

            // 成就完成后，夜间第二次击杀点亮「所谓破晓，终将自由」
            int nightKills = data.getInt(NIGHT_WITHER_KILLS_TAG) + 1;
            data.putInt(NIGHT_WITHER_KILLS_TAG, nightKills);
            if (nightKills >= 2) awardAdvancement(player, SECOND_WITHER_ADVANCEMENT);
            drops.add(spawnDrop(level, event.getEntity(), new ItemStack(EndboundEmber.DAWN_STAR.get())));
        }

        private static boolean hasAdvancement(ServerPlayer player, ResourceLocation advancementId) {
            var server = player.getServer();
            if (server == null) return false;
            var advancement = server.getAdvancements().get(advancementId);
            return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
        }

        private static void awardAdvancement(ServerPlayer player, ResourceLocation advancementId) {
            var server = player.getServer();
            if (server == null) return;
            var advancement = server.getAdvancements().get(advancementId);
            if (advancement != null) {
                String criterion = advancementId.equals(FIRST_WITHER_ADVANCEMENT)
                        ? "kill_wither_for_free_will"
                        : "second_night_wither_kill";
                player.getAdvancements().award(advancement, criterion);
            }
        }

        private static ItemEntity spawnDrop(ServerLevel level, LivingEntity source, ItemStack stack) {
            ItemEntity item = new ItemEntity(level,
                    source.getX(), source.getY() + 1.0D, source.getZ(), stack);
            // 凋灵死亡爆炸会摧毁掉落物，这里让奖励浮空且免疫伤害
            item.setInvulnerable(true);
            item.setPickUpDelay(10);
            item.setGlowingTag(true);
            return item;
        }
    }
}
