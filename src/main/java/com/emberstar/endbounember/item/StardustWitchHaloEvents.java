package com.emberstar.endbounember.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 光环通用事件（星尘光环 + 时光光环共享），细分两种光环专属规则：
 * <p>
 * <b>星尘光环</b>：虚无中的微光（移动光源 + 攻击速度 +250%）、创造模式飞行、99% 全伤害减伤、
 * 生物强制中立、免疫虚空 / 真实伤害、致命伤免死。
 * <br>
 * <b>时光光环</b>：创造模式飞行、50% 伤害减伤、维度传送轮盘（见 {@code TimeTurbulenceLevels}）。
 * <p>
 * <b>两者共有</b>：挖掘惩罚（水下 / 悬空）免疫、移动减速效果与负向移速修饰符清除。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class StardustWitchHaloEvents {
    /** 星尘光环 soulless heart：伤害减免至原来的 1%（99% 减伤） */
    private static final float STARDUST_DAMAGE_MULTIPLIER = 0.01F;
    /** 时光光环：伤害减免至原来的 50% */
    private static final float TIME_DAMAGE_MULTIPLIER = 0.5F;

    /** 虚无中的微光：攻击恢复速度 +250% 的属性修饰符标识（仅星尘光环） */
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "stardust_witch_halo_swiftness");
    /** 移动光源：记录每名星尘光环佩戴者上一次放置光源方块的位置，便于移动后清除 */
    private static final Map<UUID, BlockPos> HALO_LIGHT_POSITIONS = new ConcurrentHashMap<>();

    /** 飞行：佩戴者当前是否处于「光环授予飞行」状态（两种光环共用同一份状态） */
    private static final String FLIGHT_GRANTED = "EndboundEmberHaloFlightGranted";
    /** 飞行：授予飞行前是否本来就能飞（创造模式等） */
    private static final String FLIGHT_PREV_MAYFLY = "EndboundEmberHaloFlightPrevMayfly";
    /** 飞行：授予飞行前是否正在飞 */
    private static final String FLIGHT_PREV_FLYING = "EndboundEmberHaloFlightPrevFlying";

    private StardustWitchHaloEvents() {
    }

    // ===== 光环判定助手 =====
    /** 是否佩戴星尘光环（头盔栏或 Curios） */
    public static boolean wearingStardustHalo(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.STARDUST_WITCH_HALO.get())) {
            return true;
        }
        return ModList.get().isLoaded("curios")
                && CuriosCompat.isEquipped(player, EndboundEmber.STARDUST_WITCH_HALO.get());
    }

    /** 是否佩戴时光光环（头盔栏或 Curios） */
    public static boolean wearingTimeHalo(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return TimeTurbulenceLevels.wearsTimeHalo(player);
    }

    /** 两种光环任意其一佩戴 */
    private static boolean wearingAnyHalo(LivingEntity entity) {
        return wearingStardustHalo(entity) || wearingTimeHalo(entity);
    }

    // ===== 直面于虚无（仅星尘光环）：免疫虚空伤害与真实伤害；坠落虚空送回最近的重生点 =====
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!wearingStardustHalo(event.getEntity())) {
            return;
        }
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                returnToNearestSpawn(serverPlayer);
            }
            return;
        }
        // 真实伤害：绕过护甲、抗性、无敌帧的伤害类型一律无效（含 generic_kill 等强制击杀伤害）
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypeTags.BYPASSES_EFFECTS)
                || event.getSource().is(DamageTypeTags.BYPASSES_RESISTANCE)
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
        }
    }

    // ===== 减伤（两种光环独立）：星尘 1%、时光 50% =====
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (wearingStardustHalo(entity)) {
            float reduced = event.getOriginalDamage() * STARDUST_DAMAGE_MULTIPLIER;
            if (reduced >= entity.getHealth()) {
                event.setNewDamage(0.0F);
                return;
            }
            event.setNewDamage(reduced);
        } else if (wearingTimeHalo(entity)) {
            float reduced = event.getOriginalDamage() * TIME_DAMAGE_MULTIPLIER;
            event.setNewDamage(reduced);
        }
    }

    // ===== 存在的意义？（仅星尘光环）：兜底免死，拦截 setHealth(0)/kill() 等强制击杀 =====
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!wearingStardustHalo(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    // ===== 虚无的存在（仅星尘光环）：生物对佩戴者保持强制中立 =====
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null || !wearingStardustHalo(newTarget)) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && isProvokedBy(mob, newTarget)) {
            return;
        }
        event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        LivingEntity entity = event.getEntity() instanceof LivingEntity living ? living : null;
        if (entity == null || entity.level().isClientSide || !(entity instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && wearingStardustHalo(target) && !isProvokedBy(mob, target)) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!wearingStardustHalo(victim) || event.getSource().getEntity() == victim) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            if (attacker instanceof Mob mob && isProvokedBy(mob, victim)) {
                return;
            }
            event.setCanceled(true);
        }
    }

    private static boolean isProvokedBy(Mob mob, LivingEntity haloWearer) {
        return mob.getLastHurtByMob() == haloWearer || mob.getLastHurtMob() == haloWearer;
    }

    // ===== 每 tick 维护：飞行（两光环）、光源（仅星尘）、攻击速度（仅星尘）、惩罚免疫（两光环） =====
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        boolean stardust = wearingStardustHalo(player);
        boolean time = wearingTimeHalo(player);
        boolean amulet = AnahitaFirstlightLiddellItem.isEquipped(player);
        boolean any = stardust || time;

        updateFlight(player, any);
        // 惩罚免疫：光环或上位护符任一佩戴即可
        updatePenaltyClearing(player, any || amulet);
        // 上位护符同样提供随身照明，与光环共用同一份光源跟踪
        updateHaloLight(player, stardust || amulet);
        updateAttackSpeed(player, stardust);
    }

    /**
     * 创造模式飞行：两种光环均可授予。摘下光环（两种都不戴）后恢复原状态。
     * <p>
     * 创造与旁观模式的飞行由原版管理，这里不覆盖其状态，避免摘下光环后把创造模式的飞行能力一并收回。
     */
    private static void updateFlight(Player player, boolean wearing) {
        CompoundTag data = player.getPersistentData();
        boolean granted = data.getBoolean(FLIGHT_GRANTED);

        if (player.isCreative() || player.isSpectator()) {
            if (granted) {
                data.remove(FLIGHT_GRANTED);
                data.remove(FLIGHT_PREV_MAYFLY);
                data.remove(FLIGHT_PREV_FLYING);
            }
            return;
        }

        if (wearing && !granted) {
            data.putBoolean(FLIGHT_PREV_MAYFLY, player.getAbilities().mayfly);
            data.putBoolean(FLIGHT_PREV_FLYING, player.getAbilities().flying);
            data.putBoolean(FLIGHT_GRANTED, true);
        }

        if (wearing) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            return;
        }

        if (granted) {
            if (!data.getBoolean(FLIGHT_PREV_MAYFLY)) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            data.remove(FLIGHT_GRANTED);
            data.remove(FLIGHT_PREV_MAYFLY);
            data.remove(FLIGHT_PREV_FLYING);
        }
    }

    /**
     * 惩罚免疫：清除挖掘疲劳、缓慢等原版负面移速 / 挖掘减速效果，
     * 以及任何负向的 MOVEMENT_SPEED 属性修饰符（药水、模组等来源）。
     */
    private static void updatePenaltyClearing(Player player, boolean wearing) {
        if (!wearing) {
            return;
        }
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
        }
        if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            List<ResourceLocation> negatives = new ArrayList<>();
            for (AttributeModifier modifier : moveSpeed.getModifiers()) {
                if (modifier.amount() < 0.0D) {
                    negatives.add(modifier.id());
                }
            }
            for (ResourceLocation id : negatives) {
                moveSpeed.removeModifier(id);
            }
        }
    }

    /** 挖掘速度事件：两种光环都免疫水下 / 悬空惩罚（抵消原版 ÷5 的逻辑） */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!wearingAnyHalo(player) && !AnahitaFirstlightLiddellItem.isEquipped(player)) {
            return;
        }
        float speed = event.getNewSpeed();
        // 水中惩罚：除以原版算出来的「水下挖掘速度」（若有水下速掘则 ≈1，相除等于 1；默认为 0.2）
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submerged = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submerged > 0.0D) {
                speed /= (float) submerged;
            }
        }
        // 空中惩罚：原版在离地时除以 5，这里乘回去
        if (!player.onGround()) {
            speed *= 5.0F;
        }
        event.setNewSpeed(speed);
    }

    /** 在头部放置 15 级光源方块形成移动光源，移动后清除旧位置（仅星尘光环） */
    private static void updateHaloLight(Player player, boolean wearing) {
        UUID id = player.getUUID();
        BlockPos last = HALO_LIGHT_POSITIONS.get(id);
        if (!wearing) {
            if (last != null) {
                clearLight(player.level(), last);
                HALO_LIGHT_POSITIONS.remove(id);
            }
            return;
        }
        BlockPos headPos = BlockPos.containing(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        if (headPos.equals(last)) {
            return;
        }
        if (last != null) {
            clearLight(player.level(), last);
        }
        Level level = player.level();
        BlockState current = level.getBlockState(headPos);
        if (current.isAir() || current.is(Blocks.LIGHT)) {
            level.setBlock(headPos, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 15), 3);
            HALO_LIGHT_POSITIONS.put(id, headPos);
        } else {
            HALO_LIGHT_POSITIONS.remove(id);
        }
    }

    /** 攻击恢复速度 +250%（仅星尘光环）：佩戴时挂载属性修饰符，摘下时移除 */
    private static void updateAttackSpeed(Player player, boolean wearing) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        boolean has = attackSpeed.hasModifier(ATTACK_SPEED_ID);
        if (wearing && !has) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    ATTACK_SPEED_ID, 2.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!wearing && has) {
            attackSpeed.removeModifier(ATTACK_SPEED_ID);
        }
    }

    // ===== 移动光源的善后清理：登出、跨维度、重生时清除遗留光源 =====
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearTrackedLight(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearTrackedLight(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearTrackedLight(event.getEntity());
    }

    private static void clearTrackedLight(Player player) {
        BlockPos last = HALO_LIGHT_POSITIONS.remove(player.getUUID());
        if (last == null || player.getServer() == null) {
            return;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            clearLight(level, last);
        }
    }

    private static void clearLight(Level level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 跨维度送回最近的重生点（床/重生锚），无重生点时回到重生维度对应的世界出生点 */
    private static void returnToNearestSpawn(ServerPlayer player) {
        ResourceKey<Level> dimension = player.getRespawnDimension();
        ServerLevel target = player.server.getLevel(dimension);
        if (target == null) {
            target = player.server.overworld();
        }
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) {
            spawn = target.getSharedSpawnPos();
        }
        player.teleportTo(target, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }
}
