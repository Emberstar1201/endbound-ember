package com.emberstar.endbounember.item;

import java.util.List;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.FriendlyMobs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 虚空之星：以坠落为代价的虚空支配者，摔落与虚空都无法伤及持有者。
 */
public class VoidStarItem extends Item {
    private static final int COLOR = 0x7C5BD6;

    public VoidStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(COLOR));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.endbound_ember.void_star.condition").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.void_star.void_body").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.void_star.return").withStyle(style -> style.withColor(COLOR)));
        tooltip.add(Component.translatable("item.endbound_ember.void_star.ripple").withStyle(style -> style.withColor(COLOR)));
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        /** 低于世界底部多少格开始把持有者送回重生点。 */
        private static final int VOID_TRIGGER_OFFSET = 5;
        /** 虚空涟漪的作用半径。 */
        private static final double RIPPLE_RADIUS = 18.0D;
        /** 虚空涟漪的结算间隔与单次伤害。 */
        private static final int RIPPLE_INTERVAL = 40;
        private static final float RIPPLE_DAMAGE = 1.0F;

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onFall(LivingFallEvent event) {
            if (!(event.getEntity() instanceof Player player)
                    || player.level().isClientSide
                    || !hasVoidStar(player)) {
                return;
            }
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
            player.resetFallDistance();
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof Player player)
                    || player.level().isClientSide
                    || !hasVoidStar(player)) {
                return;
            }
            if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!hasVoidStar(player)) return;
            if (player.getY() >= player.level().getMinBuildHeight() - VOID_TRIGGER_OFFSET) return;
            sendHome(player);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (event.getServer().getTickCount() % RIPPLE_INTERVAL != 0) return;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    if (!hasVoidStar(player)) continue;
                    AABB area = player.getBoundingBox().inflate(RIPPLE_RADIUS);
                    for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                            entity -> entity instanceof Enemy && !FriendlyMobs.isProtected(entity))) {
                        mob.hurt(level.damageSources().fellOutOfWorld(), RIPPLE_DAMAGE);
                    }
                }
            }
        }

        private static void sendHome(ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server == null) return;

            BlockPos respawn = player.getRespawnPosition();
            ServerLevel targetLevel = respawn == null
                    ? server.overworld()
                    : server.getLevel(player.getRespawnDimension());
            if (targetLevel == null) targetLevel = server.overworld();
            BlockPos target = respawn == null ? targetLevel.getSharedSpawnPos() : respawn;

            player.stopRiding();
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(targetLevel,
                    target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.displayClientMessage(
                    Component.translatable("item.endbound_ember.void_star.teleported"), true);
        }

        private static boolean hasVoidStar(Player player) {
            return player.getMainHandItem().is(EndboundEmber.VOID_STAR.get())
                    || player.getOffhandItem().is(EndboundEmber.VOID_STAR.get())
                    || hasCuriosVoidStar(player);
        }

        private static boolean hasCuriosVoidStar(Player player) {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            return com.emberstar.endbounember.compat.CuriosCompat.isEquipped(
                    player, EndboundEmber.VOID_STAR.get());
        }
    }
}
