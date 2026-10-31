package com.emberstar.endbounember.compat.ysm;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * YSM 骨骼数值的换算与 locator 矩阵合成。
 * <p>
 * 骨骼数值与 YSM 内部 locator 的语义一致：pivot 与 position 以模型像素为单位，
 * 旋转以角度为单位。合成算法等价于 YSM 内部的 {@code prepMatrixForLocator}。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code LocatorMath}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmBoneMath {
    /** 模型像素到格子的换算 */
    public static final float PIXEL = 1.0F / 16.0F;

    private YsmBoneMath() {
    }

    /**
     * 按 YSM 的 locator 规则合成骨骼链矩阵。
     * <p>
     * 每节骨骼依次执行：位移、平移到 pivot、按 ZYX 顺序旋转、缩放；除链末骨骼外，
     * 还要再平移出 pivot。链末骨骼故意停留在自己的 pivot 上，这正是 Head locator 的锚点。
     *
     * @param root      渲染入口处 {@code PoseStack} 的栈顶矩阵，即实体根变换
     * @param hierarchy Head locator 骨骼链，从根骨骼到 Head
     * @return 合成后的矩阵；输入无效时返回 {@code null}
     */
    public static Matrix4f composeHeadMatrix(Matrix4f root, List<BonePose> hierarchy) {
        if (root == null || hierarchy == null || hierarchy.isEmpty() || !isFinite(root)) {
            return null;
        }
        Matrix4f matrix = new Matrix4f(root);
        for (int i = 0; i < hierarchy.size(); i++) {
            BonePose bone = hierarchy.get(i);
            if (bone == null || !bone.isUsable()) {
                return null;
            }
            // YSM 的模型空间 X 轴与原版相反，因此位移的 X 取负
            matrix.translate(-bone.positionX() * PIXEL, bone.positionY() * PIXEL, bone.positionZ() * PIXEL);
            matrix.translate(bone.pivotX() * PIXEL, bone.pivotY() * PIXEL, bone.pivotZ() * PIXEL);
            if (bone.rotationX() != 0.0F || bone.rotationY() != 0.0F || bone.rotationZ() != 0.0F) {
                matrix.rotate(new Quaternionf().rotationZYX(
                        bone.rotationZ(), bone.rotationY(), bone.rotationX()));
            }
            matrix.scale(bone.scaleX(), bone.scaleY(), bone.scaleZ());
            // 中间骨骼要退出自己的 pivot，链末的 Head 则停留在 pivot 上作为锚点
            if (i + 1 < hierarchy.size()) {
                matrix.translate(-bone.pivotX() * PIXEL, -bone.pivotY() * PIXEL, -bone.pivotZ() * PIXEL);
            }
        }
        return isFinite(matrix) ? matrix : null;
    }

    /** 矩阵的 16 个元素是否全部为有限值 */
    public static boolean isFinite(Matrix4f matrix) {
        return matrix != null
                && Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01())
                && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
                && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21())
                && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31())
                && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    /**
     * 一节骨骼在 locator 合成中所需的 12 个数值。
     *
     * @param rotationX 绕 X 轴旋转（度）
     * @param rotationY 绕 Y 轴旋转（度）
     * @param rotationZ 绕 Z 轴旋转（度）
     * @param positionX 位移 X（模型像素）
     * @param positionY 位移 Y（模型像素）
     * @param positionZ 位移 Z（模型像素）
     * @param scaleX    缩放 X
     * @param scaleY    缩放 Y
     * @param scaleZ    缩放 Z
     * @param pivotX    旋转轴心 X（模型像素）
     * @param pivotY    旋转轴心 Y（模型像素）
     * @param pivotZ    旋转轴心 Z（模型像素）
     */
    public record BonePose(
            float rotationX, float rotationY, float rotationZ,
            float positionX, float positionY, float positionZ,
            float scaleX, float scaleY, float scaleZ,
            float pivotX, float pivotY, float pivotZ) {

        /** 12 个分量均为有限值且缩放不为 0，否则该骨骼无法参与矩阵合成 */
        public boolean isUsable() {
            return Float.isFinite(rotationX) && Float.isFinite(rotationY) && Float.isFinite(rotationZ)
                    && Float.isFinite(positionX) && Float.isFinite(positionY) && Float.isFinite(positionZ)
                    && Float.isFinite(scaleX) && Float.isFinite(scaleY) && Float.isFinite(scaleZ)
                    && Float.isFinite(pivotX) && Float.isFinite(pivotY) && Float.isFinite(pivotZ)
                    && scaleX != 0.0F && scaleY != 0.0F && scaleZ != 0.0F;
        }
    }
}
