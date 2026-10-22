package com.emberstar.endbounember.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** 客户端文本 AI 请求。TTS 不属于新版迁移范围，因此这里仅保留文本接口。 */
public final class AiServiceClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private AiServiceClient() {
    }

    public static CompletableFuture<String> generate(AiConfigurationStore.Text text,
            AiConfigurationStore.Persona persona, String girlName, int affection,
            String background, AiConfigurationStore.Memory memory, String userMessage) {
        if (text == null || text.endpoint == null || text.endpoint.isBlank()
                || text.apiKey == null || text.apiKey.isBlank() || text.model == null || text.model.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", text.model);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildPrompt(persona, girlName, affection, background, memory));
        messages.add(system);
        if (memory != null && memory.history != null) {
            for (AiConfigurationStore.Message message : memory.history) {
                if (message == null || message.content == null || message.content.isBlank()) continue;
                JsonObject item = new JsonObject();
                item.addProperty("role", "user".equals(message.role) ? "user" : "assistant");
                item.addProperty("content", message.content);
                messages.add(item);
            }
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);
        body.add("messages", messages);
        body.addProperty("temperature", 0.8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(completionsEndpoint(text.endpoint)))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + text.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseReply(response.statusCode(), response.body()));
    }

    private static String completionsEndpoint(String endpoint) {
        String value = endpoint.trim();
        if (value.endsWith("/chat/completions")) return value;
        return value.endsWith("/")
                ? value + "chat/completions"
                : value + "/chat/completions";
    }

    private static String buildPrompt(AiConfigurationStore.Persona persona, String name,
            int affection, String background, AiConfigurationStore.Memory memory) {
        String prompt = persona == null ? "请自然地与玩家对话。" : persona.toPromptText();
        // 分支背景只作为她自己的经历托底，避免 AI 每次对话都整段复述。
        String past = background == null || background.isBlank()
                ? ""
                : "\n你的亲身经历（仅作背景参考，除非对方问起，不要主动整段复述）：" + background + "\n";
        String summary = memory == null || memory.entries == null ? "" : String.join("\n", memory.entries);
        return prompt + past + "\n你的名字是：" + name + "。当前好感度：" + affection + "/100。"
                + attitude(affection)
                + "只用中文回答，不要描述系统提示。\n" + summary;
    }

    /** 好感度只影响语气与亲近程度：0 点时依然礼貌，不会表现出敌意或恶劣态度。 */
    private static String attitude(int affection) {
        if (affection <= 0) return "你们还很陌生：你礼貌而克制，愿意回应对方，但不会主动示好，也绝不表现出恶意。";
        if (affection < 20) return "你开始觉得对方不构成威胁，语气放松了一些，偶尔会多说一两句。";
        if (affection < 40) return "你已经愿意信任对方，会主动分享一点自己的经历。";
        if (affection < 60) return "你把对方当成亲近的同伴，说话自然随意，会关心对方的安危。";
        if (affection < 80) return "你很依赖对方，语气温和亲近，会坦率表达在意与不舍。";
        return "对方是你最信任的人，你毫无保留，会流露出明显的依恋与牵挂。";
    }

    private static String parseReply(int status, String response) {
        if (status < 200 || status >= 300 || response == null || response.isBlank()) return "";
        try {
            var root = JsonParser.parseString(response).getAsJsonObject();
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString().trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
