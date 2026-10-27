package com.emberstar.endbounember.enchantment;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.DawnEnergyClient;
import com.emberstar.endbounember.compat.FriendlyMobs;
import com.emberstar.endbounember.entity.DawnSwordQiEntity;
import com.emberstar.endbounember.item.SwordOfTheFreeWillItem;
import com.emberstar.endbounember.network.DawnEnergyPayload;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Vector3f;

/**
 * 拂晓附魔的全部逻辑：击杀成长、四维加成、能量与「刺破长夜的黎明」。
 * 附魔等级直接写在物品的附魔组件上（武器用 ENCHANTMENTS，附魔书用 STORED_ENCHANTMENTS），等级 1~100。
 */
public final class DawnEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dawn"));
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dawn_light"));

    public static final int MAX_LEVEL = 100;
    public static final int MAX_ENERGY = 100;
    /** 第一次进入世界时发放拂晓并授予「踏上旅途」。 */
    public static final ResourceLocation JOURNEY_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dawn_journey");
    public static final String JOURNEY_CRITERION = "obtain_dawn";

    private static final int ENERGY_PER_HIT = 2;
    private static final int ENERGY_PER_KILL = 10;
    /** 剑气最大飞行距离（格）。 */
    private static final double SKILL_RANGE = 15.0D;
    /** 剑气命中判定的横向容差（格）。 */
    private static final double HIT_TOLERANCE = 1.0D;
    private static final int STAT_COLOR = 0xFFE9A8;
    private static final int SKILL_COLOR = 0xFFF4D6;

    private static final ResourceLocation RANGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dawn_attack_range");
    private static final String ENERGY_TAG = "EndboundEmberDawnEnergy";
    private static final String RECEIVED_TAG = "EndboundEmberDawnReceived";

    private DawnEnchantment() {
    }

    /**
     * 读取物品的拂晓成长等级。
     *
     * 附魔等级本身固定为 1 级（保证物品提示里只显示「拂晓」），
     * 成长层数存放在独立的 {@link EndboundEmber#DAWN_LEVEL} 组件里。
     */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty() || !hasDawn(stack)) return 0;
        Integer growth = stack.get(EndboundEmber.DAWN_LEVEL.get());
        if (growth == null) return 1;
        return Math.max(1, Math.min(MAX_LEVEL, growth));
    }

    /** 物品是否带有拂晓附魔（武器看 ENCHANTMENTS，附魔书看 STORED_ENCHANTMENTS）。 */
    private static boolean hasDawn(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && levelIn(enchantments) > 0) return true;
        enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return enchantments != null && levelIn(enchantments) > 0;
    }

    private static int levelIn(ItemEnchantments enchantments) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /** 以主手武器判定当前拂晓等级。 */
    public static int levelOf(Player player) {
        return levelOf(player.getMainHandItem());
    }

    public static double damageMultiplier(int level) {
        return 1.0D + stat(level, 0.20D, 10.0D);
    }

    public static double critChance(int level) {
        return Math.min(1.0D, stat(level, 0.05D, 1.00D));
    }

    public static double critMultiplier(int level) {
        return 1.0D + stat(level, 0.50D, 1.50D);
    }

    public static double rangeBonus(int level) {
        return stat(level, 1.0D, 10.0D);
    }

    /** 等级 0 为 0，等级 1 为 atOne，等级 100 为 atMax，中间线性增长。 */
    private static double stat(int level, double atOne, double atMax) {
        if (level <= 0) return 0.0D;
        if (level >= MAX_LEVEL) return atMax;
        return atOne + (atMax - atOne) * (level - 1) / (double) (MAX_LEVEL - 1);
    }

    private static Holder<Enchantment> holder(Level level) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(KEY).orElse(null);
    }

    /** 确保物品上的拂晓附魔等级为 1 级；附魔书写入 STORED_ENCHANTMENTS，其它物品写入 ENCHANTMENTS。 */
    private static void ensureLevelOne(ServerPlayer player, ItemStack stack) {
        Holder<Enchantment> holder = holder(player.level());
        if (holder == null) return;
        boolean isBook = stack.is(Items.ENCHANTED_BOOK);
        DataComponentType<ItemEnchantments> componentType =
                isBook ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
        ItemEnchantments current = stack.getOrDefault(componentType, ItemEnchantments.EMPTY);
        if (levelIn(current) == 1) return;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, 1);
        stack.set(componentType, mutable.toImmutable());
    }

    /** 写入拂晓成长层数。 */
    private static void setGrowthLevel(ServerPlayer player, ItemStack stack, int value) {
        ensureLevelOne(player, stack);
        stack.set(EndboundEmber.DAWN_LEVEL.get(), Math.max(1, Math.min(MAX_LEVEL, value)));
    }

    /**
     * 旧存档迁移：早期版本把成长层数直接写在附魔等级上，
     * 这里把超出的等级搬进成长组件，让附魔等级回到固定的 1 级。
     * 武器与附魔书的附魔组件都会处理。
     */
    private static void migrateLegacyLevel(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (levelIn(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)) <= 1
                && levelIn(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)) <= 1) {
            return;
        }
        Holder<Enchantment> holder = holder(player.level());
        if (holder == null) return;

        migrateLegacyComponent(stack, DataComponents.ENCHANTMENTS, holder);
        migrateLegacyComponent(stack, DataComponents.STORED_ENCHANTMENTS, holder);
    }

    private static void migrateLegacyComponent(ItemStack stack,
            DataComponentType<ItemEnchantments> componentType, Holder<Enchantment> holder) {
        ItemEnchantments enchantments = stack.get(componentType);
        if (enchantments == null) return;
        int enchantLevel = levelIn(enchantments);
        if (enchantLevel <= 1) return;

        // 武器的旧等级搬进成长组件；附魔书只把等级降回 1
        if (componentType == DataComponents.ENCHANTMENTS) {
            int growth = stack.getOrDefault(EndboundEmber.DAWN_LEVEL.get(), enchantLevel);
            stack.set(EndboundEmber.DAWN_LEVEL.get(), Math.max(1, Math.min(MAX_LEVEL, growth)));
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        mutable.set(holder, 1);
        stack.set(componentType, mutable.toImmutable());
    }

    /** 构建一本带 1 级拂晓附魔的附魔书（成长层数从 1 开始）。 */
    public static ItemStack createEnchantedBook(ServerPlayer player, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ensureLevelOne(player, book);
        book.set(EndboundEmber.DAWN_LEVEL.get(), Math.max(1, Math.min(MAX_LEVEL, level)));
        return book;
    }

    private static int energy(Player player) {
        return player.getPersistentData().getInt(ENERGY_TAG);
    }

    private static void addEnergy(Player player, int amount) {
        CompoundTag data = player.getPersistentData();
        data.putInt(ENERGY_TAG, Math.min(MAX_ENERGY, data.getInt(ENERGY_TAG) + amount));
    }

    private static void resetEnergy(Player player) {
        player.getPersistentData().putInt(ENERGY_TAG, 0);
    }

    private static void updateRange(Player player, int level) {
        AttributeInstance instance = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(RANGE_MODIFIER_ID);
        if (level <= 0) {
            if (existing != null) instance.removeModifier(RANGE_MODIFIER_ID);
            return;
        }
        double bonus = rangeBonus(level);
        if (existing != null && existing.amount() == bonus) return;
        if (existing != null) instance.removeModifier(RANGE_MODIFIER_ID);
        instance.addTransientModifier(new AttributeModifier(RANGE_MODIFIER_ID, bonus,
                AttributeModifier.Operation.ADD_VALUE));
    }

    /** 快捷键或自动触发的入口：只有满能量时才会真正释放。 */
    public static void tryCast(ServerPlayer player) {
        if (levelOf(player) <= 0) return;
        if (energy(player) < MAX_ENERGY) {
            player.displayClientMessage(Component.translatable("endbound_ember.dawn.not_ready",
                    String.valueOf(energy(player)), String.valueOf(MAX_ENERGY)), true);
            return;
        }
        cast(player);
    }

    /** 右键释放入口：主手带拂晓且能量已满时射出剑气，返回是否拦截了这次右键。 */
    private static boolean tryCastFromMainHand(Player entity, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return false;
        // 人权剑的剑气随普攻挥出，右键不触发拂晓技能
        if (entity.getMainHandItem().getItem() instanceof SwordOfTheFreeWillItem) return false;
        if (!(entity instanceof ServerPlayer player)) return false;
        if (levelOf(player) <= 0) return false;
        if (energy(player) < MAX_ENERGY) return false;
        cast(player);
        return true;
    }

    /** 刺破长夜的黎明：朝视线方向射出穿透方块、不衰减伤害的剑气。 */
    private static void cast(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        int growth = levelOf(player);
        if (growth <= 0) return;

        resetEnergy(player);

        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (attack * (4.0D + growth * 0.06D));
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        DamageSource source = level.damageSources().source(DAMAGE_TYPE, player);

        // 沿视线以 0.5 格步长扫描，保证穿透路径上的每个目标都被命中一次
        Set<UUID> hit = new HashSet<>();
        for (double travelled = 0.0D; travelled <= SKILL_RANGE; travelled += 0.5D) {
            Vec3 point = eye.add(direction.scale(travelled));
            AABB area = new AABB(point, point).inflate(HIT_TOLERANCE);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    candidate -> canHit(player, candidate))) {
                if (!hit.add(target.getUUID())) continue;
                target.invulnerableTime = 0;
                target.hurt(source, damage);
            }
        }

        level.addFreshEntity(new DawnSwordQiEntity(level, player, eye, direction, SKILL_RANGE));
        spawnBurst(level, player);
        player.displayClientMessage(Component.translatable("endbound_ember.dawn.cast"), true);
    }

    /** 剑气与技能的通用友伤过滤：不命中玩家、友方生物与已驯服生物。 */
    public static boolean canHit(LivingEntity attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || target.isSpectator()) return false;
        return !FriendlyMobs.isProtected(target);
    }

    /** 敌对生物判定：丧尸娘、溺尸娘等带敌对标签但属于友好分类的生物不算。 */
    private static boolean isHostile(LivingEntity dead) {
        if (FriendlyMobs.isProtected(dead)) return false;
        return dead instanceof Enemy || dead instanceof EnderDragon;
    }

    /** 释放瞬间的起手特效。 */
    private static void spawnBurst(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                eye.x, eye.y, eye.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.988F, 0.965F, 0.973F), 1.0F),
                eye.x, eye.y, eye.z, 12, 0.35D, 0.35D, 0.35D, 0.0D);
    }

    private static void spawnHalo(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double angle = player.tickCount * 0.35D;
        for (int i = 0; i < 3; i++) {
            double a = angle + i * (Math.PI * 2.0D / 3.0D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX() + Math.cos(a) * 0.9D,
                    player.getY() + 1.1D + Math.sin(angle * 0.5D) * 0.25D,
                    player.getZ() + Math.sin(a) * 0.9D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(), 3, 0.4D, 0.6D, 0.4D, 0.0D);
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            // 每 20 tick 扫一遍背包，把旧存档里超出 1 级的附魔等级迁移掉
            if (player.tickCount % 20 == 0) {
                for (ItemStack stack : player.getInventory().items) {
                    migrateLegacyLevel(player, stack);
                }
                migrateLegacyLevel(player, player.getInventory().offhand.get(0));
            }
            // 人权剑只保留「拂晓 I」这一行附魔数值，不参与拂晓的成长、范围与技能系统
            if (player.getMainHandItem().getItem() instanceof SwordOfTheFreeWillItem) {
                updateRange(player, 0);
                return;
            }
            int level = levelOf(player);
            updateRange(player, level);

            // 每 5 tick 把能量同步给客户端，用于物品提示中的能量显示
            if (player.tickCount % 5 == 0) {
                PacketDistributor.sendToPlayer(player, new DawnEnergyPayload(energy(player), level));
            }

            if (level <= 0) return;

            if (energy(player) >= MAX_ENERGY) {
                if (player.tickCount % 4 == 0) spawnHalo(player);
                if (player.tickCount % 20 == 0) {
                    player.displayClientMessage(Component.translatable("endbound_ember.dawn.ready"), true);
                }
            }
        }

        /** 满能量后右键（对空气或方块）射出剑气。 */
        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (tryCastFromMainHand(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (tryCastFromMainHand(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
            }
        }

        /** 近战命中：四维伤害加成、暴击与能量积累。 */
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
            if (source.is(DAMAGE_TYPE)) return;
            if (source.is(FreeWillEnchantments.BLUE_FLAME)) return;
            if (source.is(FreeWillEnchantments.FREE_WILL_LIGHT)) return;
            if (!(source.getEntity() instanceof ServerPlayer player)) return;
            if (source.getDirectEntity() != player) return;

            int level = levelOf(player);
            if (level <= 0) return;

            double multiplier = damageMultiplier(level);
            boolean crit = player.getRandom().nextDouble() < critChance(level);
            if (crit) multiplier *= critMultiplier(level);
            event.setAmount((float) (event.getAmount() * multiplier));

            addEnergy(player, ENERGY_PER_HIT);
            if (crit && player.level() instanceof ServerLevel levelObj) {
                LivingEntity target = event.getEntity();
                levelObj.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        target.getX(), target.getY(0.5D), target.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.2D);
                levelObj.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        /** 击杀成长：附魔等级 +1，并额外积攒能量。 */
        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

            ItemStack weapon = player.getMainHandItem();
            // 人权剑不参与拂晓的击杀成长
            if (weapon.getItem() instanceof SwordOfTheFreeWillItem) return;
            int level = levelOf(weapon);
            if (level <= 0) return;

            // 只有击杀敌对生物才会成长：丧尸娘、溺尸娘虽带敌对标签，但属于友好生物分类
            if (!isHostile(event.getEntity())) return;

            if (level < MAX_LEVEL) {
                setGrowthLevel(player, weapon, level + 1);
                if (player.level() instanceof ServerLevel levelObj) {
                    levelObj.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.2D, player.getZ(), 16, 0.4D, 0.6D, 0.4D, 0.05D);
                    levelObj.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 0.8F, 1.4F);
                }
            }
            addEnergy(player, ENERGY_PER_KILL);
        }

        /** 物品提示：在拂晓附魔行之后追加四维数值与技能说明。 */
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            int level = levelOf(stack);
            if (level <= 0) return;
            List<Component> tooltip = event.getToolTip();
            // 人权剑只显示拂晓成长数值，不显示拂晓的通用技能说明；原有附魔仍由原版正常显示。
            if (stack.getItem() instanceof SwordOfTheFreeWillItem) {
                appendGrowthTooltip(tooltip, level);
                return;
            }
            // 定位到拂晓附魔名称行，把数值插在它后面
            String enchantName = Component.translatable("enchantment.endbound_ember.dawn").getString();
            int insertAt = tooltip.size();
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().startsWith(enchantName)) {
                    insertAt = i + 1;
                    break;
                }
            }

            tooltip.add(insertAt, stat("endbound_ember.dawn.level",
                    String.valueOf(level), String.valueOf(MAX_LEVEL)));
            tooltip.add(insertAt + 1, stat("endbound_ember.dawn.damage",
                    String.format(java.util.Locale.ROOT, "%.0f", (damageMultiplier(level) - 1.0D) * 100.0D)));
            tooltip.add(insertAt + 2, stat("endbound_ember.dawn.crit",
                    String.format(java.util.Locale.ROOT, "%.0f", critChance(level) * 100.0D)));
            tooltip.add(insertAt + 3, stat("endbound_ember.dawn.crit_damage",
                    String.format(java.util.Locale.ROOT, "%.0f", (critMultiplier(level) - 1.0D) * 100.0D)));
            tooltip.add(insertAt + 4, stat("endbound_ember.dawn.range",
                    String.format(java.util.Locale.ROOT, "%.1f", rangeBonus(level))));
            tooltip.add(insertAt + 5, stat("endbound_ember.dawn.energy",
                    String.valueOf(DawnEnergyClient.energy()), String.valueOf(MAX_ENERGY)));
            tooltip.add(insertAt + 6, Component.translatable("endbound_ember.dawn.skill")
                    .withStyle(style -> style.withColor(SKILL_COLOR)));
            tooltip.add(insertAt + 7, Component.translatable("endbound_ember.dawn.skill_key")
                    .withStyle(style -> style.withColor(SKILL_COLOR)));
        }

        /** 人权剑只展示拂晓的成长数值，不展示通用技能说明或能量状态。 */
        private static void appendGrowthTooltip(List<Component> tooltip, int level) {
            String enchantName = Component.translatable("enchantment.endbound_ember.dawn").getString();
            int insertAt = tooltip.size();
            for (int i = 0; i < tooltip.size(); i++) {
                if (tooltip.get(i).getString().startsWith(enchantName)) {
                    insertAt = i + 1;
                    break;
                }
            }
            tooltip.add(insertAt, stat("endbound_ember.dawn.level",
                    String.valueOf(level), String.valueOf(MAX_LEVEL)));
            tooltip.add(insertAt + 1, stat("endbound_ember.dawn.damage",
                    String.format(java.util.Locale.ROOT, "%.0f", (damageMultiplier(level) - 1.0D) * 100.0D)));
            tooltip.add(insertAt + 2, stat("endbound_ember.dawn.crit",
                    String.format(java.util.Locale.ROOT, "%.0f", critChance(level) * 100.0D)));
            tooltip.add(insertAt + 3, stat("endbound_ember.dawn.crit_damage",
                    String.format(java.util.Locale.ROOT, "%.0f", (critMultiplier(level) - 1.0D) * 100.0D)));
            tooltip.add(insertAt + 4, stat("endbound_ember.dawn.range",
                    String.format(java.util.Locale.ROOT, "%.1f", rangeBonus(level))));
        }

        private static Component stat(String key, String... args) {
            return Component.translatable(key, (Object[]) args).withStyle(style -> style.withColor(STAT_COLOR));
        }

        /** 首次进入世界：发放拂晓附魔书（1 级），并授予「踏上旅途」。 */
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            CompoundTag data = player.getPersistentData();
            if (data.getBoolean(RECEIVED_TAG)) return;
            data.putBoolean(RECEIVED_TAG, true);

            ItemStack book = createEnchantedBook(player, 1);
            if (!player.getInventory().add(book)) player.drop(book, false);

            var server = player.getServer();
            if (server == null) return;
            var advancement = server.getAdvancements().get(JOURNEY_ADVANCEMENT);
            if (advancement != null) player.getAdvancements().award(advancement, JOURNEY_CRITERION);
        }
    }
}
