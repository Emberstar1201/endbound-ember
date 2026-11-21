package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;

/**
 * 千位劫取：dsh 原作大剑。潜行使用引爆周围的程序劫掠效果。
 * 原始实现的数值和交互由本类与 KiloSnatchAbilities 保留。
 */
public class KiloSnatchItem extends SwordItem {
    private static final ResourceLocation DAMAGE_ID = EndboundEmber.id("damage");
    private static final ResourceLocation SPEED_ID = EndboundEmber.id("speed");
    private static final ResourceLocation REACH_ID = EndboundEmber.id("reach");

    public KiloSnatchItem(Item.Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    public static Item.Properties properties() {
        ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(DAMAGE_ID, 20.0D, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SPEED_ID, 1.0D, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_ID, 3.5D, Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        return new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(attributes);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }

            int detonated = KiloSnatchAbilities.detonate(player);
            player.getCooldowns().addCooldown(this, 200);
            player.displayClientMessage(Component.literal("§d程序劫掠：引爆 " + detonated + " 个目标"), true);
        }
        return InteractionResultHolder.success(stack);
    }
}
