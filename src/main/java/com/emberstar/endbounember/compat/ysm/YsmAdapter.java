package com.emberstar.endbounember.compat.ysm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

/**
 * 通过反射读取 YSM 混淆类中的 Head 骨骼层级，并合成头部矩阵。
 * <p>
 * 只在首次使用时解析一次符号；解析失败会抛出异常，由调用方统一降级到原版头部锚点。
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmV265Adapter}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmAdapter {
    private static volatile Accessors accessors;
    /** 临时诊断：只在首次捕获时打印一次骨骼数据 */
    private static volatile boolean boneDiagnosed;

    private YsmAdapter() {
    }

    /**
     * 捕获 Head locator 的最终矩阵。
     *
     * @param model      YSM 的动画模型实例
     * @param rootMatrix 渲染该模型时的根矩阵
     * @return 头部矩阵；模型没有可用的 Head 层级时返回 {@code null}
     * @throws Throwable 符号解析或调用失败（版本不匹配）
     */
    public static Matrix4f captureHeadMatrix(Object model, Matrix4f rootMatrix) throws Throwable {
        Accessors a = accessorsFor(model);
        Object value = a.headBones.invoke(model);
        if (!(value instanceof List<?> bones) || bones.isEmpty()) {
            return null;
        }
        List<YsmBoneMath.BonePose> poses = new ArrayList<>(bones.size());
        for (Object bone : bones) {
            if (bone == null || !a.boneClass.isInstance(bone)) {
                return null;
            }
            YsmBoneMath.BonePose pose = new YsmBoneMath.BonePose(
                    floatValue(a.rotationX.invoke(bone)),
                    floatValue(a.rotationY.invoke(bone)),
                    floatValue(a.rotationZ.invoke(bone)),
                    floatValue(a.positionX.invoke(bone)),
                    floatValue(a.positionY.invoke(bone)),
                    floatValue(a.positionZ.invoke(bone)),
                    floatValue(a.scaleX.invoke(bone)),
                    floatValue(a.scaleY.invoke(bone)),
                    floatValue(a.scaleZ.invoke(bone)),
                    floatValue(a.pivotX.invoke(bone)),
                    floatValue(a.pivotY.invoke(bone)),
                    floatValue(a.pivotZ.invoke(bone)));
            if (!pose.isUsable()) {
                return null;
            }
            poses.add(pose);
        }
        if (!boneDiagnosed) {
            boneDiagnosed = true;
            StringBuilder sb = new StringBuilder("[光环诊断] 骨骼数=").append(poses.size());
            for (int i = 0; i < poses.size(); i++) {
                YsmBoneMath.BonePose p = poses.get(i);
                sb.append(String.format(" | 骨骼%d: pos=(%.2f,%.2f,%.2f) pivot=(%.2f,%.2f,%.2f) scale=(%.2f,%.2f,%.2f)",
                        i, p.positionX(), p.positionY(), p.positionZ(),
                        p.pivotX(), p.pivotY(), p.pivotZ(),
                        p.scaleX(), p.scaleY(), p.scaleZ()));
            }
            com.emberstar.endbounember.EndboundEmber.LOGGER.info(sb.toString());
        }
        return YsmBoneMath.composeHeadMatrix(rootMatrix, poses);
    }

    /** YSM 是否已加载 */
    public static boolean isLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded(YsmSymbols.MOD_ID);
    }

    private static Accessors accessorsFor(Object model) throws ReflectiveOperationException {
        Accessors current = accessors;
        if (current != null) {
            if (!current.modelClass.isInstance(model)) {
                throw new IllegalArgumentException("unexpected YSM model class " + model.getClass().getName());
            }
            return current;
        }
        synchronized (YsmAdapter.class) {
            if (accessors == null) {
                accessors = Accessors.resolve(model.getClass().getClassLoader());
            }
            return accessorsFor(model);
        }
    }

    private static float floatValue(Object value) {
        return ((Number) value).floatValue();
    }

    private record Accessors(Class<?> modelClass, Class<?> boneClass, MethodHandle headBones,
            MethodHandle rotationX, MethodHandle rotationY, MethodHandle rotationZ,
            MethodHandle positionX, MethodHandle positionY, MethodHandle positionZ,
            MethodHandle scaleX, MethodHandle scaleY, MethodHandle scaleZ,
            MethodHandle pivotX, MethodHandle pivotY, MethodHandle pivotZ) {

        private static Accessors resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> modelClass = Class.forName(YsmSymbols.ANIMATED_GEO_MODEL, false, loader);
            Class<?> boneClass = Class.forName(YsmSymbols.BONE, false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType listGetter = MethodType.fromMethodDescriptorString(
                    YsmSymbols.HEAD_BONES_DESCRIPTOR, loader);
            MethodType floatGetter = MethodType.fromMethodDescriptorString(
                    YsmSymbols.BONE_FLOAT_GETTER_DESCRIPTOR, loader);
            return new Accessors(modelClass, boneClass,
                    lookup.findVirtual(modelClass, YsmSymbols.HEAD_BONES_GETTER, listGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.ROTATION_X, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.ROTATION_Y, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.ROTATION_Z, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.POSITION_X, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.POSITION_Y, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.POSITION_Z, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.SCALE_X, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.SCALE_Y, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.SCALE_Z, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.PIVOT_X, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.PIVOT_Y, floatGetter),
                    lookup.findVirtual(boneClass, YsmSymbols.PIVOT_Z, floatGetter));
        }
    }
}
