package com.emberstar.endbounember.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.entity.ZombieGirl;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 配置界面：布局对齐旧版 1.20.1 的标签页风格。
 * 顶部为「文本配置 / 人设编辑」两个标签页（旧版的 TTS 标签页已按需求移除）。
 */
public final class AiConfigurationScreen extends Screen {
    /** 服务商预设。endpoint 为 null 表示地址由用户手填（New API / 自定义服务商）。 */
    private record Provider(String name, String endpoint) {
        boolean editable() {
            return endpoint == null;
        }
    }

    /** 地址统一填到基础路径（如 https://api.openai.com/v1），请求时再补 /chat/completions。 */
    private static final Provider[] PROVIDERS = {
            new Provider("OpenAI", "https://api.openai.com/v1"),
            new Provider("DeepSeek", "https://api.deepseek.com/v1"),
            new Provider("Kimi", "https://api.moonshot.cn/v1"),
            new Provider("Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            new Provider("硅基流动", "https://api.siliconflow.cn/v1"),
            new Provider("New API", null),
            new Provider("Google (Gemini)", "https://generativelanguage.googleapis.com/v1beta/openai"),
            new Provider("自定义服务商", null),
    };
    private static final String[] PERSONAS = {"zombie_girl", "drowned_girl", "survivor_girl"};
    /** 长报错最多画 3 行，超出部分通过悬停 tooltip 与点击复制查看。 */
    private static final int STATUS_MAX_LINES = 3;
    private static final int STATUS_LINE_HEIGHT = 10;

    private final Screen parent;
    private final int entityId;
    private int section;              // 0 = 文本配置，1 = 人设编辑
    private int providerIndex;
    private int personaIndex;
    private boolean keyVisible;
    private EditBox endpoint;
    private EditBox apiKey;
    private EditBox model;
    private MultiLineEditBox personaText;
    private Component status = Component.empty();
    private int statusX;
    private int statusY;
    private int statusWidth;
    private int statusHeight;
    private boolean statusTruncated;

    public AiConfigurationScreen(Screen parent, int entityId) {
        super(Component.translatable("gui.endbound_ember.ai.config"));
        this.parent = parent;
        this.entityId = entityId;
        this.personaIndex = indexOf(PERSONAS, personaKey());
        this.providerIndex = providerIndexOf(AiConfigurationStore.get().text.provider);
    }

    private ZombieGirl currentGirl() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof ZombieGirl girl ? girl : null;
    }

    private String personaKey() {
        ZombieGirl girl = currentGirl();
        if (girl instanceof HumanGirl) return "survivor_girl";
        if (girl != null && girl.getType().toString().contains("drowned")) return "drowned_girl";
        return "zombie_girl";
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = Math.max(8, width / 2 - 150);
        int right = width / 2 + 8;
        int footerY = Math.max(44, height - 26);
        // 标签页居中：两个 96px 宽按钮，间隔 4px（旧版为三个，TTS 已移除）。
        int tabWidth = 96;
        int tabStart = (width - tabWidth * 2 - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.ai.tab_text"),
                button -> switchSection(0)).bounds(tabStart, 24, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.ai.tab_persona"),
                button -> switchSection(1)).bounds(tabStart + tabWidth + 4, 24, tabWidth, 20).build());

