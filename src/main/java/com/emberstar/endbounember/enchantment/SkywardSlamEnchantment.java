package com.emberstar.endbounember.enchantment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 浮空重击：重锤专属，最高 III 级。
 * 重锤命中生物时把目标向上抛起，目标落地时再结算一次「余震」伤害。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SkywardSlamEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "skyward_slam"));

    private static final double LAUNCH_BASE = 0.8D;
    private static final double LAUNCH_PER_LEVEL = 0.2D;
    private static final float AFTERSHOCK_BASE = 3.0F;
    private static final float AFTERSHOCK_PER_LEVEL = 3.0F;
    /** 抛起状态的最长保留时间，避免目标卡在空中导致状态永久残留。 */
    private static final int EXPIRE_TICKS = 100;
    private static final int TOOLTIP_COLOR = 0xC9A66B;

    /** 已被抛起、等待落地结算余震的目标。 */
    private static final Map<UUID, PendingSlam> PENDING = new HashMap<>();

    private SkywardSlamEnchantment() {
    }

    /** 读取物品上的浮空重击附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 重锤命中后登记抛起状态，实际速度改动在下一 tick 执行，避免被原版击退覆盖。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;

        int level = levelOf(attacker.getMainHandItem());
        if (level <= 0) return;
        if (!(target.level() instanceof ServerLevel serverLevel)) return;
        if (target instanceof Player player && player.isCreative()) return;

        PENDING.put(target.getUUID(), new PendingSlam(
                serverLevel.getGameTime(),
                LAUNCH_BASE + LAUNCH_PER_LEVEL * level,
                AFTERSHOCK_BASE + AFTERSHOCK_PER_LEVEL * level));
    }

    /** 下一 tick 把目标抛起；目标落地时结算余震伤害。 */
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;

        PendingSlam slam = PENDING.get(entity.getUUID());
        if (slam == null) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            PENDING.remove(entity.getUUID());
            return;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            PENDING.remove(entity.getUUID());
            return;
        }
        if (serverLevel.getGameTime() - slam.startTime() > EXPIRE_TICKS) {
            PENDING.remove(entity.getUUID());
            return;
        }

        if (!slam.launched()) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, slam.launchPower(), entity.getDeltaMovement().z);
            entity.hurtMarked = true;
            PENDING.put(entity.getUUID(), slam.withLaunched());
            return;
        }

        if (!entity.onGround()) return;
        PENDING.remove(entity.getUUID());
        // 用无来源的魔法伤害，避免再次触发本附魔
        entity.hurt(serverLevel.damageSources().magic(), slam.aftershockDamage());

        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                entity.getX(), entity.getY() + 0.2D, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 物品提示：在附魔名称之后追加余震伤害说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.skyward_slam").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.skyward_slam.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.skyward_slam.aftershock",
                String.format(Locale.ROOT, "%.0f", AFTERSHOCK_BASE + AFTERSHOCK_PER_LEVEL * level)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }

    /** 单个目标的抛起与余震状态。 */
    private record PendingSlam(long startTime, double launchPower, float aftershockDamage, boolean launched) {
        private PendingSlam(long startTime, double launchPower, float aftershockDamage) {
            this(startTime, launchPower, aftershockDamage, false);
        }

        private PendingSlam withLaunched() {
            return new PendingSlam(startTime, launchPower, aftershockDamage, true);
        }
    }
}
