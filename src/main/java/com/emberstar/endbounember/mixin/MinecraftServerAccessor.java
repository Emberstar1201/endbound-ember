package com.emberstar.endbounember.mixin;

import java.util.concurrent.Executor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link MinecraftServer} 的两份构造 {@code ServerLevel} 所需数据：
 * 存档访问器与区块调度执行器。两者在运行时创建动态维度时必须原样传入。
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("executor")
    Executor endbound$getExecutor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess endbound$getStorageSource();
}
