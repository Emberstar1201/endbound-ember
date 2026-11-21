package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.client.KiloSnatchWingRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class KiloSnatchItemRendererMixin {
    private static final ResourceLocation KILO_SNATCH_ID =
            ResourceLocation.fromNamespaceAndPath("endbound_ember", "kilo_snatch");

    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void endbound$renderKiloSnatch(
            ItemStack stack,
            ItemDisplayContext context,
            boolean leftHand,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            BakedModel model,
            CallbackInfo callback) {
        if (stack.isEmpty() || !KILO_SNATCH_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return;
        }
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        if (KiloSnatchWingRenderer.render(poses, buffers, light, overlay, model, context, leftHand)) {
            callback.cancel();
        }
    }
}
