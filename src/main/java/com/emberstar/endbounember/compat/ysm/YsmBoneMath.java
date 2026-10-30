package com.emberstar.endbounember.compat.ysm;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 由 YSM 的 Head locator 骨骼层级合成头部矩阵。
 * <p>
 * 骨骼数值与 YSM 内部 {@code RenderUtils.prepMatrixForLocator} 的语义一致：pivot 与 position
 * 以模型像素为单位（1 像素 = 1/16 格），旋转以角度为单位。合成顺序为
 * 「平移到 pivot → 旋转 → 缩放 → 平移 position」，与 YSM 渲染 locator 时保持一致。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的同名实现，
 * 许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmBoneMath {
    /** 模型像素到格子的换算 */
    private static final float PIXEL = 1.0F / 16.0F;

    private YsmBoneMath() {
    }

    /** 单个骨骼在合成时需要的全部数值 */
    public record BonePose(float rotationX, float rotationY, float rotationZ,
            float positionX, float positionY, float positionZ,
            float scaleX, float scaleY, float scaleZ,
            float pivotX, float pivotY, float pivotZ) {

        /** 数值全部有限且缩放非零时才可用于合成 */
        public boolean isUsable() {
            return Float.isFinite(rotationX) && Float.isFinite(rotationY) && Float.isFinite(rotationZ)
                    && Float.isFinite(positionX) && Float.isFinite(positionY) && Float.isFinite(positionZ)
                    && Float.isFinite(scaleX) && Float.isFinite(scaleY) && Float.isFinite(scaleZ)
                    && Float.isFinite(pivotX) && Float.isFinite(pivotY) && Float.isFinite(pivotZ)
                    && scaleX != 0.0F && scaleY != 0.0F && scaleZ != 0.0F;
        }
    }

    /**
     * 把骨骼层级依次叠乘到根矩阵上，得到 Head locator 的最终矩阵。
     *
     * @param root      渲染该模型时的根矩阵（相机坐标系）
     * @param hierarchy 从根骨骼到 Head 的骨骼链
     * @return 合成结果；数据不可用时返回 {@code null}
     */
    public static Matrix4f composeHeadMatrix(Matrix4f root, List<BonePose> hierarchy) {
        if (root == null || hierarchy == null || hierarchy.isEmpty()) {
            return null;
        }
        // YSM locator 骨骼链的 pivot 是模型空间绝对坐标（像素单位），
        // 链末骨骼的 pivot 就是 Head locator 的最终位置。
        // 中间骨骼仅承载动画层级，position 字段为零时不需要逐层累乘。
        BonePose tail = null;
        for (BonePose bone : hierarchy) {
            if (bone == null || !bone.isUsable()) {
                return null;
            }
            tail = bone;
        }
        Matrix4f result = new Matrix4f(root);
        result.translate(tail.pivotX() * PIXEL, tail.pivotY() * PIXEL, tail.pivotZ() * PIXEL);
        // 链末骨骼的旋转与缩放仍然需要应用（YSM 模型可能带头部倾斜动画）
        Quaternionf rotation = new Quaternionf();
        rotation.rotationZYX(tail.rotationZ() * (float) Math.PI / 180.0F,
                tail.rotationY() * (float) Math.PI / 180.0F,
                tail.rotationX() * (float) Math.PI / 180.0F);
        result.rotate(rotation);
        result.scale(tail.scaleX(), tail.scaleY(), tail.scaleZ());
        return result;
    }

    /** 矩阵元素是否全部有限 */
    public static boolean isFinite(Matrix4f matrix) {
        if (matrix == null) {
            return false;
        }
        float[] values = matrix.get(new float[16]);
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /** 取矩阵的平移分量 */
    public static Vector3f translation(Matrix4f matrix) {
        return matrix.getTranslation(new Vector3f());
    }
}
