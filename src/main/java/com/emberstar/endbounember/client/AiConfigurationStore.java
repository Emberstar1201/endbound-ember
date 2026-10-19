package com.emberstar.endbounember.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 文本 AI 配置与每只少女的本地对话记忆。 */
public final class AiConfigurationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AiConfiguration value;

    private AiConfigurationStore() {
    }

    public static synchronized AiConfiguration get() {
        if (value == null) value = load();
        return value;
    }

    public static synchronized void save() {
        if (value == null) return;
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/endbound_ember_ai.json");
    }

    private static AiConfiguration load() {
        try {
            if (Files.isRegularFile(path())) {
                AiConfiguration loaded = GSON.fromJson(Files.readString(path()), AiConfiguration.class);
                if (loaded != null) return loaded.defaults();
            }
        } catch (Exception ignored) {
        }
        return new AiConfiguration().defaults();
    }

    public static Memory memory(UUID id) {
        return get().memories.computeIfAbsent(id.toString(), key -> new Memory()).defaults();
    }

    public static void add(UUID id, String user, String assistant) {
        Memory memory = memory(id);
        memory.history.add(new Message("user", limit(user, 1000)));
        memory.history.add(new Message("assistant", limit(assistant, 1000)));
        while (memory.history.size() > 32) memory.history.remove(0);
        save();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public static final class AiConfiguration {
        public Text text = new Text();
        public Map<String, Persona> personas = new LinkedHashMap<>();
        public Map<String, Memory> memories = new LinkedHashMap<>();

        AiConfiguration defaults() {
            if (text == null) text = new Text();
            if (text.provider == null) text.provider = "OpenAI";
            if (personas == null) personas = new LinkedHashMap<>();
            if (memories == null) memories = new LinkedHashMap<>();
            personas.putIfAbsent("zombie_girl", new Persona("丧尸娘", "警惕而温柔，保留人类记忆。"));
            personas.putIfAbsent("drowned_girl", new Persona("溺尸娘", "安静敏感，熟悉河流和海洋。"));
            personas.putIfAbsent("survivor_girl", new Persona("幸存者少女", "谨慎善良，重视同伴和安全。"));
            for (Memory memory : memories.values()) if (memory != null) memory.defaults();
            return this;
        }
    }

    public static final class Text {
        public String provider = "OpenAI";
        public String endpoint = "https://api.openai.com/v1";
        public String apiKey = "";
        public String model = "gpt-4o-mini";
    }

    public static final class Persona {
        public String name;
        public String prompt;

        Persona() {
        }

        Persona(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        public String toPromptText() {
            return (prompt == null || prompt.isBlank()) ? "请自然地与玩家对话。" : prompt;
        }
    }

    public static final class Memory {
        public List<Message> history = new ArrayList<>();
        public List<String> entries = new ArrayList<>();

        Memory defaults() {
            if (history == null) history = new ArrayList<>();
            if (entries == null) entries = new ArrayList<>();
            return this;
        }
    }

    public static final class Message {
        public String role;
        public String content;

        Message() {
        }

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
