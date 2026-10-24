package com.emberstar.endbounember.client;

import com.emberstar.endbounember.entity.DawnSwordQiEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 剑气实体不渲染模型，视觉完全由粒子构成；此渲染器只负责占位。
 */
public class DawnSwordQiRenderer extends EntityRenderer<DawnSwordQiEntity> {
    private static final ResourceLocation EMPTY =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/note.png");

    public DawnSwordQiRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DawnSwordQiEntity entity) {
        return EMPTY;
    }
}
