package com.emberstar.endbounember.client;

import com.emberstar.endbounember.network.TimeTurbulenceTogglePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 时光光环维度传送轮盘：四个方向（主世界 / 下界 / 末地 / 时间乱流）。
 * 按 I 键打开后用鼠标滑动决定方向，松开即发送传送指令。
 */
public final class TimeDimensionWheelScreen extends Screen {
    /** 四个方向对应的翻译键后缀（"overworld" / "nether" / "end" / "time_turbulence"） */
    private static final String[] TARGET_KEYS = {"overworld", "nether", "end", "time_turbulence"};
    /** 四个方向对应的 payload direction 值，顺序与 TARGET_KEYS 相同 */
    private static final int[] DIRECTION_VALUES = {
            TimeTurbulenceTogglePayload.OVERWORLD,
            TimeTurbulenceTogglePayload.NETHER,
            TimeTurbulenceTogglePayload.END,
            TimeTurbulenceTogglePayload.TIME_TURBULENCE
    };
    /** 四个扇区的起始角度（度，0=正右，顺时针递增），每个扇区 90° */
    private static final double[] SECTOR_STARTS = {225.0D, 135.0D, 45.0D, 315.0D};

    /** 鼠标是否按下中（决定是绘制高亮还是直接关闭） */
    private boolean mouseHeld;
    /** 上一帧鼠标角度（用于高亮显示） */
    private double currentAngle;
    /** 当前鼠标坐标（render 里缓存，currentSector 里使用） */
    private int mouseX, mouseY;
    /** 屏幕中心点 */
    private int centerX, centerY;
    /** 轮盘半径 */
    private int radius;

    public TimeDimensionWheelScreen() {
        super(Component.translatable("item.endbound_ember.time_halo"));
    }

    @Override
    protected void init() {
        super.init();
        centerX = width / 2;
        centerY = height / 2;
        // 半径随屏幕缩放，保证四向文字不会挤出
        radius = Math.min(width, height) / 5;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 缓存鼠标坐标供 currentSector() 使用
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        // 半透明深色遮罩（不暂停游戏）
        graphics.fillGradient(0, 0, width, height, 0xB0101010, 0xD0101010);

        // 计算鼠标相对于中心的角度
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        currentAngle = Math.toDegrees(Math.atan2(dy, dx));
        if (currentAngle < 0.0D) {
            currentAngle += 360.0D;
        }

        // 绘制四个扇区
        for (int i = 0; i < 4; i++) {
            int color = (i == currentSector()) ? 0xA0D8A8FF : 0x40FFFFFF;
            // 用 fill 模拟四个三角切片（通过多边形近似）
            drawSector(graphics, i, color);
        }

        // 绘制四个方向文字
        for (int i = 0; i < 4; i++) {
            double midAngle = Math.toRadians(SECTOR_STARTS[i] + 45.0D);
            int textX = centerX + (int) (Math.cos(midAngle) * (radius + 18));
            int textY = centerY + (int) (Math.sin(midAngle) * (radius + 18)) - 6;
            Component label = Component.translatable("endbound_ember.time_halo.destination." + TARGET_KEYS[i]);
            graphics.drawString(font, label,
                    textX - font.width(label) / 2, textY, 0xFFFFFF, true);
        }

        // 中心装饰圆
        graphics.fill(centerX - 8, centerY - 8, centerX + 8, centerY + 8, 0xFF283150);
        graphics.drawString(font, title,
                centerX - font.width(title) / 2, centerY - 4, 0xD0D8E8, false);

        // 关闭提示
        Component hint = Component.translatable("endbound_ember.time_halo.wheel.hint");
        graphics.drawString(font, hint, width / 2 - font.width(hint) / 2, height - 24, 0xB0B0B0, true);
    }

    /** 绘制四分之一圆环扇区（使用矩形近似三角切片） */
    private void drawSector(GuiGraphics graphics, int index, int color) {
        double startDeg = SECTOR_STARTS[index];
        double endDeg = startDeg + 90.0D;
        for (int r = 0; r < radius; r += 2) {
            int x1 = centerX + (int) (r * Math.cos(Math.toRadians(startDeg)));
            int y1 = centerY + (int) (r * Math.sin(Math.toRadians(startDeg)));
            int x2 = centerX + (int) ((r + 2) * Math.cos(Math.toRadians(startDeg)));
            int y2 = centerY + (int) ((r + 2) * Math.sin(Math.toRadians(startDeg)));
            int x3 = centerX + (int) ((r + 2) * Math.cos(Math.toRadians(endDeg)));
            int y3 = centerY + (int) ((r + 2) * Math.sin(Math.toRadians(endDeg)));
            int x4 = centerX + (int) (r * Math.cos(Math.toRadians(endDeg)));
            int y4 = centerY + (int) (r * Math.sin(Math.toRadians(endDeg)));
            graphics.fill(Math.min(x1, Math.min(x2, Math.min(x3, x4))),
                    Math.min(y1, Math.min(y2, Math.min(y3, y4))),
                    Math.max(x1, Math.max(x2, Math.max(x3, x4))) + 1,
                    Math.max(y1, Math.max(y2, Math.max(y3, y4))) + 1, color);
        }
    }

    /** 当前角度命中的扇区索引（0-3），-1 表示在扇区外 */
    private int currentSector() {
        double len = Math.hypot(
                mouseX - centerX, mouseY - centerY);
        if (len > radius || len < 6) {
            return -1;
        }
        for (int i = 0; i < 4; i++) {
            double start = SECTOR_STARTS[i];
            double end = start + 90.0D;
            if (currentAngle >= start && currentAngle < end) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseHeld = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int sector = currentSector();
        if (sector >= 0) {
            PacketDistributor.sendToServer(new TimeTurbulenceTogglePayload(DIRECTION_VALUES[sector]));
        }
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
