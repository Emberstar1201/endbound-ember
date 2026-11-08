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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 兵长的回声：武器附魔，最高 V 级。
 * 攻击同一目标叠加回声标记，满 3 层引爆，造成目标最大生命值
 * 5% + (等级-1) × 2% 的魔法伤害；目标最大生命值需 ≥ 50。
 * 标记 100 tick 未续期则重置。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class LevisEchoEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "levis_echo"));

    /** 旧版 Config.levisEchoMaxStacks 默认值。 */
    private static final int MAX_STACKS = 3;
    /** 旧版 Config.levisEchoMarkTimeout 默认值：100 tick。 */
    private static final int MARK_TIMEOUT_TICKS = 100;
    /** 旧版 Config.levisEchoHealthThreshold 默认值：50 点（25 颗心）。 */
    private static final double HEALTH_THRESHOLD = 50.0D;
    /** 旧版 Config.levisEchoDamageBasePercent 默认值：5%。 */
    private static final double DAMAGE_BASE_PERCENT = 5.0D;
    /** 旧版 Config.levisEchoDamagePerLevelPercent 默认值：每级 +2%。 */
    private static final double DAMAGE_PER_LEVEL_PERCENT = 2.0D;

    /** 旧版 LevisEchoHandler.TAG_ROOT。 */
    private static final String TAG_ROOT = "EndboundEmberLevisEcho";
    /** 旧版 LevisEchoHandler.KEY_STACKS。 */
    private static final String KEY_STACKS = "stacks";
    /** 旧版 LevisEchoHandler.KEY_LAST_HIT。 */
    private static final String KEY_LAST_HIT = "lastHitTime";

    private static final int TOOLTIP_COLOR = 0x7A5FA8;

    private LevisEchoEnchantment() {
    }

    /** 读取物品上的回声附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 命中叠加标记；满层引爆魔法伤害。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        int enchantLevel = levelOf(weapon);
        if (enchantLevel <= 0) return;

        if (target.getMaxHealth() < HEALTH_THRESHOLD) return;

        CompoundTag root = target.getPersistentData();
        CompoundTag data = root.getCompound(TAG_ROOT);
        int currentStacks = data.getInt(KEY_STACKS);
        long gameTime = target.level().getGameTime();

        if (currentStacks > 0) {
            long lastHit = data.getLong(KEY_LAST_HIT);
            if (gameTime - lastHit > MARK_TIMEOUT_TICKS) currentStacks = 0;
        }

        currentStacks++;
        data.putInt(KEY_STACKS, currentStacks);
        data.putLong(KEY_LAST_HIT, gameTime);
        root.put(TAG_ROOT, data);

        if (target.level() instanceof ServerLevel serverLevel) {
            sendMarkParticles(serverLevel, target, currentStacks);
        }

        if (currentStacks >= MAX_STACKS) {
            root.remove(TAG_ROOT);

            double damagePercent = DAMAGE_BASE_PERCENT + (enchantLevel - 1) * DAMAGE_PER_LEVEL_PERCENT;
            float damage = (float) (target.getMaxHealth() * (damagePercent / 100.0D));
            if (damage > 0.0F) {
                target.hurt(target.damageSources().magic(), damage);
            }
        }
    }

    private static void sendMarkParticles(ServerLevel serverLevel, LivingEntity target, int stacks) {
        Vec3 targetPos = target.position();
        if (stacks == 1) {
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    targetPos.x, targetPos.y + target.getBbHeight() * 0.5D, targetPos.z,
                    8, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.5D,
                    0.1D);
        } else if (stacks == 2) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    targetPos.x, targetPos.y + target.getBbHeight() * 0.6D, targetPos.z,
                    12, target.getBbWidth() * 0.6D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.6D,
                    0.05D);
        } else {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    targetPos.x, targetPos.y + target.getBbHeight() * 0.5D, targetPos.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);

            double baseRadius = 0.5D;
            double maxRadius = 3.0D;
            int rings = 3;
            int particlesPerRing = 16;
            for (int ring = 0; ring < rings; ring++) {
                double radius = baseRadius + (maxRadius - baseRadius) * ring / (rings - 1);
                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = 2.0D * Math.PI * i / particlesPerRing;
                    double x = targetPos.x + radius * Math.cos(angle);
                    double z = targetPos.z + radius * Math.sin(angle);
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                            x, targetPos.y + target.getBbHeight() * 0.5D + 0.2D, z,
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.levis_echo").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.levis_echo.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
