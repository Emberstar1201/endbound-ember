package com.emberstar.endbounember.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.ZombieGirl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 归乡铃：摇响后把主人名下所有同维度的少女唤回身边，
 * 连躺在未加载区块里的个体也能循声归来。
 */
public class HomeBellItem extends Item {
    /** 使用冷却，避免连点反复强制加载区块。 */
    private static final int USE_COOLDOWN = 40;

    public HomeBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int count = CompanionRecall.summonAll(serverPlayer);
            player.getCooldowns().addCooldown(this, USE_COOLDOWN);
            level.playSound(null, player.blockPosition(), EndboundEmber.HOME_BELL_SOUND.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            serverPlayer.displayClientMessage(Component.translatable(
                    count > 0
                            ? "item.endbound_ember.home_bell.summoned"
                            : "item.endbound_ember.home_bell.none",
                    count), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static class Events {
        @SubscribeEvent
        public static void onGirlTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof ZombieGirl girl)
                    || girl.level().isClientSide
                    || girl.tickCount % CompanionRecall.TRACK_INTERVAL != 0
                    || !girl.isTamed()
                    || !(girl.level() instanceof ServerLevel level)) {
                return;
            }
            UUID owner = girl.getOwnerUUID();
            if (owner == null) return;
            CompanionRecall.data(level.getServer())
                    .track(girl.getUUID(), owner, level.dimension(), girl.blockPosition());
        }
    }

    /** 少女位置簿与召回逻辑。 */
    private static final class CompanionRecall {
        private static final String DATA_NAME = "endbound_ember_companion_recall";
        private static final int TRACK_INTERVAL = 40;
        private static final int SPOT_ATTEMPTS = 16;

        private CompanionRecall() {
        }

        private static RecallData data(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(RecallData::new, RecallData::load, null), DATA_NAME);
        }

        private static int summonAll(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            RecallData data = data(level.getServer());
            Set<UUID> recalled = new HashSet<>();
            List<UUID> stale = new ArrayList<>();

            // 先按位置簿召回：强制加载目标区块，让未加载的少女也进入世界。
            for (Map.Entry<UUID, RecallData.Entry> entry : new HashMap<>(data.entries).entrySet()) {
                RecallData.Entry info = entry.getValue();
                if (!info.owner().equals(player.getUUID()) || info.dimension() != level.dimension()) continue;
                level.getChunk(info.pos().getX() >> 4, info.pos().getZ() >> 4, ChunkStatus.FULL, true);
                Entity entity = level.getEntity(entry.getKey());
                if (entity == null) {
                    stale.add(entry.getKey());
                    continue;
                }
                if (summon(entity, player)) recalled.add(entity.getUUID());
            }
            stale.forEach(data::remove);

            // 再扫一遍已加载实体，兜底刚驯服、还没被记入位置簿的少女。
            List<Entity> loaded = new ArrayList<>();
            level.getAllEntities().forEach(loaded::add);
            for (Entity entity : loaded) {
                if (recalled.contains(entity.getUUID())) continue;
                if (summon(entity, player)) recalled.add(entity.getUUID());
            }
            return recalled.size();
        }

        private static boolean summon(Entity entity, ServerPlayer player) {
            if (!(entity instanceof ZombieGirl girl)
                    || !girl.isAlive()
                    || !girl.isTamed()
                    || !girl.isOwnedBy(player)) {
                return false;
            }
            if (girl.isSleeping()) girl.stopSleeping();
            girl.getNavigation().stop();
            girl.setTarget(null);
            Vec3 spot = findSpot(girl, player);
            girl.teleportTo(spot.x, spot.y, spot.z);
            girl.resetFallDistance();
            return true;
        }

        private static Vec3 findSpot(ZombieGirl girl, ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
                mutable.set(
                        player.getBlockX() + girl.getRandom().nextInt(7) - 3,
                        player.getBlockY(),
                        player.getBlockZ() + girl.getRandom().nextInt(7) - 3);
                while (level.getBlockState(mutable).blocksMotion()
                        && mutable.getY() < level.getMaxBuildHeight()) {
                    mutable.move(0, 1, 0);
                }
                if (!level.getBlockState(mutable).isSolid()
                        && !level.getBlockState(mutable.below()).isAir()) {
                    return new Vec3(mutable.getX() + 0.5D, mutable.getY(), mutable.getZ() + 0.5D);
                }
            }
            return new Vec3(player.getX(), player.getY(), player.getZ());
        }

        /** 每只已驯服少女的最后已知位置，区块粒度即可满足强制加载。 */
        private static final class RecallData extends SavedData {
            private final Map<UUID, Entry> entries = new HashMap<>();

            private record Entry(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
            }

            private static RecallData load(CompoundTag tag, HolderLookup.Provider provider) {
                RecallData data = new RecallData();
                ListTag list = tag.getList("Girls", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    data.entries.put(
                            entry.getUUID("Id"),
                            new Entry(
                                    entry.getUUID("Owner"),
                                    ResourceKey.create(Registries.DIMENSION,
                                            ResourceLocation.parse(entry.getString("Dim"))),
                                    BlockPos.of(entry.getLong("Pos"))));
                }
                return data;
            }

            private void track(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
                Entry old = entries.get(id);
                if (old == null || !old.owner().equals(owner) || old.dimension() != dimension
                        || !old.pos().equals(pos)) {
                    entries.put(id, new Entry(owner, dimension, pos));
                    setDirty();
                }
            }

            private void remove(UUID id) {
                if (entries.remove(id) != null) setDirty();
            }

            @Override
            public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
                ListTag list = new ListTag();
                for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
                    CompoundTag item = new CompoundTag();
                    item.putUUID("Id", entry.getKey());
                    item.putUUID("Owner", entry.getValue().owner());
                    item.putString("Dim", entry.getValue().dimension().location().toString());
                    item.putLong("Pos", entry.getValue().pos().asLong());
                    list.add(item);
                }
                tag.put("Girls", list);
                return tag;
            }
        }
    }
}
