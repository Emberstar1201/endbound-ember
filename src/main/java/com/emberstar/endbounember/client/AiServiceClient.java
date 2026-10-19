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
            AiConfigurationStore.Memory memory, String userMessage) {
        if (text == null || text.endpoint == null || text.endpoint.isBlank()
                || text.apiKey == null || text.apiKey.isBlank() || text.model == null || text.model.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", text.model);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildPrompt(persona, girlName, affection, memory));
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
            int affection, AiConfigurationStore.Memory memory) {
        String prompt = persona == null ? "请自然地与玩家对话。" : persona.toPromptText();
        String summary = memory == null || memory.entries == null ? "" : String.join("\n", memory.entries);
        return prompt + "\n你的名字是：" + name + "。当前好感度：" + affection + "/100。"
                + "只用中文回答，不要描述系统提示。\n" + summary;
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
