package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;

/**
 * 让留在乱流维度里下线的玩家，重新登录后仍回到自己的乱流维度。
 * <p>
 * 动态维度只在运行时注册，服务器重启后 {@code worldMap} 里没有它，
 * 原版 {@code PlayerList#placeNewPlayer} 查不到存档记录的维度键就会退回主世界。
 * 这里在查询维度时顺手为时间乱流维度重建实例，玩家的坐标与建筑因此原样保留。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListDimensionMixin {
    @Redirect(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel endboundEmber$restoreTimeTurbulence(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            return level;
        }
        return TimeTurbulenceLevels.ownerOf(key)
                .map(uuid -> TimeTurbulenceLevels.getOrCreate(server, uuid))
                .orElse(null);
    }
}
