package com.emberstar.endbounember.item;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class StardustCordBraceletItem extends Item {
    private static final int COLOR = 0x828CD8;
    private static final String INVULNERABLE_UNTIL = "EndboundEmberMeaningInvulnerableUntil";
    private static final long IMMUNITY_TICKS = 600L;
    private static final long MAX_PROGRESS_TICKS = 2400000L;
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "stardust_cord_attack_speed");

    public StardustCordBraceletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(COLOR));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.condition")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.night")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.soulless")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.empty")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.void")
                .withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.stardust_cord_bracelet.meaning")
                .withStyle(style -> style.withColor(COLOR)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            boolean active = hasBracelet(player);
            updateAttackSpeed(player, active);
            if (active && player instanceof ServerPlayer serverPlayer
                    && serverPlayer.getY() < serverPlayer.level().getMinBuildHeight() - 16) {
                returnToBedSpawn(serverPlayer);
            }
        }

        @SubscribeEvent
        public static void onDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                    || !hasBracelet(player)) {
                return;
            }

            long now = player.level().getGameTime();
            long invulnerableUntil = player.getPersistentData().getLong(INVULNERABLE_UNTIL);
            if (invulnerableUntil > now) {
                event.setCanceled(true);
                return;
            }

            if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer serverPlayer) returnToBedSpawn(serverPlayer);
                return;
            }

            // /kill 指令的绝对致死伤害免疫。
            if (event.getSource().is(DamageTypes.GENERIC_KILL)) {
                event.setCanceled(true);
                return;
            }

            if (player.getHealth() - event.getAmount() <= 0.0F) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                player.getPersistentData().putLong(INVULNERABLE_UNTIL, now + IMMUNITY_TICKS);
                return;
            }

            double progress = Math.min(1.0D, Math.max(0.0D, now / (double) MAX_PROGRESS_TICKS));
            double reduction = 0.25D + 0.749D * progress;
            event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
        }

        @SubscribeEvent
        public static void onChangeTarget(LivingChangeTargetEvent event) {
            if (event.getEntity() instanceof Monster
                    && event.getNewAboutToBeSetTarget() instanceof Player player
                    && hasBracelet(player)) {
                event.setCanceled(true);
            }
        }

        private static boolean hasBracelet(Player player) {
            return player.getMainHandItem().is(EndboundEmber.STARDUST_CORD_BRACELET.get())
                    || player.getOffhandItem().is(EndboundEmber.STARDUST_CORD_BRACELET.get())
                    || hasCuriosBracelet(player);
        }

        private static boolean hasCuriosBracelet(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.STARDUST_CORD_BRACELET.get());
        }

        private static void updateAttackSpeed(Player player, boolean active) {
            AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
            if (instance == null) return;
            AttributeModifier modifier = instance.getModifier(ATTACK_SPEED_MODIFIER);
            if (active && modifier == null) {
                instance.addPermanentModifier(new AttributeModifier(
                        ATTACK_SPEED_MODIFIER, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!active && modifier != null) {
                instance.removeModifier(ATTACK_SPEED_MODIFIER);
            }
        }

        private static void returnToBedSpawn(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            BlockPos target = null;
            ServerLevel targetLevel = level;
            BlockPos respawn = player.getRespawnPosition();
            if (respawn != null && player.getRespawnDimension().equals(level.dimension())
                    && level.getBlockState(respawn).is(BlockTags.BEDS)) {
                target = respawn.above();
            }
            if (target == null) {
                target = level.getSharedSpawnPos();
            }
            Vec3 position = Vec3.atBottomCenterOf(target);
            player.teleportTo(targetLevel, position.x, position.y, position.z,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }
    }
}
