package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.HaloWearing;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「不被世界承认的少女」在未佩戴星尘光环时不弹出提示条，
 * 否则解锁瞬间的提示条会用真实标题提前泄底。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin {
    private static final ResourceLocation ENDBOUND$VOID_TARGET =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "unrecognized_girl");

    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void endbound$hideUntilHalo(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible,
            CallbackInfoReturnable<Toast.Visibility> cir) {
        if (!ENDBOUND$VOID_TARGET.equals(this.advancement.id())) return;
        if (HaloWearing.stardustHalo(Minecraft.getInstance().player)) return;
        cir.setReturnValue(Toast.Visibility.HIDE);
    }
}
