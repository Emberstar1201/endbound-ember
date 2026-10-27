package com.emberstar.endbounember.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.CuriosCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
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
 * 星尘魔女的光环能力（戴在头盔栏或 Curios 光环饰品栏时生效）：
 * <ul>
 * <li>虚无中的微光：头部放置 15 级移动光源，攻击恢复速度提升 250%</li>
 * <li>soulless heart：99% 全伤害减伤</li>
 * <li>虚无的存在：全部生物对你中立</li>
 * <li>直面于虚无：免疫虚空与真实伤害，坠落虚空后跨维度送回最近的重生点</li>
 * <li>存在的意义？：每次致命伤免死；改写、强制清除类攻击（如无尽贪婪、莱特兰恶意的强制击杀）一律无效，无法被禁用</li>
 * </ul>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class StardustWitchHaloEvents {
    /** soulless heart：伤害减免至原来的 1%（99% 减伤） */
    private static final float DAMAGE_MULTIPLIER = 0.01F;
    /** 虚无中的微光：攻击恢复速度 +250% 的属性修饰符标识 */
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "stardust_witch_halo_swiftness");
    /** 移动光源：记录每名佩戴者上一次放置光源方块的位置，便于移动后清除 */
    private static final Map<UUID, BlockPos> HALO_LIGHT_POSITIONS = new ConcurrentHashMap<>();

    private StardustWitchHaloEvents() {
    }

    // ===== 直面于虚无：免疫虚空伤害与真实伤害；坠落虚空送回最近的重生点 =====
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!wearingHalo(event.getEntity())) {
            return;
        }
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            // 跨维度送回最近的重生点（床/重生锚），无重生点则回该维度世界出生点
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

    // ===== soulless heart：99% 减伤；存在的意义？：减免后仍致命的伤害直接无效化 =====
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (!wearingHalo(entity)) {
            return;
        }
        // 该事件在所有减伤计算之后触发，直接按原始伤害的 1% 结算
        float reduced = event.getOriginalDamage() * DAMAGE_MULTIPLIER;
        if (reduced >= entity.getHealth()) {
            event.setNewDamage(0.0F);
            return;
        }
        event.setNewDamage(reduced);
    }

    // ===== 存在的意义？：兜底免死，拦截 setHealth(0)/kill() 等改写类强制击杀 =====
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!wearingHalo(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    // ===== 虚无的存在：全部生物对你中立（含被攻击后不反击、碰撞挤压不生效） =====
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget != null && wearingHalo(newTarget)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** 中立补强：生物每 tick 都会被清掉以佩戴者为目标的仇恨，攻击后也不会反击 */
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        LivingEntity entity = event.getEntity() instanceof LivingEntity living ? living : null;
        if (entity == null || entity.level().isClientSide) {
            return;
        }
        LivingEntity target = entity instanceof Mob mob ? mob.getTarget() : null;
        if (target != null && wearingHalo(target)) {
            ((Mob) entity).setTarget(null);
        }
    }

    /**
     * 中立补强：佩戴者被生物攻击时不产生任何伤害（等价于「返回 false，不吃这个伤害」）。
     * <p>
     * 覆盖近战、弹射物、碰撞挤压（史莱姆/岩浆怪）、爆炸等一切由生物发起的伤害；
     * 只有佩戴者自己造成的伤害（如摔落、火焰、虚空等环境伤害）不受影响。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!wearingHalo(victim) || event.getSource().getEntity() == victim) {
            return;
        }
        // 生物发起的伤害：史莱姆挤压（mob_attack_no_aggro）、近战、弹射物、爆炸等
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            event.setCanceled(true);
        }
    }

    // ===== 虚无中的微光：移动光源与攻击恢复速度提升（服务端每 tick 维护） =====
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        boolean wearing = wearingHalo(player);
        updateHaloLight(player, wearing);
        updateAttackSpeed(player, wearing);
    }

    /** 在头部放置 15 级光源方块形成移动光源，移动后清除旧位置 */
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
            // 头部处于实体方块内时不放置，下一 tick 继续尝试
            HALO_LIGHT_POSITIONS.remove(id);
        }
    }

    /** 攻击恢复速度 +250%：佩戴时挂载属性修饰符，摘下时移除 */
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
        // 跨维度场景下不确定光源位于哪个维度，遍历所有已加载维度清除
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

    /** 头盔栏或 Curios 光环饰品栏佩戴均生效 */
    private static boolean wearingHalo(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.STARDUST_WITCH_HALO.get())) {
            return true;
        }
        // Curios 为运行时软依赖，仅在已加载时检查饰品栏，避免未安装时链接错误
        return ModList.get().isLoaded("curios")
                && CuriosCompat.isEquipped(player, EndboundEmber.STARDUST_WITCH_HALO.get());
    }
}
