package com.emberstar.endbounember.compat.ysm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.compat.ysm.YsmHaloRenderer;
import com.emberstar.endbounember.compat.ysm.YsmHeadCapture;
import com.emberstar.endbounember.compat.ysm.YsmRenderContext;
import com.emberstar.endbounember.compat.ysm.YsmSymbols;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 在 YSM 的模型渲染接口上抓取 Head locator，并在渲染结束后补画光环。
 * <p>
 * 注入点是 YSM 渲染接口 {@code ooo0000oO0O0ooOO0ooOO0o0} 的默认实现
 * {@code (模型, 动画实体, tickDelta, PoseStack, MultiBufferSource, VertexConsumer, int, int, FFFF)V}。
 * 该方法的第一个参数就是模型实例，且进入时 {@code pose} 已包含实体的全部根变换、
 * 尚未叠加任何骨骼变换，栈顶矩阵正好是合成头部矩阵所需的根矩阵。
 * <p>
 * 之所以要在 RETURN 处直接绘制，是因为 YSM 用自建渲染器接管了玩家模型，
 * 原版 {@code PlayerRenderer} 的 {@code RenderLayer} 列表不会被执行。
 * <p>
 * 目标类与方法名都是 YSM 的混淆符号，{@code remap = false} 保证不被映射改写；
 * {@code require = 0} 让版本不符时静默跳过，光环自动回退到原版头部锚点。
 * <p>
 * 目标是一个接口，因此这里必须声明为 {@code interface} 混入：Mixin 的 Standard 子类型
 * 会校验「类混入类、接口混入接口」，用普通类去混入接口会在配置准备阶段直接抛
 * {@code @Mixin target type mismatch}，导致整个注入被丢弃。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmGeoRendererIntermediaryMixin}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
@Pseudo
@Mixin(targets = YsmSymbols.RENDER_INTERFACE, remap = false)
public interface YsmGeoRendererMixin {
    @Inject(
            method = YsmSymbols.RENDER_MODEL_ENTRY + YsmSymbols.RENDER_MODEL_ENTRY_DESCRIPTOR,
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void endboundEmber$captureYsmHead(@Coerce Object animatedModel, @Coerce Object animatable,
            float tickDelta, PoseStack pose, MultiBufferSource buffers, VertexConsumer vertexConsumer,
            int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        // animatable 参数就是正在渲染的实体，优先用它；取不到时再回退到线程上下文
        LivingEntity entity = YsmRenderContext.resolve(animatable);
        YsmHeadCapture.capture(entity, animatedModel, pose);
    }

    @Inject(
            method = YsmSymbols.RENDER_MODEL_ENTRY + YsmSymbols.RENDER_MODEL_ENTRY_DESCRIPTOR,
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void endboundEmber$renderHalo(@Coerce Object animatedModel, @Coerce Object animatable,
            float tickDelta, PoseStack pose, MultiBufferSource buffers, VertexConsumer vertexConsumer,
            int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        YsmHaloRenderer.render(YsmRenderContext.resolve(animatable), pose, buffers, light, overlay);
    }
}
