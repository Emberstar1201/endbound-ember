package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 熵增：近战武器附魔，最高 III 级。
 * 攻击叠加熵标记：2 层减速 15%，满 3 层引爆，造成目标已损失生命值 30% 的真实伤害，
 * 并以 50% 伤害扩散到半径 8 格内的敌对生物，随后标记清零。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EntropyEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "entropy"));

    /** 旧版 EntropyHandler.MAX_STACKS。 */
    private static final int MAX_STACKS = 3;
    /** 旧版 EntropyHandler.LOST_HP_RATIO：已损失生命值的 30%。 */
    private static final double LOST_HEALTH_RATIO = 0.30D;
    /** 旧版 EntropyHandler.SPREAD_RATIO：扩散继承 50% 伤害。 */
    private static final double SPREAD_RATIO = 0.50D;
    /** 旧版 EntropyHandler.SPREAD_RADIUS：8 格扩散半径。 */
    private static final double SPREAD_RADIUS = 8.0D;
    /** 旧版 EntropyHandler.SPEED_REDUCTION：2 层时移动速度 -15%。 */
    private static final double SPEED_REDUCTION = -0.15D;

    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "entropy_slow");

    /** 旧版 EntropyHandler.TAG_STACKS。 */
    private static final String TAG_STACKS = "EndboundEmberEntropyStacks";
    /** 旧版 EntropyHandler.TAG_SPEED_DEBUFF。 */
    private static final String TAG_SLOWED = "EndboundEmberEntropySlowed";

    private static final int TOOLTIP_COLOR = 0x8A7CA8;

    private EntropyEnchantment() {
    }

    /** 读取物品上的熵增附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 近战命中：叠加标记、2 层减速、满层引爆。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player) return;
        if (target == player) return;
        if (levelOf(player.getMainHandItem()) <= 0) return;

        CompoundTag data = target.getPersistentData();
        int stacks = data.getInt(TAG_STACKS) + 1;

        if (stacks < MAX_STACKS) {
            data.putInt(TAG_STACKS, stacks);
            if (stacks == 2) applySpeedDebuff(target);
            return;
        }

        // 满层：引爆，伤害为已损失生命值的 30%（魔法伤害，无视护甲）
        float lostHealth = target.getMaxHealth() - target.getHealth();
        float explosionDamage = lostHealth * (float) LOST_HEALTH_RATIO;
        if (explosionDamage > 0.0F) {
            target.hurt(target.damageSources().magic(), explosionDamage);
            if (target.level() instanceof ServerLevel serverLevel) {
                spreadDamage(serverLevel, target, player, explosionDamage);
                serverLevel.sendParticles(ParticleTypes.WITCH,
                        target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(),
                        40, 1.5D, 0.5D, 1.5D, 0.1D);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(),
                        15, 1.0D, 0.3D, 1.0D, 0.02D);
            }
        }

        clearMarks(target);
    }

    /** 把 50% 的引爆伤害扩散给周围敌对生物。 */
    private static void spreadDamage(ServerLevel level, LivingEntity center, ServerPlayer attacker,
            float explosionDamage) {
        AABB box = AABB.ofSize(center.position(), SPREAD_RADIUS * 2.0D, SPREAD_RADIUS * 2.0D,
                SPREAD_RADIUS * 2.0D);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != center && entity != attacker && entity instanceof Enemy);
        float spreadDamage = explosionDamage * (float) SPREAD_RATIO;
        for (LivingEntity mob : nearby) {
            mob.hurt(mob.damageSources().magic(), spreadDamage);
        }
    }

    private static void applySpeedDebuff(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(TAG_SLOWED)) return;
        AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;
        instance.removeModifier(SPEED_MODIFIER_ID);
        instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, SPEED_REDUCTION,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        data.putBoolean(TAG_SLOWED, true);
    }

    private static void clearMarks(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(TAG_SLOWED)) {
            AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance != null) instance.removeModifier(SPEED_MODIFIER_ID);
        }
        data.remove(TAG_STACKS);
        data.remove(TAG_SLOWED);
    }

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.entropy").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.entropy.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
