package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 亡者之柱：Herobrine 第二阶段召出的黑色柱子。
 *
 * <p>柱子的唯一作用是平摊本体受到的伤害，同时拥有独立血条。
 * 玩家可以左键攻击柱子把它打碎，击碎后本体能分摊掉的伤害随之减少。</p>
 */
public class DoomPillarBlockEntity extends BlockEntity {
    /** 单根柱子的生命值 */
    public static final float MAX_HEALTH = 200.0F;
    /** 玩家直接攻击柱子时，攻击力换算成柱子伤害的倍率 */
    private static final float MELEE_SCALE = 3.0F;
    /** 连续攻击的最小间隔（tick） */
    private static final int HIT_COOLDOWN = 4;
    /** 血条刷新与周围玩家扫描的间隔（tick） */
    private static final int BAR_REFRESH_INTERVAL = 10;
    /** 血条可见半径（格） */
    private static final double BAR_RANGE = 48.0D;

    private float health = MAX_HEALTH;
    private int hitCooldown;
    private int barCooldown;

    /** 柱子独立血条：只在服务端创建 */
    @Nullable
    private ServerBossEvent bossEvent;

    public DoomPillarBlockEntity(BlockPos pos, BlockState state) {
        super(EndboundEmber.DOOM_PILLAR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoomPillarBlockEntity pillar) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (pillar.hitCooldown > 0) {
            pillar.hitCooldown--;
        }
        if (--pillar.barCooldown > 0) {
            return;
        }
        pillar.barCooldown = BAR_REFRESH_INTERVAL;
        pillar.refreshBossBar(serverLevel);
        // 柱子自身的灵魂粒子，方便玩家在混战中定位
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D, 3, 0.25D, 0.3D, 0.25D, 0.01D);
    }

    /** 柱子是否还能继续平摊伤害 */
    public boolean isAlivePillar() {
        return this.health > 0.0F && !this.isRemoved();
    }

    /** 玩家左键攻击柱子 */
    public void hit(Player player) {
        if (this.level == null || this.level.isClientSide() || this.hitCooldown > 0 || this.health <= 0.0F) {
            return;
        }
        this.hitCooldown = HIT_COOLDOWN;
        // 按蓄力值缩放：连点时每一下都只有很低的倍率，避免几下发就把柱子秒掉
        float strength = player.getAttackStrengthScale(0.5F);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * MELEE_SCALE * strength;
        // 攻击柱子同样消耗蓄力，让破柱和近战保持一致的节奏
        player.resetAttackStrengthTicker();
        this.applyDamage(damage);
    }

    /** 扣除柱子血量，血量归零时直接摧毁 */
    public void applyDamage(float amount) {
        if (!(this.level instanceof ServerLevel level) || this.health <= 0.0F || amount <= 0.0F) {
            return;
        }
        this.health = Math.max(0.0F, this.health - amount);
        this.setChanged();
        Vec3 center = Vec3.atCenterOf(this.worldPosition).add(0.0D, 1.2D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT,
                center.x, center.y, center.z, 12, 0.35D, 0.6D, 0.35D, 0.2D);
        level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.HOSTILE, 1.2F, 0.6F);
        this.refreshBossBar(level);
        if (this.health <= 0.0F) {
            this.shatter(level);
        }
    }

    private void shatter(ServerLevel level) {
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y + 1.0D, center.z, 2, 0.5D, 1.0D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL,
                center.x, center.y + 1.0D, center.z, 60, 0.6D, 1.2D, 0.6D, 0.05D);
        level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                center.x, center.y + 1.0D, center.z, 40, 0.6D, 1.2D, 0.6D, 0.1D);
        level.playSound(null, this.worldPosition, SoundEvents.GLASS_BREAK,
                SoundSource.HOSTILE, 1.6F, 0.5F);
        level.playSound(null, this.worldPosition, SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE, 0.7F, 1.4F);
        if (this.bossEvent != null) {
            this.bossEvent.removeAllPlayers();
        }
        DoomPillarBlock.removePillar(level, this.worldPosition);
    }

    private ServerBossEvent bossBar() {
        if (this.bossEvent == null) {
            this.bossEvent = new ServerBossEvent(
                    Component.translatable("block.endbound_ember.doom_pillar"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
        return this.bossEvent;
    }

    private void refreshBossBar(ServerLevel level) {
        ServerBossEvent bar = this.bossBar();
        bar.setProgress(Math.max(0.0F, this.health / MAX_HEALTH));
        bar.setName(Component.translatable("block.endbound_ember.doom_pillar")
                .append(Component.literal(" " + Math.max(0, (int) Math.ceil(this.health))
                        + " / " + (int) MAX_HEALTH)));
        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(this.worldPosition).inflate(BAR_RANGE));
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!nearby.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : nearby) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.bossEvent != null) {
            this.bossEvent.removeAllPlayers();
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("PillarHealth", this.health);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("PillarHealth")) {
            this.health = tag.getFloat("PillarHealth");
        }
    }
}
