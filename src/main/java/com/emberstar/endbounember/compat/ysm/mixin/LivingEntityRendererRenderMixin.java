package com.emberstar.endbounember.compat.ysm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.emberstar.endbounember.compat.ysm.YsmRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 在实体渲染期间记录当前实体，供 YSM 渲染钩子关联「模型属于谁」。
 * <p>
 * YSM 用自建管线替换了玩家模型渲染，渲染入口拿不到实体参数；这里用线程内的上下文补齐。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void endboundEmber$beginEntityRender(LivingEntity entity, float entityYaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        YsmRenderContext.begin(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void endboundEmber$endEntityRender(LivingEntity entity, float entityYaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        YsmRenderContext.end();
    }
}
