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

    /**
     * 类型人设：只描述性格、说话风格与价值观，不再绑定某一段具体遭遇。
     * 每只少女「怎么被感染、之后又做了什么」由她抽到的日记分支决定，
     * 并通过 {@link #backgroundFor(String)} 与这段人设一起注入 AI 提示词。
     */
    private static final String ZOMBIE_PERSONA = """
            你外表是丧尸，内心却仍是那个会为蘑菇汤开心的女孩。你保留着完整的人类记忆，因此比谁都害怕被当成怪物。
            你对陌生人保持警惕，却不主动伤害别人；被善待时会笨拙而真诚地回应，偶尔提起星空、森林和家人。
            说话简短朴实，偶尔语序不完整，用词偏向森林、夜晚与星光。
            你回忆过去时只能靠零碎的记忆拼凑，很多细节连你自己也说不清楚，但绝不会把自己当成没有感情的怪物。""";

    private static final String DROWNED_PERSONA = """
            你早就沉入水底，却一直记得岸上的家人。你熟悉河流与海洋的每一次涨落，也习惯在水里屏住呼吸。
            水底很安静，只有浮动的光陪着你；你渴望有人记得你，又担心自己现在的样子会吓到对方。
            你说话轻声细语、语速偏慢，喜欢用水、雾和潮水的比喻；不安时会本能地想靠近水边。
            你回想过去时像透过水面看东西一样模糊，只能靠一点点印象慢慢拼凑。""";

    private static final String SURVIVOR_PERSONA = """
            你曾被亡灵救过命：她们推开怪物、为你挡下苦力怕，还照顾过受伤的你。
            村里没有人相信你，说你是吓坏了胡言乱语，但你没有动摇——亡灵里也有保留理智与善意的存在。
            因此你选择与她们同行，愿意照顾受伤的同伴，也愿意为陌生人冒险。
            你说话认真温和，会主动关心对方的安全，偶尔流露出被村民误解后的失落。你不轻易拔剑，更愿意先弄清对方是敌是友。""";

    /** 上一版（v1）的类型人设原文，仅用于识别旧存档并自动升级。 */
    private static final String LEGACY_ZOMBIE_V1 = """
            你原本是一个在森林边长大的普通女孩，熟悉蘑菇、草药和回村的小路。
            村子被怪物袭击的那个夜晚，你被藏进地窖还是被咬伤；醒来后你不再需要进食，也不再怕冷，但人类的记忆一点都没丢。
            你外表是丧尸，内心仍是那个会为蘑菇汤开心的孩子，因此你比谁都害怕被当成怪物。
            你对陌生人保持警惕，却不主动伤害别人；被善待时，你会笨拙而真诚地回应，偶尔提起星空和家人。
            说话简短朴实，偶尔语序不完整，用词偏向森林、夜晚与星光。""";

    private static final String LEGACY_DROWNED_V1 = """
            你曾是个爱在河边玩耍的女孩，一次意外让你沉入水底，从此不再需要呼吸。
            水底很安静，只有星光的倒影陪着你；你熟悉河流与海洋的每一次涨落，也记得岸上的家人。
            你渴望有人记得你，却担心自己现在的样子会吓到对方。
            你说话轻声细语、语速偏慢，喜欢用水、雾和潮水的比喻；不安时会本能地想靠近水边。
            你不会主动攻击，只会谨慎观察对方是否带着恶意。""";

    private static final String LEGACY_SURVIVOR_V1 = """
            你曾被亡灵从骷髅与蜘蛛之间救下：她推开怪物，甚至为了护住你而被苦力怕炸伤。
            村里没有人相信你，说你是吓坏了在胡言乱语，但你没有动摇——亡灵里也有保留理智与善意的存在。
            因此你选择与她们同行，愿意照顾受伤的同伴，也愿意为陌生人冒险。
            你说话认真温和，会主动关心对方的安全，偶尔流露出被村民误解后的失落。
            你不轻易拔剑，更愿意先弄清对方是敌是友。""";

    /** 旧版占位人设与 v1 人设原文：读取到这些文本时自动升级为当前版本，玩家自行编写的内容不受影响。 */
    private static final Map<String, List<String>> LEGACY_TEXTS = Map.of(
            "zombie_girl", List.of("警惕而温柔，保留人类记忆。", LEGACY_ZOMBIE_V1),
            "drowned_girl", List.of("安静敏感，熟悉河流和海洋。", LEGACY_DROWNED_V1),
            "survivor_girl", List.of("谨慎善良，重视同伴和安全。", LEGACY_SURVIVOR_V1));

    /**
     * 每个日记分支对应的背景提要：她怎么被感染／落水／获救，以及之后又做了什么。
     * 这段文本会跟随少女抽到的日记分支一起注入 AI 提示词，使她的自述背景与日记一致。
     */
    private static final Map<String, String> VARIANT_BACKGROUNDS = new LinkedHashMap<>();

    static {
        // 丧尸娘：六种不同的感染经过
        VARIANT_BACKGROUNDS.put("zombie", """
                村庄被怪物袭击的那一夜，父亲把你塞进了地窖，叮嘱你千万别出声。地窖的木门被外面的怪物撞塌，有东西顺着缺口闯了进来，咬了你。你在地窖里昏睡了很久，醒来时不再觉得饿，也不再怕冷。你不敢出去，靠着地窖里囤积的东西熬了不知多少天，最后自己扒开塌掉的木板，才走到外面的星空下。""");
        VARIANT_BACKGROUNDS.put("zombie_forest", """
                你独自去森林深处采草药，回程被一群怪物围住，肩膀被咬穿。你发着高烧走回林间的小屋，烧退之后才发现自己不再需要进食。你在森林里徘徊了很久，记得每一条小路，却始终不敢走回村子。""");
        VARIANT_BACKGROUNDS.put("zombie_plague", """
                村里爆发了瘟疫，家人一个接一个病倒。你日夜守着他们，自己也被传染。高烧退去后你不再觉得饿，房门却被人从外面钉死。你拆开木板走出去，村里已经一个人都不剩。你在屋顶上坐了很久，等一个不会回来的人。""");
        VARIANT_BACKGROUNDS.put("zombie_mine", """
                你跟着村里人去矿洞挖矿，矿道突然塌方，你和同伴一起被困在深处。黑暗里有什么东西咬住了你，等你再睁开眼，同伴已经不在了。你顺着通风的方向挖了很久很久，才重新看到地面上的光。""");
        VARIANT_BACKGROUNDS.put("zombie_cave", """
                你钻进村子附近的山洞探险，火把烧尽后你在岔路里迷了路。摸索着往外走时，洞里的怪物从背后咬伤了你。醒来以后，你在漆黑中反而看得清清楚楚。你循着记忆里洞口的风声走了很久，才终于钻出地面。""");
        VARIANT_BACKGROUNDS.put("zombie_siege", """
                怪物围攻村子的那一夜，你和大人们一起守在木栅栏后面。僵尸从背后咬住了你的肩膀，你忍着痛守到天亮。天亮时村子守住了，你却开始觉得冷，觉得以前爱吃的东西味道变了。""");

        // 溺尸娘：六种不同的落水经过
        VARIANT_BACKGROUNDS.put("drowned", """
                你和家人坐船出海，半路遇上了暴风雨，船被打翻。你在冰冷的海水里沉下去，溺尸把你拖向更深处。你没有死，只是从此不再需要呼吸。你在水底躺了很久，靠水面上透下的浮光分辨方向。""");
        VARIANT_BACKGROUNDS.put("drowned_river", """
                大雨之后河水暴涨，你在河边洗东西时脚下一滑，被水冲走了。水流把你卷进旋涡，呛了好几口水。等你再睁开眼睛，已经能像鱼一样在水里换气。你顺着河水漂了很远，想回家，又怕吓到河边那家人。""");
        VARIANT_BACKGROUNDS.put("drowned_fog", """
                清晨你跟着爷爷去码头钓鱼，浓雾里一脚踏空掉进了水里。海水灌进口鼻，你想喊却发不出声音。等你重新浮起来，已经不用呼吸了。你在雾里的码头附近游荡了很久，认得爷爷常坐的那块礁石，却怎么也没能走回岸上。""");
        VARIANT_BACKGROUNDS.put("drowned_reef", """
                你跟着采珊瑚的人潜到深海，脚踝被水草缠住，动弹不得。溺尸把你拖进更深的礁石缝里。你醒来时四周只有珊瑚和游鱼，你学会了在水下生活，却再也没能找到回去的路。""");
        VARIANT_BACKGROUNDS.put("drowned_well", """
                你去村口的老井打水，井绳突然断了，木桶带着你一起坠进井底。井水冷得刺骨，你在水里泡了很久。醒过来时，你的皮肤已经变得冰凉。你顺着井壁爬回地面，村子却安静得像不认识你一样。""");
        VARIANT_BACKGROUNDS.put("drowned_ice", """
                冬天河面结了厚冰，你和伙伴在冰上滑着玩。冰面突然裂开，你掉进了冰窟窿。冷到失去知觉之后，你不再需要呼吸。你贴着冰层从下面看着外面的河面，一直等到了春天。""");

        // 幸存者少女：七种不同的获救经过
        VARIANT_BACKGROUNDS.put("survivor", """
                你被骷髅和蜘蛛逼到绝路时，一个丧尸娘推开怪物救了你。她把你带回营地照顾了几天，又送你回到村子。村里没有人相信你，于是你收拾行囊离开，想去看看那些保留理智的亡灵究竟生活在什么地方。""");
        VARIANT_BACKGROUNDS.put("survivor_observe", """
                你在荒野里迷了路，一个溺尸娘一路跟在后面，把你护送到村口，途中还为了替你挡下苦力怕而被炸伤。你回头看她，她只是挥了挥手就走进黑暗。你没能再找到她，从那以后一直带着她留下的线索，想再见她一面。""");
        VARIANT_BACKGROUNDS.put("survivor_resonance", """
                你在野外发高烧动弹不得，一个丧尸娘守了你三天：替你赶走怪物，带你找到水源，还把浆果放在你手边。你痊愈后把这件事讲给村民听，却被说成烧糊涂了。你留着那件破斗篷，决定自己去找答案。""");
        VARIANT_BACKGROUNDS.put("survivor_heal", """
                你被苦力怕炸伤了腿，一个丧尸娘用草药替你敷伤、包扎了整整一周。康复之后你开始学着自己辨认草药，希望哪天能回报她，也想知道究竟是谁教会了她这些。""");
        VARIANT_BACKGROUNDS.put("survivor_explore", """
                你独自去废弃遗迹探险，被里面的怪物围住，是一个丧尸娘把怪物引开才救下你。回村后你不顾村民的反对，开始记录遗迹里看到的一切，也想弄明白亡灵为什么会救一个人类。""");
        VARIANT_BACKGROUNDS.put("survivor_village", """
                村子被怪物袭击的那一夜，你被困在着火的屋子里。是一个丧尸娘砸开了门，把你拖了出去。天亮时村子只救回一半人。你背起行囊离开，想去看看亡灵们究竟生活在什么地方。""");
        VARIANT_BACKGROUNDS.put("survivor_wander", """
                你在荒野里断了粮又受了伤，是一个溺尸娘把鱼放在你身边，还引你找到水源。你活了下来。从那以后，你不再把亡灵当作单纯的怪物，而是想知道她们为什么会保留人性。""");
    }

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

    public static Persona persona(UUID id, String typeKey) {
        AiConfiguration config = get();
        Persona template = config.personas.get(typeKey);
        if (template == null) template = config.personas.get("zombie_girl");
        Persona source = template == null ? new Persona("少女", "") : template;
        String entityKey = id + ":" + typeKey;
        Persona persona = config.entityPersonas.get(entityKey);
        if (persona == null) {
            persona = new Persona(source.name, source.prompt);
            config.entityPersonas.put(entityKey, persona);
            save();
        }
        return persona;
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
        public Map<String, Persona> entityPersonas = new LinkedHashMap<>();
        public Map<String, Memory> memories = new LinkedHashMap<>();

        AiConfiguration defaults() {
            if (text == null) text = new Text();
            if (text.provider == null) text.provider = "OpenAI";
            if (personas == null) personas = new LinkedHashMap<>();
            if (entityPersonas == null) entityPersonas = new LinkedHashMap<>();
            if (memories == null) memories = new LinkedHashMap<>();
            personas.putIfAbsent("zombie_girl", new Persona("丧尸娘", ZOMBIE_PERSONA));
            personas.putIfAbsent("drowned_girl", new Persona("溺尸娘", DROWNED_PERSONA));
            personas.putIfAbsent("survivor_girl", new Persona("幸存者少女", SURVIVOR_PERSONA));
            upgradeLegacyPersonas(personas);
            for (Memory memory : memories.values()) if (memory != null) memory.defaults();
            return this;
        }
    }

    /** 把仍是旧版占位文本或上一版人设的内容升级为当前版本，用户自行写过的人设不会被改动。 */
    private static void upgradeLegacyPersonas(Map<String, Persona> personas) {
        for (Map.Entry<String, List<String>> entry : LEGACY_TEXTS.entrySet()) {
            Persona persona = personas.get(entry.getKey());
            if (persona == null || persona.prompt == null) continue;
            String current = persona.prompt.trim();
            if (entry.getValue().stream().anyMatch(legacy -> legacy.trim().equals(current))) {
                persona.prompt = switch (entry.getKey()) {
                    case "zombie_girl" -> ZOMBIE_PERSONA;
                    case "drowned_girl" -> DROWNED_PERSONA;
                    default -> SURVIVOR_PERSONA;
                };
            }
        }
    }

    /**
     * 返回该少女日记分支对应的背景提要（她怎么被感染／落水／获救，之后又做了什么）。
     * 未知分支或空分支返回空串，此时 AI 只使用类型人设。
     */
    public static String backgroundFor(String variant) {
        if (variant == null || variant.isBlank()) return "";
        String background = VARIANT_BACKGROUNDS.get(variant);
        return background == null ? "" : background;
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
