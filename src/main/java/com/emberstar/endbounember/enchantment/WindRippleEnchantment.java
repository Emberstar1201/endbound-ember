package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 风踏涟漪：靴子专属，最高 I 级。
 * 陆上获得明显加速；水面与岩浆表面可如履平地，站在岩浆上时不会被点燃。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class WindRippleEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "wind_ripple"));

    private static final ResourceLocation SPEED_ID = modifierId("wind_ripple_speed");

    private static final double LAND_MULTIPLIER = 1.5D;
    private static final double WATER_MULTIPLIER = 1.25D;
    private static final double LAVA_MULTIPLIER = 1.15D;

    /** 允许玩家脚底高出流体表面的距离（下落接住）。 */
    private static final double SURFACE_TOLERANCE_ABOVE = 0.5D;
    /** 允许玩家脚底低于流体表面的距离（穿过表面后拖回）。 */
    private static final double SURFACE_TOLERANCE_BELOW = 1.5D;

    private static final int TOOLTIP_COLOR = 0xC9A66B;

    private WindRippleEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 读取物品上的风踏涟漪附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        int level = levelOf(player.getItemBySlot(EquipmentSlot.FEET));

        double multiplier = 1.0D;
        if (level > 0 && !player.isFallFlying() && !player.isPassenger() && !player.getAbilities().flying) {
            multiplier = moveOnSurface(player);
        }

        // 属性修饰符只在服务端维护，客户端由同步的属性值获得结果
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            manageSpeedModifier(serverPlayer, multiplier);
        }
    }

    /** 根据脚下流体决定行走方式并处理表面托举，返回当前应使用的移速倍率。 */
    private static double moveOnSurface(Player player) {
        Double lavaSurface = surfaceY(player, Fluids.LAVA);
        if (lavaSurface != null) {
            alignToSurface(player, lavaSurface);
            player.setRemainingFireTicks(0);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.LAVA);
            return LAVA_MULTIPLIER;
        }

        Double waterSurface = surfaceY(player, Fluids.WATER);
        if (waterSurface != null) {
            alignToSurface(player, waterSurface);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.FALLING_WATER);
            return WATER_MULTIPLIER;
        }

        // 完全沉入水中时不给加成，交给原版游泳
        if (player.isInWater()) return 1.0D;

        if (player.onGround()) spawnLandParticles(player);
        return LAND_MULTIPLIER;
    }

    /**
     * 计算玩家脚下的流体表面高度；不在表面附近时返回 null。
     * 两种识别方式：脚下方块是流体且双脚不在流体内，或双脚在流体内且上方是空气。
     */
    private static Double surfaceY(Player player, Fluid fluid) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        BlockState feetState = level.getBlockState(feet);

        double surfaceY;
        if (feetState.getFluidState().is(fluid)) {
            if (!level.getBlockState(feet.above()).isAir()) return null;
            surfaceY = feet.getY() + 1.0D;
        } else if (level.getBlockState(feet.below()).getFluidState().is(fluid)) {
            surfaceY = feet.getY();
        } else {
            return null;
        }

        double feetY = player.getY();
        if (feetY < surfaceY - SURFACE_TOLERANCE_BELOW || feetY > surfaceY + SURFACE_TOLERANCE_ABOVE) {
            return null;
        }
        return surfaceY;
    }

    /** 把玩家固定在流体表面；正在主动上升（跳跃）时不拉回，保留起跳手感。 */
    private static void alignToSurface(Player player, double surfaceY) {
        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0.0D) return;

        if (Math.abs(player.getY() - surfaceY) > 0.001D) {
            player.setPos(player.getX(), surfaceY, player.getZ());
        }
        player.setDeltaMovement(motion.x, 0.0D, motion.z);
    }

    /** 按目标倍率维护移速修饰符，倍率为 1 时移除。 */
    private static void manageSpeedModifier(ServerPlayer player, double multiplier) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(SPEED_ID);
        if (multiplier <= 1.0D) {
            if (existing != null) instance.removeModifier(SPEED_ID);
            return;
        }

        double amount = multiplier - 1.0D;
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(SPEED_ID);
        instance.addTransientModifier(
                new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static boolean shouldSpawn(Player player, int interval, double minSpeedSqr) {
        if (!player.level().isClientSide()) return false;
        if (player.level().getGameTime() % interval != 0L) return false;
        Vec3 motion = player.getDeltaMovement();
        return motion.x * motion.x + motion.z * motion.z >= minSpeedSqr;
    }

    private static void spawnFluidParticles(Player player, ParticleOptions particle) {
        if (!shouldSpawn(player, 3, 0.01D)) return;

        Level level = player.level();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        for (int i = 0; i < 2; i++) {
            double ox = (level.random.nextDouble() - 0.5D) * 0.4D;
            double oz = (level.random.nextDouble() - 0.5D) * 0.4D;
            level.addParticle(particle, px + ox, py + 0.05D, pz + oz, 0.0D, 0.05D, 0.0D);
        }
    }

    private static void spawnLandParticles(Player player) {
        if (!shouldSpawn(player, 2, 0.015D)) return;

        Level level = player.level();
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal < 0.001D) horizontal = 0.001D;

        BlockState ground = level.getBlockState(player.blockPosition().below());
        ParticleOptions particle = ground.isAir()
                ? ParticleTypes.POOF
                : new BlockParticleOption(ParticleTypes.BLOCK, ground);

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        for (int i = 0; i < 2; i++) {
            double backX = -(motion.x / horizontal) * 0.2D;
            double backZ = -(motion.z / horizontal) * 0.2D;
            double randX = (level.random.nextDouble() - 0.5D) * 0.3D;
            double randZ = (level.random.nextDouble() - 0.5D) * 0.3D;
            level.addParticle(particle, px + backX + randX, py + 0.01D, pz + backZ + randZ,
                    (level.random.nextDouble() - 0.5D) * 0.1D,
                    level.random.nextDouble() * 0.05D + 0.02D,
                    (level.random.nextDouble() - 0.5D) * 0.1D);
        }
    }

    /** 物品提示：在附魔名称之后追加移速说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.wind_ripple").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.wind_ripple.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.wind_ripple.land_speed",
                String.format(Locale.ROOT, "%.0f", LAND_MULTIPLIER * 100.0D)));
        tooltip.add(insertAt + 2, line("endbound_ember.wind_ripple.water_speed",
                String.format(Locale.ROOT, "%.0f", WATER_MULTIPLIER * 100.0D)));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
