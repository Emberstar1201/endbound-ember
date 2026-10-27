package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.DrownedGirl;
import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.entity.ZombieGirl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** 收容和释放已驯服少女的星尘容器。空、已收容两种形态共用同一个物品名称。 */
public class UnownedStardustItem extends Item {
    private static final String CAPTURED_DATA = "CapturedData";
    private static final String CAPTURED_TYPE = "CapturedType";

    public UnownedStardustItem(Properties properties) {
        super(properties);
    }

    public static boolean isCaptured(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(CAPTURED_DATA);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
            LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ZombieGirl girl) || isCaptured(stack)) {
            return InteractionResult.PASS;
        }
        if (!girl.isTamed()) {
            return InteractionResult.PASS;
        }
        if (!girl.isOwnedBy(player)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "item.endbound_ember.unowned_stardust.not_owner"), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

        if (!player.level().isClientSide) {
            CompoundTag entityData = new CompoundTag();
            target.saveWithoutId(entityData);
            entityData.remove("Pos");
            entityData.remove("Motion");
            entityData.remove("Rotation");
            entityData.remove("UUID");
            entityData.remove("Passengers");

            String type = target instanceof HumanGirl ? "human_girl"
                    : target instanceof DrownedGirl ? "drowned_girl" : "zombie_girl";
            ItemStack filled = new ItemStack(this);
            CompoundTag itemTag = new CompoundTag();
            itemTag.put(CAPTURED_DATA, entityData);
            itemTag.putString(CAPTURED_TYPE, type);
            filled.set(DataComponents.CUSTOM_DATA, CustomData.of(itemTag));
            target.discard();
            player.setItemInHand(hand, filled);
            player.getCooldowns().addCooldown(this, 20);
            player.level().playSound(null, player.blockPosition(), EndboundEmber.STARLIGHT.get(),
                    target.getSoundSource(), 0.8F, 1.4F);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isCaptured(stack)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player != null && player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.CONSUME;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return InteractionResult.FAIL;
        }
        CompoundTag itemData = customData.copyTag();
        Entity entity = switch (itemData.getString(CAPTURED_TYPE)) {
            case "zombie_girl" -> ModEntities.ZOMBIE_GIRL.get().create(level);
            case "drowned_girl" -> ModEntities.DROWNED_GIRL.get().create(level);
            case "human_girl" -> ModEntities.HUMAN_GIRL.get().create(level);
            default -> null;
        };
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        CompoundTag capturedData = itemData.getCompound(CAPTURED_DATA);
        capturedData.remove("UUID");
        entity.load(capturedData);
        entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                player == null ? 0.0F : player.getYRot(), 0.0F);
        level.addFreshEntity(entity);

        if (player != null) {
            player.setItemInHand(context.getHand(), new ItemStack(this));
            player.getCooldowns().addCooldown(this, 20);
        } else {
            stack.shrink(1);
        }
        level.playSound(null, spawnPos, EndboundEmber.STARLIGHT.get(),
                entity.getSoundSource(), 0.8F, 0.8F);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.endbound_ember.unowned_stardust");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isCaptured(stack);
    }
}