        if (section == 0) {
            initTextSection(left, right, footerY);
        } else {
            initPersonaSection(left, right, footerY);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.back"),
                button -> closeScreen()).bounds(right, footerY, 142, 20).build());
    }

    /** 文本配置页：服务商按钮、端点、密钥（可显隐）、模型 ID、测试/保存。 */
    private void initTextSection(int left, int right, int footerY) {
        int y = 52;
        Provider provider = PROVIDERS[providerIndex];
        addRenderableWidget(Button.builder(Component.literal(provider.name()),
                button -> cycleProvider()).bounds(left, y, 300, 20).build());
        y += 26;
        endpoint = null;
        if (provider.editable()) {
            endpoint = edit(left, y, 300, "API 基础地址", AiConfigurationStore.get().text.endpoint);
            y += 24;
        }
        apiKey = editSecret(left, y, "API 密钥", AiConfigurationStore.get().text.apiKey, visible -> {
            saveFields();
            keyVisible = visible;
            rebuildWidgets();
        });
        model = edit(right, y, 142, "模型 ID", AiConfigurationStore.get().text.model);
        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.ai.test"),
                button -> testText()).bounds(left, footerY, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.save"),
                button -> save()).bounds(left + 74, footerY, 68, 20).build());
    }

    /** 人设编辑页：少女切换按钮 + 大文本框（纯文本人设）+ 字数统计。 */
    private void initPersonaSection(int left, int right, int footerY) {
        int y = 52;
        addRenderableWidget(Button.builder(Component.literal("少女：" + persona().name),
                button -> cyclePersona()).bounds(left, y, 300, 20).build());
        y += 26;
        y += 18;
        int statusY = footerY - 38;
        int textHeight = Math.max(36, statusY - y - 8);
        personaText = new MultiLineEditBox(font, left, y, 300, textHeight,
                Component.empty(), Component.empty());
        personaText.setValue(persona().prompt == null ? "" : persona().prompt);
        personaText.setCharacterLimit(8192);
        addRenderableWidget(personaText);
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.save"),
                button -> save()).bounds(left, footerY, 68, 20).build());
    }

    private EditBox edit(int x, int y, int boxWidth, String hint, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    /** 密钥输入框：隐藏时用圆点显示，右侧附带 20px 的显隐切换按钮。 */
    private EditBox editSecret(int x, int y, String hint, String value, Consumer<Boolean> onToggle) {
        int fieldWidth = 118;
        EditBox box = new EditBox(font, x, y, fieldWidth, 20, Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        if (!keyVisible) {
            box.setFormatter((text, cursor) -> FormattedCharSequence.forward(
                    "•".repeat(text.length()), Style.EMPTY));
        }
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal(keyVisible ? "◉" : "◌"), button -> onToggle.accept(!keyVisible))
                .bounds(x + fieldWidth + 4, y, 20, 20)
                .tooltip(Tooltip.create(Component.translatable(keyVisible
                        ? "gui.endbound_ember.ai.hide_key" : "gui.endbound_ember.ai.show_key")))
                .build());
        return box;
    }

    private void switchSection(int value) {
        saveFields();
        section = value;
        rebuildWidgets();
    }

    private void cycleProvider() {
        saveFields();
        providerIndex = (providerIndex + 1) % PROVIDERS.length;
        Provider provider = PROVIDERS[providerIndex];
        AiConfigurationStore.get().text.provider = provider.name();
        if (provider.editable()) {
            // New API 和自定义服务商不继承上一个预设服务商的地址。
            AiConfigurationStore.get().text.endpoint = "";
        } else {
            AiConfigurationStore.get().text.endpoint = provider.endpoint();
        }
        rebuildWidgets();
    }

    private void cyclePersona() {
        saveFields();
        personaIndex = (personaIndex + 1) % PERSONAS.length;
        rebuildWidgets();
    }

    private AiConfigurationStore.Persona persona() {
        ZombieGirl girl = currentGirl();
        if (girl != null) {
            return AiConfigurationStore.persona(girl.getUUID(), PERSONAS[personaIndex]);
        }
        return AiConfigurationStore.get().personas.computeIfAbsent(PERSONAS[personaIndex],
                key -> new AiConfigurationStore.Persona("少女", ""));
    }

    /** 把当前分区的输入框内容写回配置；重建界面后只存在当前分区的控件。 */
    private void saveFields() {
        var config = AiConfigurationStore.get();
        if (endpoint != null) config.text.endpoint = endpoint.getValue().trim();
        if (apiKey != null) config.text.apiKey = apiKey.getValue().trim();
        if (model != null) config.text.model = model.getValue().trim();
        if (personaText != null) persona().prompt = personaText.getValue();
    }

    private void save() {
        saveFields();
        AiConfigurationStore.save();
        status = Component.translatable("gui.endbound_ember.ai.saved");
    }

    /** 用当前文本配置发一条 ping 请求，验证端点、密钥与模型可用。 */
    private void testText() {
        saveFields();
        AiConfigurationStore.save();
        status = Component.translatable("gui.endbound_ember.ai.test_pending");
        var config = AiConfigurationStore.get();
        AiServiceClient.generate(config.text, persona(), persona().name, 0, "",
                new AiConfigurationStore.Memory(), "ping")
                .thenAccept(result -> minecraft.execute(() -> status = Component.literal(result.isBlank()
                        ? Component.translatable("gui.endbound_ember.ai.test_failed").getString()
                        : Component.translatable("gui.endbound_ember.ai.test_ok").getString())));
    }

    private void closeScreen() {
        saveFields();
        AiConfigurationStore.save();
        minecraft.setScreen(parent);
    }

    private static int indexOf(String[] values, String value) {
        if (value == null) return 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    private static int providerIndexOf(String value) {
        if (value != null) {
            for (int i = 0; i < PROVIDERS.length; i++) {
                if (PROVIDERS[i].name().equals(value)) return i;
            }
        }
        return 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        int footerY = Math.max(44, height - 26);
        int statusWidth = Math.min(300, width - 16);
        statusX = (width - statusWidth) / 2;
        List<FormattedCharSequence> statusLines = font.split(status, statusWidth);
        int visibleLines = Math.min(statusLines.size(), STATUS_MAX_LINES);
        statusTruncated = statusLines.size() > STATUS_MAX_LINES;
        statusHeight = visibleLines * STATUS_LINE_HEIGHT;
        statusY = Math.max(34, footerY - 6 - statusHeight);

        int statusColor = status.getString().startsWith("测试失败") ? 0xFF8080 : 0xA0FFA0;
        for (int i = 0; i < visibleLines; i++) {
            graphics.drawString(font, statusLines.get(i), statusX, statusY + i * STATUS_LINE_HEIGHT, statusColor);
        }
        if (statusTruncated && visibleLines > 0) {
            String more = "…";
            graphics.drawString(font, more, statusX + statusWidth - font.width(more),
                    statusY + (visibleLines - 1) * STATUS_LINE_HEIGHT, statusColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        if (statusTruncated && isMouseOverStatus(mouseX, mouseY)) {
            graphics.renderTooltip(font, font.split(status, Math.max(180, width - 80)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && statusTruncated && isMouseOverStatus(mouseX, mouseY)) {
            // 屏幕内只放摘要；完整信息复制到剪贴板，方便发给服务商排查。
            minecraft.keyboardHandler.setClipboard(status.getString());
            status = Component.translatable("gui.endbound_ember.ai.copied");
            statusTruncated = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverStatus(double mouseX, double mouseY) {
        return statusHeight > 0
                && mouseX >= statusX && mouseX < statusX + statusWidth
                && mouseY >= statusY && mouseY < statusY + statusHeight;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21 默认背景走模糊后期管线，会把裸绘文字一并染糊；改用旧版的半透明暗色遮罩。
        if (minecraft != null && minecraft.level != null) {
            graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
