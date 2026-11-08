package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 狙击：弓 / 弩专属，最高 I 级。
 * 箭矢飞行距离越远伤害越高，每格 +4%，最多 +100%；命中时向射手显示加成。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SniperEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "sniper"));

    /** 旧版 SniperConfig.damageBonusPerBlock 默认值：每格飞行距离增加 4% 伤害。 */
    private static final double BONUS_PER_BLOCK = 0.04D;
    /** 旧版 SniperConfig.maxDamageBonus 默认值：伤害加成上限 100%。 */
    private static final double MAX_BONUS = 1.0D;

    /** 旧版 SniperHandler.TAG_SNIPER：标记带有狙击附魔的箭。 */
    private static final String TAG_MARKED = "EndboundEmberSniper";
    /** 旧版 SniperHandler.TAG_ORIGIN_X / Y / Z：记录箭发射时的射手眼位。 */
    private static final String TAG_ORIGIN_X = "EndboundEmberSniperOriginX";
    private static final String TAG_ORIGIN_Y = "EndboundEmberSniperOriginY";
    private static final String TAG_ORIGIN_Z = "EndboundEmberSniperOriginZ";

    private SniperEnchantment() {
    }

    /** 旧版 SniperHandler.getSniperLevel：取主副手弓 / 弩上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的狙击附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) return 0;

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

    /** 箭矢生成：给带有狙击附魔的箭打上标记，并记录发射瞬间的射手眼位作为测距起点。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.getBoolean(TAG_MARKED)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        if (levelOfHands(shooter) <= 0) return;

        Vec3 origin = shooter.getEyePosition();
        tag.putBoolean(TAG_MARKED, true);
        tag.putDouble(TAG_ORIGIN_X, origin.x);
        tag.putDouble(TAG_ORIGIN_Y, origin.y);
        tag.putDouble(TAG_ORIGIN_Z, origin.z);
    }

    /** 箭矢造成伤害：按飞行距离线性提高伤害，并向射手显示加成。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (!tag.getBoolean(TAG_MARKED)) return;

        Level level = arrow.level();
        if (level.isClientSide()) return;

        Vec3 origin = new Vec3(tag.getDouble(TAG_ORIGIN_X), tag.getDouble(TAG_ORIGIN_Y),
                tag.getDouble(TAG_ORIGIN_Z));
        double distance = arrow.position().distanceTo(origin);
        double bonus = Math.min(distance * BONUS_PER_BLOCK, MAX_BONUS);
        if (bonus <= 0.0D) return;

        event.setAmount((float) (event.getAmount() * (1.0D + bonus)));

        Entity owner = arrow.getOwner();
        if (owner instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§b狙击：距离 " + String.format("%.1f", distance)
                    + " 格，伤害 +" + Math.round(bonus * 100.0D) + "%"), true);
        }
    }
}
