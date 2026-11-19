package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.DawnArrowEntity;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class DawnArrowRenderer extends ArrowRenderer<DawnArrowEntity> {
    private static final ResourceLocation TEXTURE = EndboundEmber.id("textures/entity/projectiles/dawn_arrow.png");

    public DawnArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DawnArrowEntity arrow) {
        return TEXTURE;
    }
}
