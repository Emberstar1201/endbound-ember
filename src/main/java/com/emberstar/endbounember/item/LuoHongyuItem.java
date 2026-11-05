package com.emberstar.endbounember.item;

import java.util.List;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.QianpoQingMingSwordEnchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 洛红雨：赤色刀锋。
 *
 * <p>命中叠加「赤雨」流血层数，每层每秒造成持续伤害，层数在脱战后自然消退；
 * 首次进入背包时写入旧版预设的千破·青溟剑 V。</p>
 */
public class LuoHongyuItem extends Item {
    /** 流血最大层数。 */
    public static final int MAX_STACKS = 5;
    /** 流血持续时间（刻）：6 秒，命中刷新。 */
    public static final long BLEED_DURATION = 120L;
    /** 流血结算间隔（刻）：1 秒。 */
    public static final long BLEED_INTERVAL = 20L;
    /** 每层流血每秒造成的伤害。 */
    public static final float DAMAGE_PER_STACK = 1.5F;
    /** 旧版预设的千破·青溟剑等级。 */
    public static final int PRESET_QIANPO_LEVEL = 5;

    private static final String STACKS_TAG = "EndboundEmberLuoHongyuStacks";
    private static final String EXPIRE_TAG = "EndboundEmberLuoHongyuExpire";
    private static final String NEXT_TICK_TAG = "EndboundEmberLuoHongyuNextTick";
    private static final String ATTACKER_TAG = "EndboundEmberLuoHongyuAttacker";

    private static final int NAME_COLOR = 0xC0243A;

    private static final ResourceLocation ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "luohongyu_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "luohongyu_attack_speed");

    public LuoHongyuItem(Properties properties) {
        super(properties);
    }

    /** 攻击伤害 / 攻速均为相对玩家基础值的偏移量。 */
    public static ItemAttributeModifiers attributes(double attackDamageBonus, double attackSpeedBonus) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ATTACK_DAMAGE_ID, attackDamageBonus, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ATTACK_SPEED_ID, attackSpeedBonus, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(NAME_COLOR));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.luohongyu.desc")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.endbound_ember.luohongyu.mark")
                .withStyle(style -> style.withColor(NAME_COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.luohongyu.bleed")
                .withStyle(style -> style.withColor(0xE0567A)));
    }

    /** 首次进入背包时写入旧版预设附魔，之后由玩家自行搭配。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!current.isEmpty()) return;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(QianpoQingMingSwordEnchantment.KEY)
                .ifPresent(holder -> mutable.set(holder, PRESET_QIANPO_LEVEL));
        bless(mutable, level, "minecraft:sharpness", 5);
        bless(mutable, level, "minecraft:looting", 3);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    private static void bless(ItemEnchantments.Mutable mutable, Level level, String id, int value) {
        level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id)))
                .ifPresent(holder -> mutable.set(holder, value));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        /** 命中叠加赤雨层数。 */
        @SubscribeEvent
        public static void onLivingDamaged(LivingDamageEvent.Post event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
            if (!(source.getDirectEntity() instanceof ServerPlayer attacker) || source.getEntity() != attacker) {
                return;
            }
            if (!(attacker.getMainHandItem().getItem() instanceof LuoHongyuItem)) return;
            if (!(attacker.level() instanceof ServerLevel level)) return;

            LivingEntity target = event.getEntity();
            CompoundTag data = target.getPersistentData();
            long gameTime = level.getGameTime();
            int stacks = data.getLong(EXPIRE_TAG) >= gameTime ? data.getInt(STACKS_TAG) : 0;
            if (stacks <= 0) {
                data.putLong(NEXT_TICK_TAG, gameTime + BLEED_INTERVAL);
            }
            data.putInt(STACKS_TAG, Math.min(stacks + 1, MAX_STACKS));
            data.putLong(EXPIRE_TAG, gameTime + BLEED_DURATION);
            data.putString(ATTACKER_TAG, attacker.getUUID().toString());
        }

        /** 每秒结算一次流血伤害；持续时间结束后清空层数。 */
        @SubscribeEvent
        public static void onLivingTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof LivingEntity target)) return;
            if (target.level().isClientSide) return;
            CompoundTag data = target.getPersistentData();
            int stacks = data.getInt(STACKS_TAG);
            if (stacks <= 0) return;
            if (!(target.level() instanceof ServerLevel level)) return;

            long gameTime = level.getGameTime();
            if (gameTime >= data.getLong(EXPIRE_TAG)) {
                clear(data);
                return;
            }
            if (gameTime < data.getLong(NEXT_TICK_TAG)) return;
            data.putLong(NEXT_TICK_TAG, gameTime + BLEED_INTERVAL);

            ServerPlayer attacker = resolveAttacker(level, data);
            // 直接实体置空：流血跳伤不再被当作一次玩家命中，
            // 否则会被叠层事件二次捕获、反复刷新持续时间，造成永续流血
            DamageSource source = attacker != null
                    ? level.damageSources().source(DamageTypes.MAGIC, null, attacker)
                    : level.damageSources().magic();
            target.invulnerableTime = 0;
            target.hurt(source, DAMAGE_PER_STACK * stacks);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    8, 0.3D, 0.4D, 0.3D, 0.0D);
        }

        private static ServerPlayer resolveAttacker(ServerLevel level, CompoundTag data) {
            String raw = data.getString(ATTACKER_TAG);
            if (raw.isEmpty()) return null;
            try {
                UUID id = UUID.fromString(raw);
                return level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(id);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static void clear(CompoundTag data) {
            data.putInt(STACKS_TAG, 0);
            data.putLong(EXPIRE_TAG, 0L);
            data.putLong(NEXT_TICK_TAG, 0L);
            data.remove(ATTACKER_TAG);
        }
    }
}
