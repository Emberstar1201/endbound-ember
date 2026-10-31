package com.emberstar.endbounember.compat.ysm;

import java.lang.reflect.Method;

import net.minecraft.world.entity.LivingEntity;

import com.emberstar.endbounember.EndboundEmber;

/**
 * 渲染上下文：记录当前正在渲染的实体，供 YSM 渲染钩子取用。
 * <p>
 * 由 {@code LivingEntityRendererRenderMixin} 在实体渲染前后写入/清空，按线程隔离，
 * 避免预览界面等嵌套渲染互相污染。
 * <p>
 * 注意：本类不能放在 {@code compat.ysm.mixin} 包下。Mixin 会把该包视为专属命名空间，
 * 该包内的非 Mixin 类不允许被外部直接引用，否则渲染时会抛
 * {@code IllegalClassLoadError}。
 */
public final class YsmRenderContext {
    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    /** 临时诊断：只在首次解析时打印一次 */
    private static volatile boolean resolveDiagnosed;

    /** animatable → 实体访问器的反射缓存；{@code null} 表示尚未解析 */
    private static volatile Method animatableEntityGetter;
    /** 符号解析失败后本会话不再尝试，避免每帧抛异常 */
    private static volatile boolean getterBroken;

    private YsmRenderContext() {
    }

    /** 当前线程正在渲染的实体；不在实体渲染中时为 {@code null} */
    public static LivingEntity currentEntity() {
        return CURRENT.get();
    }

    /**
     * 从 YSM 渲染方法的 {@code animatable} 参数取得实体。
     * <p>
     * YSM 用自建渲染器接管玩家模型，原版 {@code LivingEntityRenderer#render} 不会执行，
     * 线程上下文通常为空；animatable 本身也不是实体，而是 YSM 的玩家动画状态对象
     * （继承泛型基类 {@code OoO0oo0o0o0oOoo0oOOO0Ooo<TEntity>}），通过其
     * {@code ooo00OoO00OOOO0oOooOo0Oo()} 访问器取回所属实体。
     */
    public static LivingEntity resolve(Object animatable) {
        LivingEntity direct = animatable instanceof LivingEntity living ? living : null;
        LivingEntity resolved = direct != null ? direct : entityFromAnimatable(animatable);
        if (!resolveDiagnosed) {
            resolveDiagnosed = true;
            EndboundEmber.LOGGER.info("[光环诊断] animatable={}, 线程上下文={}, 解析结果={}",
                    animatable == null ? "null" : animatable.getClass().getName(),
                    CURRENT.get() == null ? "null" : CURRENT.get().getClass().getName(),
                    resolved == null ? "null" : resolved.getClass().getName());
        }
        return resolved != null ? resolved : CURRENT.get();
    }

    /** 反射调用 YSM 动画对象的实体访问器；失败一次后本会话不再尝试 */
    private static LivingEntity entityFromAnimatable(Object animatable) {
        if (animatable == null || getterBroken) {
            return null;
        }
        try {
            if (animatableEntityGetter == null) {
                animatableEntityGetter = animatable.getClass().getMethod(YsmSymbols.ANIMATABLE_ENTITY_GETTER);
            }
            Object entity = animatableEntityGetter.invoke(animatable);
            return entity instanceof LivingEntity living ? living : null;
        } catch (Throwable error) {
            getterBroken = true;
            EndboundEmber.LOGGER.warn("[YSM Compat] 无法从 YSM 动画对象读取实体，光环回退到线程上下文：{}: {}",
                    error.getClass().getSimpleName(), error.getMessage());
            return null;
        }
    }

    public static void begin(LivingEntity entity) {
        CURRENT.set(entity);
    }

    public static void end() {
        CURRENT.remove();
    }
}