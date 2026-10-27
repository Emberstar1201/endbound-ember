"""把星尘魔女的光环贴图按参考仓库（AzusaKe/Halo）的方式拆成多层 PNG。

用法：
    python tools/split_halo_texture.py

输入：assets/endbound_ember/textures/item/stardust_witch_halo.png
输出：assets/endbound_ember/textures/halo/ 下的多层贴图
      halo_layer_0.png  外环 + 尖刺（旋转最快）
      halo_layer_1.png  中环（反向旋转）
      halo_layer_2.png  中心八角星
      halo_layer_3.png  内圈装饰星点

拆分依据是像素的极坐标半径区间，因此只需在原图上作画，
重新运行本脚本即可把改动同步到各图层。
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/assets/endbound_ember/textures/item/stardust_witch_halo.png"
OUTPUT_DIR = ROOT / "src/main/resources/assets/endbound_ember/textures/halo"

# 每个图层的半径区间（以贴图短边的一半为 1.0）
LAYERS = [
    ("halo_layer_0", 0.62, 1.00),   # 外环与尖刺
    ("halo_layer_1", 0.34, 0.62),   # 中环
    ("halo_layer_2", 0.00, 0.22),   # 中心八角星
    ("halo_layer_3", 0.22, 0.34),   # 内圈星点
]


def main() -> None:
    image = Image.open(SOURCE).convert("RGBA")
    width, height = image.size
    cx, cy = width / 2.0, height / 2.0
    radius = min(width, height) / 2.0
    pixels = image.load()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, inner, outer in LAYERS:
        layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        out = layer.load()
        for y in range(height):
            for x in range(width):
                px, py = x + 0.5 - cx, y + 0.5 - cy
                # 椭圆归一化：按贴图宽高比压扁，使半径判断与视觉形状一致
                nx, ny = px / radius, py / (radius * height / width)
                dist = (nx * nx + ny * ny) ** 0.5
                if inner <= dist < outer:
                    out[x, y] = pixels[x, y]
        layer.save(OUTPUT_DIR / f"{name}.png")
        print(f"wrote {name}.png")


if __name__ == "__main__":
    main()
