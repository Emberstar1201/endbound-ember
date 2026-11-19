package com.emberstar.endbounember.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.DoomPillarBlock;
import com.emberstar.endbounember.block.DoomPillarBlockEntity;
import com.emberstar.endbounember.compat.FriendlyMobs;
import com.emberstar.endbounember.enchantment.DawnEnchantment;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.entity.DawnSwordQiEntity;
import com.emberstar.endbounember.network.ShockwaveVisualPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
    /** 捡起人权剑时点亮的成就。 */
    private static final ResourceLocation FREE_WILL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "we_fall_we_remain");
    /** 成就判定条件名，与 we_fall_we_remain.json 保持一致 */
    private static final String FREE_WILL_CRITERION = "obtain_free_will";
    /** 上一次挥出剑气的时间点，用于同一次横扫命中多个目标时只挥出一道剑气。 */
    private static final String LAST_QI_TICK_TAG = "EndboundEmberLastQiTick";

    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "free_will_armor_toughness");

    /** 持剑时受到伤害的减免比例。 */
    public static final double DAMAGE_REDUCTION = 0.25D;
    /** 星火不灭抵挡致命伤害的冷却：300 秒。 */
    public static final int ETERNAL_SPARK_COOLDOWN_TICKS = 6000;
    private static final String SPARK_COOLDOWN_TAG = "EndboundEmberEternalSparkCooldown";

    /** 冲击波在十个 tick 内从施法位置扩散到十格，每个目标只受伤一次。 */
    private static final double SHOCKWAVE_RADIUS = 10.0D;
    private static final float SHOCKWAVE_DAMAGE = 50.0F;
    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();
    private static final String LAST_SHOCKWAVE_TICK_TAG = "EndboundEmberLastShockwaveTick";

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
        // 使用原版的 base_attack_damage / base_attack_speed 标识，
        // 这样悬停面板显示的是加上玩家基础值后的总伤害与总攻速，而不是“+19”这类偏移量。
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, modifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamageBonus),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, modifier(Item.BASE_ATTACK_SPEED_ID, attackSpeedBonus),
                        EquipmentSlotGroup.MAINHAND);
        if (armorBonus != 0.0D) {
            builder.add(Attributes.ARMOR, modifier(ARMOR_ID, armorBonus), EquipmentSlotGroup.MAINHAND);
        }
        if (armorToughnessBonus != 0.0D) {
            builder.add(Attributes.ARMOR_TOUGHNESS, modifier(ARMOR_TOUGHNESS_ID, armorToughnessBonus),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build().withTooltip(true);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.endbound_ember.sword_of_the_free_will.shockwave")
                .withStyle(style -> style.withColor(this.enhanced ? ENHANCED_COLOR : BASE_COLOR)));
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
     * 强化版固有技能使剑气固定为 500% 且必定暴击、无视护甲的蓝火。
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

    /** 只启动技能，不消费右键事件，后续的剑格挡与拂晓技能继续照常处理。 */
    private void castShockwave(ServerPlayer player) {
        long time = player.level().getGameTime();
        var data = player.getPersistentData();
        // 双手与方块交互可能在同一 tick 派发多个事件，只合并同一次输入。
        if (data.contains(LAST_SHOCKWAVE_TICK_TAG) && data.getLong(LAST_SHOCKWAVE_TICK_TAG) == time) return;
        data.putLong(LAST_SHOCKWAVE_TICK_TAG, time);
        ServerLevel level = player.serverLevel();
        SHOCKWAVES.add(new Shockwave(level, player.getUUID(), player.position(), this.enhanced));
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    private static final class Shockwave {
        private final ServerLevel level;
        private final UUID owner;
        private final Vec3 origin;
        private final boolean blue;
        private final Set<UUID> hit = new HashSet<>();
        private final List<BlockPos> pillars = new ArrayList<>();
        private int age;

        private Shockwave(ServerLevel level, UUID owner, Vec3 origin, boolean blue) {
            this.level = level;
            this.owner = owner;
            this.origin = origin;
            this.blue = blue;
            // 只枚举已加载区块的方块实体，既不强加载区块，也不逐格扫描整个球体。
            int minX = net.minecraft.util.Mth.floor(origin.x - SHOCKWAVE_RADIUS) >> 4;
            int maxX = net.minecraft.util.Mth.floor(origin.x + SHOCKWAVE_RADIUS) >> 4;
            int minZ = net.minecraft.util.Mth.floor(origin.z - SHOCKWAVE_RADIUS) >> 4;
            int maxZ = net.minecraft.util.Mth.floor(origin.z + SHOCKWAVE_RADIUS) >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof DoomPillarBlockEntity pillar
                                && pillarDistance(pillar.getBlockPos()) <= SHOCKWAVE_RADIUS * SHOCKWAVE_RADIUS) {
                            this.pillars.add(pillar.getBlockPos().immutable());
                        }
                    }
                }
            }
        }

        /** 按柱子的整个三格碰撞高度计算距离，站在柱顶旁也能完整清柱。 */
        private double pillarDistance(BlockPos pos) {
            AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + DoomPillarBlock.HEIGHT, pos.getZ() + 1.0D);
            return box.distanceToSqr(this.origin);
        }

        private boolean tick(ServerPlayer player) {
            double radius = ++this.age;
            double radiusSqr = radius * radius;
            // 保留击杀归属但不伪装成玩家直接近战，避免拂晓成长和暴击放大固定的 50 点伤害。
            DamageSource source = new DamageSource(this.level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FreeWillEnchantments.VOID_SPARK),
                    null, player);
            for (LivingEntity target : this.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(this.origin, this.origin).inflate(radius), candidate -> candidate.isAlive()
                            && !candidate.isSpectator() && !FriendlyMobs.isProtected(candidate)
                            && !player.isAlliedTo(candidate)
                            && (candidate instanceof Enemy || candidate instanceof EnderDragon
                                    || candidate.getType().getCategory() == MobCategory.MONSTER))) {
                if (this.hit.contains(target.getUUID())
                        || target.getBoundingBox().distanceToSqr(this.origin) > radiusSqr) continue;
                this.hit.add(target.getUUID());
                target.invulnerableTime = 0;
                target.hurt(source, SHOCKWAVE_DAMAGE);
                // 直接设置径向冲量，保证击飞效果不被怪物的击退抗性完全抵消。
                Vec3 away = target.position().subtract(this.origin).multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() < 1.0E-6D) away = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() < 1.0E-6D) away = new Vec3(1.0D, 0.0D, 0.0D);
                Vec3 impulse = away.normalize().scale(1.6D);
                target.setDeltaMovement(impulse.x, 0.8D, impulse.z);
                target.hurtMarked = true;
            }
            // 清柱走原有碎裂路径，血条、三段方块与 Boss 分摊判定都会正常清理。
            this.pillars.removeIf(pos -> {
                if (pillarDistance(pos) > radiusSqr) return false;
                if (this.level.hasChunkAt(pos)
                        && this.level.getBlockEntity(pos) instanceof DoomPillarBlockEntity pillar) {
                    pillar.applyDamage(DoomPillarBlockEntity.MAX_HEALTH);
                }
                return true;
            });
            if (this.age == 1) {
                // 只发送一次：由同一批火焰向外运动，不再逐 tick 留下一圈静止粒子。
                PacketDistributor.sendToPlayersNear(this.level, null,
                        this.origin.x, this.origin.y, this.origin.z, 32.0D,
                        new ShockwaveVisualPayload(this.origin.x, this.origin.y + 0.4D, this.origin.z,
                                this.blue, SHOCKWAVE_RADIUS));
            }
            return radius >= SHOCKWAVE_RADIUS;
        }
    }

    /** 人权剑攻击、技能与掉落事件。 */
    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onShockwaveUse(PlayerInteractEvent.RightClickItem event) {
            tryShockwave(event.getEntity(), event.getItemStack());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onShockwaveBlockUse(PlayerInteractEvent.RightClickBlock event) {
            tryShockwave(event.getEntity(), event.getItemStack());
        }

        private static void tryShockwave(Player player, ItemStack stack) {
            if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator() || player.isUsingItem()) return;
            if (stack.getItem() instanceof SwordOfTheFreeWillItem sword) sword.castShockwave(serverPlayer);
        }

        @SubscribeEvent
        public static void onShockwaveTick(ServerTickEvent.Post event) {
            SHOCKWAVES.removeIf(wave -> {
                if (wave.level.getServer() != event.getServer()) return false;
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(wave.owner);
                return player == null || !player.isAlive() || player.level() != wave.level || wave.tick(player);
            });
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            SHOCKWAVES.removeIf(wave -> wave.level.getServer() == event.getServer());
        }

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
                    FreeWillEnchantments.spawnEmberFlame(level,
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

        /** 击杀凋灵掉落人权剑，成就改为捡起人权剑时点亮。 */
        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (event.getEntity().getType() != EntityType.WITHER) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            Collection<ItemEntity> drops = event.getDrops();
            drops.add(spawnDrop(level, event.getEntity(),
                    new ItemStack(EndboundEmber.SWORD_OF_THE_FREE_WILL.get())));
        }

        /** 捡起人权剑即点亮「我们逝去，我们永恒」。 */
        @SubscribeEvent
        public static void onPickup(ItemEntityPickupEvent.Post event) {
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;
            if (!(event.getOriginalStack().getItem() instanceof SwordOfTheFreeWillItem)) return;
            awardAdvancement(player, FREE_WILL_ADVANCEMENT);
        }

        private static void awardAdvancement(ServerPlayer player, ResourceLocation advancementId) {
            var server = player.getServer();
            if (server == null) return;
            var advancement = server.getAdvancements().get(advancementId);
            if (advancement != null) {
                player.getAdvancements().award(advancement, FREE_WILL_CRITERION);
            }
        }

        private static ItemEntity spawnDrop(ServerLevel level, LivingEntity source, ItemStack stack) {
            ItemEntity item = new ItemEntity(level,
                    source.getX(), source.getY() + 1.0D, source.getZ(), stack);
            // 凋灵死亡爆炸会摧毁掉落物，这里让奖励浮空且免疫伤害
            item.setInvulnerable(true);
            item.setPickUpDelay(10);
            item.setGlowingTag(true);
            // 物品本身已声明 fireResistant，这里再清掉可能残留的燃烧状态
            item.setRemainingFireTicks(0);
            return item;
        }
    }
}
