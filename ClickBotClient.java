package com.example.clickbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class ClickBotClient implements ClientModInitializer {
    private static final String WATCHER_URL = "http://127.0.0.1:3100/latest";
    private static final double REACH = 2.8;
    private static final float CHASE_TURN_SMOOTHING = 0.045f;
    private static final float CHASE_MAX_YAW_STEP = 1.15f;
    private static final float CHASE_MAX_PITCH_STEP = 0.75f;
    private static final long GIVE_UP_MS = 15000;
    private static final int AFK_INTERVAL_TICKS = 600;
    private static final int AFK_STEP_TICKS = 3;
    private static final double LABEL_RADIUS = 2.0;
    private static final double AMBIGUITY_MARGIN = 0.4;
    private static final int MAX_EXTRA_WORDS = 2;
    private static final Set<String> LETTERS = Set.of("e", "m", "o");
    private static final double STRAFE_RANGE = 1.0;
    private static final double DRIFT_LIMIT = 1.0;
    private static final int STRAFE_FLIP_TICKS = 30;

    private static final AtomicReference<String> incoming = new AtomicReference<>(null);
    private static int masterKey = GLFW.GLFW_KEY_F8;
    private static int menuKey = GLFW.GLFW_KEY_F9;

    private static final Map<String, Integer> KEY_NAMES = new TreeMap<>();
    static {
        for (int i = 1; i <= 12; i++) KEY_NAMES.put("f" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        KEY_NAMES.put("insert", GLFW.GLFW_KEY_INSERT);
        KEY_NAMES.put("delete", GLFW.GLFW_KEY_DELETE);
        KEY_NAMES.put("home", GLFW.GLFW_KEY_HOME);
        KEY_NAMES.put("end", GLFW.GLFW_KEY_END);
        KEY_NAMES.put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        KEY_NAMES.put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        KEY_NAMES.put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KEY_NAMES.put("rightcontrol", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEY_NAMES.put("rightalt", GLFW.GLFW_KEY_RIGHT_ALT);
        KEY_NAMES.put("backslash", GLFW.GLFW_KEY_BACKSLASH);
        KEY_NAMES.put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        KEY_NAMES.put("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        KEY_NAMES.put("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        KEY_NAMES.put("leftbracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        KEY_NAMES.put("rightbracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        KEY_NAMES.put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        KEY_NAMES.put("scrolllock", GLFW.GLFW_KEY_SCROLL_LOCK);
        KEY_NAMES.put("pause", GLFW.GLFW_KEY_PAUSE);
        for (int i = 0; i <= 9; i++) KEY_NAMES.put("numpad" + i, GLFW.GLFW_KEY_KP_0 + i);
    }
    private static boolean masterKeyWasDown = false;
    private static boolean guiKeyWasDown = false;
    private static boolean openGui = false;
    private static volatile Process watcherProc = null;
    private static String watcherFolder = "";
    private static volatile String discordToken = "";
    private static volatile String discordChannel = "";
    private static final long DISCORD_POLL_MS = 1000;
    private static final List<String> MODEL_NAMES =
        List.of("test events", "spoke", "wemmbu", "flame", "test", "parrot");
    private static final Pattern DETECT = Pattern.compile(
        "(?<!\\w)(?:test events|spoke|wemmbu|flame|test|parrot|e|m|o)(?!\\w)|\\d+", Pattern.CASE_INSENSITIVE);
    private static String lastTestText = "wemmbu e";
    private static boolean enabled = false;
    private static boolean afk = false;
    private static boolean strafe = true;
    private static boolean sprintJump = true;
    private static boolean strafeHomeSet = false;
    private static double homeX = 0;
    private static double homeZ = 0;
    private static boolean strafeGoLeft = true;
    private static int strafeTicks = 0;

    private static Entity chase = null;
    private static String chaseName = "";
    private static long chaseStart = 0;
    private static int chaseTicks = 0;
    private static String lastDiag = "";
    private static final StringBuilder lastExplain = new StringBuilder();
    private static Entity chaseLabel = null;
    private static Entity foundLabel = null;
    private static String ambiguousMsg = null;

    private static int afkTicks = 0;
    private static int afkPhase = 0;

    @Override
    public void onInitializeClient() {
        loadSettings();
        Runtime.getRuntime().addShutdownHook(new Thread(ClickBotClient::stopWatcher));
        startPoller();
        startDiscordReader();
        ClientTickEvents.END_CLIENT_TICK.register(ClickBotClient::onTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("clickbot")
                .then(literal("activate").executes(ctx -> doActivate()))
                .then(literal("on").executes(ctx -> doActivate()))
                .then(literal("deactivate").executes(ctx -> doDeactivate()))
                .then(literal("off").executes(ctx -> doDeactivate()))
                .then(literal("master").executes(ctx -> {
                    toggleMaster(MinecraftClient.getInstance());
                    return 1;
                }))
                .then(literal("toggle").executes(ctx -> {
                    toggleMaster(MinecraftClient.getInstance());
                    return 1;
                }))
                .then(literal("menu").executes(ctx -> {
                    openGui = true;
                    return 1;
                }))
                .then(literal("gui").executes(ctx -> {
                    openGui = true;
                    return 1;
                }))
                .then(literal("menukey")
                    .executes(ctx -> {
                        showKey(MinecraftClient.getInstance(), true);
                        return 1;
                    })
                    .then(argument("key", StringArgumentType.word()).executes(ctx ->
                        setKeyByName(MinecraftClient.getInstance(), true, StringArgumentType.getString(ctx, "key")) ? 1 : 0)))
                .then(literal("masterkey")
                    .executes(ctx -> {
                        showKey(MinecraftClient.getInstance(), false);
                        return 1;
                    })
                    .then(argument("key", StringArgumentType.word()).executes(ctx ->
                        setKeyByName(MinecraftClient.getInstance(), false, StringArgumentType.getString(ctx, "key")) ? 1 : 0)))
                .then(literal("setchannel")
                    .then(argument("id", StringArgumentType.word()).executes(ctx ->
                        saveChannel(MinecraftClient.getInstance(), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
                .then(literal("cleartoken").executes(ctx -> {
                    discordToken = "";
                    saveSettings();
                    say(MinecraftClient.getInstance(), "Bot token removed.");
                    return 1;
                }))
                .then(literal("setfolder")
                    .then(argument("path", StringArgumentType.greedyString()).executes(ctx ->
                        saveFolder(MinecraftClient.getInstance(), StringArgumentType.getString(ctx, "path")) ? 1 : 0)))
                .then(literal("status").executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    say(mc, "master=" + (enabled ? "ON" : "OFF")
                        + ", discord=" + discordMode()
                        + ", strafe=" + strafe + ", sprintjump=" + sprintJump + ", afk=" + afk
                        + ", chasing=" + (chase != null ? chaseName : "no"));
                    return 1;
                }))
                .then(literal("strafe")
                    .then(literal("on").executes(ctx -> {
                        setStrafe(MinecraftClient.getInstance(), true);
                        return 1;
                    }))
                    .then(literal("off").executes(ctx -> {
                        setStrafe(MinecraftClient.getInstance(), false);
                        return 1;
                    })))
                .then(literal("sprintjump")
                    .then(literal("on").executes(ctx -> {
                        setSprintJump(MinecraftClient.getInstance(), true);
                        return 1;
                    }))
                    .then(literal("off").executes(ctx -> {
                        setSprintJump(MinecraftClient.getInstance(), false);
                        return 1;
                    })))
                .then(literal("afk")
                    .then(literal("on").executes(ctx -> {
                        setAfk(MinecraftClient.getInstance(), true);
                        return 1;
                    }))
                    .then(literal("off").executes(ctx -> {
                        setAfk(MinecraftClient.getInstance(), false);
                        return 1;
                    })))
                .then(literal("list").executes(ctx -> {
                    saveEntityList(MinecraftClient.getInstance());
                    return 1;
                }))
                .then(literal("explain")
                    .then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null || mc.world == null) return 0;
                        String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT).trim();
                        Entity found = findClickable(mc, name);
                        say(mc, "Explain \"" + name + "\":");
                        for (String line : lastExplain.toString().split("\n")) {
                            if (!line.isBlank()) say(mc, line);
                        }
                        say(mc, found == null ? "Result: would NOT click (" + lastDiag + ")"
                            : "Result: would click " + typeId(found) + " #" + found.getId());
                        return 1;
                    })))
                .then(literal("test")
                    .then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
                        runTest(MinecraftClient.getInstance(), StringArgumentType.getString(ctx, "name")) ? 1 : 0)))
            )
        );
    }

    private static Path settingsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("clickbot.properties");
    }

    private static void loadSettings() {
        try {
            Path f = settingsFile();
            if (!Files.exists(f)) return;
            Properties pr = new Properties();
            try (var in = Files.newInputStream(f)) {
                pr.load(in);
            }
            strafe = Boolean.parseBoolean(pr.getProperty("strafe", "true"));
            watcherFolder = pr.getProperty("watcherfolder", "");
            discordToken = pr.getProperty("discordtoken", "");
            discordChannel = pr.getProperty("discordchannel", "");
            sprintJump = Boolean.parseBoolean(pr.getProperty("sprintjump", "true"));
            afk = Boolean.parseBoolean(pr.getProperty("afk", "false"));
            masterKey = readKey(pr.getProperty("masterkey"), masterKey);
            menuKey = readKey(pr.getProperty("menukey"), menuKey);
        } catch (Exception ignored) {
        }
    }

    private static void saveSettings() {
        try {
            Properties pr = new Properties();
            pr.setProperty("strafe", String.valueOf(strafe));
            pr.setProperty("sprintjump", String.valueOf(sprintJump));
            pr.setProperty("afk", String.valueOf(afk));
            pr.setProperty("watcherfolder", watcherFolder);
            pr.setProperty("discordtoken", discordToken);
            pr.setProperty("discordchannel", discordChannel);
            pr.setProperty("masterkey", String.valueOf(masterKey));
            pr.setProperty("menukey", String.valueOf(menuKey));
            try (var out = Files.newOutputStream(settingsFile())) {
                pr.store(out, "ClickBot settings");
            }
        } catch (Exception ignored) {
        }
    }

    private static void setStrafe(MinecraftClient mc, boolean on) {
        strafe = on;
        saveSettings();
        strafeHomeSet = false;
        releaseKeys(mc);
        say(mc, on ? "Strafe ON: it strafes left/right near one spot while the master switch is on."
                   : "Strafe OFF");
    }

    private static void setSprintJump(MinecraftClient mc, boolean on) {
        sprintJump = on;
        saveSettings();
        say(mc, on ? "Sprint-jump ON" : "Sprint-jump OFF (only jumps over obstacles)");
    }

    private static void setAfk(MinecraftClient mc, boolean on) {
        afk = on;
        afkTicks = 0;
        afkPhase = 0;
        saveSettings();
        releaseKeys(mc);
        say(mc, on ? "Anti-AFK wiggle ON (every 30s; strafe overrides it)" : "Anti-AFK wiggle OFF");
    }

    private static boolean runTest(MinecraftClient mc, String raw) {
        String name = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
        if (name.isEmpty()) {
            say(mc, "Type a target first, e.g. wemmbu e");
            return false;
        }
        if (!enabled) {
            say(mc, "Turn the master switch on first (" + keyLabel(masterKey) + " or /clickbot activate), then test.");
            return false;
        }
        lastTestText = name;
        incoming.set(name);
        say(mc, "Test trigger: " + name);
        return true;
    }

    private static boolean saveFolder(MinecraftClient mc, String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (!new File(path, "discord_watcher.js").exists()) {
            say(mc, "discord_watcher.js was not found in: " + path);
            return false;
        }
        watcherFolder = path;
        saveSettings();
        say(mc, "Watcher folder saved: " + path);
        return true;
    }

    private static void saveEntityList(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        Map<String, Integer> counts = new TreeMap<>();
        List<Entity> all = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            counts.merge(typeId(e), 1, Integer::sum);
            all.add(e);
        }
        all.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));

        StringBuilder out = new StringBuilder();
        out.append("You are at ").append(pos(mc.player)).append("\n");
        out.append("Entity counts: ").append(counts).append("\n\n");
        for (Entity e : all) {
            String type = typeId(e);
            out.append("#").append(e.getId()).append(" ").append(type)
                .append(" dist=").append(fmt(mc.player.distanceTo(e)))
                .append(" at ").append(pos(e)).append(" ");
            if (type.equals("text_display")) {
                String shown = displayText(e);
                out.append("TEXT: \"").append(shown).append("\" (normalized: \"")
                    .append(normalize(shown == null ? "" : shown)).append("\")");
            } else {
                out.append("names: ").append(labels(e));
            }
            out.append("\n");
        }

        say(mc, "Entity counts: " + counts);
        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve("clickbot-list.txt");
            Files.writeString(file, out.toString());
            say(mc, "Full list (" + all.size() + " entities) saved to: " + file);
        } catch (Exception ex) {
            say(mc, "Could not save the list file: " + ex.getMessage());
        }
    }

    private static int readKey(String saved, int fallback) {
        try {
            int code = Integer.parseInt(saved.trim());
            return KEY_NAMES.containsValue(code) ? code : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String keyName(int code) {
        for (Map.Entry<String, Integer> e : KEY_NAMES.entrySet()) {
            if (e.getValue() == code) return e.getKey();
        }
        return "key" + code;
    }

    private static String keyLabel(int code) {
        return keyName(code).toUpperCase(Locale.ROOT);
    }

    private static void showKey(MinecraftClient mc, boolean menu) {
        say(mc, (menu ? "Menu key" : "Master-switch key") + " is " + keyLabel(menu ? menuKey : masterKey)
            + ". Change it with /clickbot " + (menu ? "menukey" : "masterkey") + " <name>. Names: "
            + KEY_NAMES.keySet());
    }

    private static boolean setKeyByName(MinecraftClient mc, boolean menu, String raw) {
        String name = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        Integer code = KEY_NAMES.get(name);
        if (code == null) {
            say(mc, "Unknown key \"" + raw + "\". Names: " + KEY_NAMES.keySet());
            return false;
        }
        return setKey(mc, menu, code);
    }

    private static boolean setKey(MinecraftClient mc, boolean menu, int code) {
        if (menu) {
            if (code == masterKey) {
                say(mc, keyLabel(code) + " is already the master-switch key. Pick another key.");
                return false;
            }
            menuKey = code;
        } else {
            if (code == menuKey) {
                say(mc, keyLabel(code) + " is already the menu key. Pick another key.");
                return false;
            }
            masterKey = code;
        }
        saveSettings();
        say(mc, (menu ? "Menu key" : "Master-switch key") + " is now " + keyLabel(code) + " (saved).");
        return true;
    }

    private static int doActivate() {
        setMaster(MinecraftClient.getInstance(), true, true);
        return 1;
    }

    private static int doDeactivate() {
        setMaster(MinecraftClient.getInstance(), false, true);
        return 1;
    }

    private static boolean discordReady() {
        return !discordToken.isBlank() && !discordChannel.isBlank();
    }

    private static String discordMode() {
        if (discordReady()) return "built-in";
        if (watcherRunning()) return "Node watcher";
        return "not set up";
    }

    private static boolean saveToken(MinecraftClient mc, String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.regionMatches(true, 0, "Bot ", 0, 4)) t = t.substring(4).trim();
        if (t.length() < 30) {
            say(mc, "That does not look like a bot token (too short). Copy it from the Discord Developer Portal > Bot.");
            return false;
        }
        discordToken = t;
        saveSettings();
        say(mc, "Bot token saved.");
        return true;
    }

    private static boolean saveChannel(MinecraftClient mc, String raw) {
        String c = raw == null ? "" : raw.trim();
        if (!c.matches("\\d{15,25}")) {
            say(mc, "The channel ID must be a long number. In Discord: right-click the channel > Copy Channel ID.");
            return false;
        }
        discordChannel = c;
        saveSettings();
        say(mc, "Discord channel saved.");
        return true;
    }

    private static String parseDiscordTarget(String text) {
        List<String> matches = new ArrayList<>();
        Matcher m = DETECT.matcher(text);
        while (m.find()) matches.add(m.group().toLowerCase(Locale.ROOT));

        int nameIdx = -1;
        for (int i = 0; i < matches.size(); i++) {
            if (MODEL_NAMES.contains(matches.get(i))) { nameIdx = i; break; }
        }
        if (nameIdx < 0) return null;

        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < matches.size(); i++) {
            if (!LETTERS.contains(matches.get(i))) continue;
            int d = Math.abs(i - nameIdx);
            if (d < bestDist || (d == bestDist && i > nameIdx)) { best = i; bestDist = d; }
        }
        String name = matches.get(nameIdx);
        return best == -1 ? name : name + " " + matches.get(best);
    }

    private static void uiSay(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> say(mc, msg));
    }

    private static HttpResponse<String> discordGet(HttpClient http, String token, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10" + path))
            .timeout(Duration.ofSeconds(8))
            .header("Authorization", "Bot " + token)
            .header("User-Agent", "DiscordBot (clickbot, 1.0)")
            .GET()
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String reportDiscordError(int code, String previous) {
        String msg = switch (code) {
            case 401 -> "Discord rejected the bot token. Enter it again in the menu (" + keyLabel(menuKey) + ").";
            case 403 -> "The bot cannot see that channel. Give it View Channel + Read Message History there.";
            case 404 -> "Discord channel not found. Check the channel ID, and that the bot is in that server.";
            default -> "Discord returned HTTP " + code + ".";
        };
        if (!msg.equals(previous)) uiSay(msg);
        return msg;
    }

    private record DiscordMsg(long id, String idText, String content, String authorId) { }

    private static void startDiscordReader() {
        Thread t = new Thread(() -> {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String lastId = null;
            String botId = null;
            String botIdToken = null;
            String lastError = null;
            while (true) {
                try {
                    if (!enabled || !discordReady()) {
                        lastId = null;
                        lastError = null;
                        Thread.sleep(500);
                        continue;
                    }
                    String token = discordToken;
                    String channel = discordChannel;

                    if (botId == null || !token.equals(botIdToken)) {
                        HttpResponse<String> me = discordGet(http, token, "/users/@me");
                        if (me.statusCode() != 200) {
                            lastError = reportDiscordError(me.statusCode(), lastError);
                            Thread.sleep(5000);
                            continue;
                        }
                        botId = JsonParser.parseString(me.body()).getAsJsonObject().get("id").getAsString();
                        botIdToken = token;
                        lastId = null;
                    }

                    String path = "/channels/" + channel + "/messages?limit="
                        + (lastId == null ? "1" : "50&after=" + lastId);
                    HttpResponse<String> res = discordGet(http, token, path);
                    int code = res.statusCode();
                    if (code == 429) {
                        double wait = 2.0;
                        try {
                            wait = JsonParser.parseString(res.body()).getAsJsonObject().get("retry_after").getAsDouble();
                        } catch (Exception ignored) { }
                        Thread.sleep((long) (Math.min(wait, 10.0) * 1000) + 100);
                        continue;
                    }
                    if (code != 200) {
                        lastError = reportDiscordError(code, lastError);
                        Thread.sleep(5000);
                        continue;
                    }

                    JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
                    List<DiscordMsg> msgs = new ArrayList<>();
                    for (var el : arr) {
                        JsonObject o = el.getAsJsonObject();
                        String idText = o.get("id").getAsString();
                        String content = (o.has("content") && !o.get("content").isJsonNull())
                            ? o.get("content").getAsString() : "";
                        String authorId = o.getAsJsonObject("author").get("id").getAsString();
                        msgs.add(new DiscordMsg(Long.parseLong(idText), idText, content, authorId));
                    }
                    msgs.sort(Comparator.comparingLong(DiscordMsg::id));

                    if (lastId == null) {
                        lastId = msgs.isEmpty() ? "0" : msgs.get(msgs.size() - 1).idText();
                        uiSay("Discord reader connected. Listening for new messages.");
                    } else {
                        for (DiscordMsg m : msgs) {
                            lastId = m.idText();
                            if (m.authorId().equals(botId)) continue;
                            String target = parseDiscordTarget(m.content());
                            if (target != null) incoming.set(target);
                        }
                    }
                    lastError = null;
                    Thread.sleep(DISCORD_POLL_MS);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    String msg = "Discord reader problem: " + e.getMessage();
                    if (!msg.equals(lastError)) uiSay(msg);
                    lastError = msg;
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }, "clickbot-discord");
        t.setDaemon(true);
        t.start();
    }

    private static void toggleMaster(MinecraftClient mc) {
        setMaster(mc, !enabled, true);
    }

    private static void setMaster(MinecraftClient mc, boolean on, boolean announce) {
        incoming.set(null);
        if (on) {
            enabled = true;
            strafeHomeSet = false;
            String source = null;
            if (discordReady()) {
                source = "built-in Discord reader";
            } else if (!watcherFolder.isEmpty() && startWatcher(mc)) {
                source = "Node watcher";
            }
            if (announce) {
                if (source == null) {
                    say(mc, "MASTER ON, but Discord is not set up. Open the menu (" + keyLabel(menuKey)
                        + ") and enter your bot token and channel ID.");
                } else {
                    say(mc, "MASTER ON - " + source + ", strafing " + (strafe ? "on" : "off") + ".");
                }
            }
        } else {
            enabled = false;
            stopChase(mc);
            stopWatcher();
            if (announce) say(mc, "MASTER OFF - Discord reading and strafing stopped.");
        }
    }

    private static boolean watcherRunning() {
        Process pr = watcherProc;
        return pr != null && pr.isAlive();
    }

    private static boolean startWatcher(MinecraftClient mc) {
        if (watcherRunning()) return true;
        if (watcherFolder.isEmpty()) {
            say(mc, "No watcher folder set. Run /clickbot setfolder <folder containing discord_watcher.js>, or start the watcher yourself.");
            return false;
        }
        File dir = new File(watcherFolder);
        if (!new File(dir, "discord_watcher.js").exists()) {
            say(mc, "discord_watcher.js not found in " + watcherFolder + ". Fix it with /clickbot setfolder <folder>.");
            return false;
        }
        try {
            File log = FabricLoader.getInstance().getConfigDir().resolve("clickbot-watcher.log").toFile();
            ProcessBuilder pb = new ProcessBuilder("node", "discord_watcher.js");
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process started = pb.start();
            watcherProc = started;
            started.onExit().thenAccept(pr -> {
                if (watcherProc == pr) {
                    watcherProc = null;
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> say(client, "The Discord watcher stopped (exit code " + pr.exitValue()
                        + "). If you already run it yourself that is fine; otherwise see " + log));
                }
            });
            return true;
        } catch (Exception e) {
            say(mc, "Could not start Node (" + e.getMessage() + "). Is Node.js installed? You can start the watcher yourself instead.");
            return false;
        }
    }

    private static void stopWatcher() {
        Process pr = watcherProc;
        watcherProc = null;
        if (pr != null) pr.destroyForcibly();
    }

    private static Text masterLabel() {
        return Text.literal(enabled ? "Master switch: ON  (click to turn off)"
                                    : "Master switch: OFF  (click to turn on)");
    }

    private static Text statusText() {
        return Text.literal("Discord: " + discordMode()
            + "   |   Strafe: " + (strafe ? "on" : "off"));
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    private static class ControlScreen extends Screen {
        private ButtonWidget masterBtn;
        private ButtonWidget statusBtn;
        private ButtonWidget strafeBtn;
        private ButtonWidget sprintBtn;
        private ButtonWidget afkBtn;
        private ButtonWidget keyBtn;
        private boolean waitingForKey = false;
        private TextFieldWidget testField;
        private TextFieldWidget tokenField;
        private TextFieldWidget channelField;
        private int refreshCounter = 0;

        ControlScreen() {
            super(Text.literal("ClickBot"));
        }

        private ButtonWidget button(Text label, int x, int y, int w, ButtonWidget.PressAction action) {
            return this.addDrawableChild(ButtonWidget.builder(label, action).dimensions(x, y, w, 20).build());
        }

        @Override
        protected void init() {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = 220;
            int x = this.width / 2 - w / 2;
            int y = Math.max(4, this.height / 2 - 116);
            int step = 21;

            masterBtn = button(masterLabel(), x, y, w, b -> {
                toggleMaster(mc);
                refresh();
            });
            y += step;

            statusBtn = button(statusText(), x, y, w, b -> { });
            statusBtn.active = false;
            y += step;

            strafeBtn = button(Text.literal(""), x, y, w, b -> {
                setStrafe(mc, !strafe);
                refresh();
            });
            y += step;

            sprintBtn = button(Text.literal(""), x, y, w, b -> {
                setSprintJump(mc, !sprintJump);
                refresh();
            });
            y += step;

            afkBtn = button(Text.literal(""), x, y, w, b -> {
                setAfk(mc, !afk);
                refresh();
            });
            y += step;

            keyBtn = button(Text.literal(""), x, y, w, b -> {
                waitingForKey = true;
                refresh();
            });
            y += step;

            testField = new TextFieldWidget(this.textRenderer, x, y, w - 64, 20, Text.literal("Test target"));
            testField.setMaxLength(60);
            testField.setText(lastTestText);
            this.addDrawableChild(testField);
            button(Text.literal("Test"), x + w - 60, y, 60, b -> runTest(mc, testField.getText()));
            y += step;

            tokenField = new TextFieldWidget(this.textRenderer, x, y, w - 84, 20, Text.literal("Bot token"));
            tokenField.setMaxLength(200);
            tokenField.setSuggestion("Paste bot token");
            tokenField.setChangedListener(t -> tokenField.setSuggestion(t.isEmpty() ? "Paste bot token" : null));
            this.addDrawableChild(tokenField);
            button(Text.literal("Save token"), x + w - 80, y, 80, b -> {
                if (saveToken(mc, tokenField.getText())) tokenField.setText("");
                refresh();
            });
            y += step;

            channelField = new TextFieldWidget(this.textRenderer, x, y, w - 84, 20, Text.literal("Channel ID"));
            channelField.setMaxLength(30);
            channelField.setText(discordChannel);
            channelField.setSuggestion(discordChannel.isEmpty() ? "Channel ID" : null);
            channelField.setChangedListener(t -> channelField.setSuggestion(t.isEmpty() ? "Channel ID" : null));
            this.addDrawableChild(channelField);
            button(Text.literal("Save channel"), x + w - 80, y, 80, b -> {
                saveChannel(mc, channelField.getText());
                refresh();
            });
            y += step;

            button(Text.literal("Save entity list (clickbot-list.txt)"), x, y, w, b -> saveEntityList(mc));
            y += step;

            button(Text.literal("Close"), x, y, w, b -> this.close());

            refresh();
        }

        private void refresh() {
            masterBtn.setMessage(masterLabel());
            statusBtn.setMessage(statusText());
            strafeBtn.setMessage(Text.literal("Strafing while on: " + onOff(strafe)));
            sprintBtn.setMessage(Text.literal("Sprint-jump to model: " + onOff(sprintJump)));
            afkBtn.setMessage(Text.literal("Anti-AFK wiggle: " + onOff(afk)));
            keyBtn.setMessage(Text.literal(waitingForKey
                ? "Press the new menu key now (F-keys, Insert, Home...)"
                : "Menu key: " + keyLabel(menuKey) + "  (click to change)"));
        }

        @Override
        public void tick() {
            super.tick();
            if (waitingForKey) {
                long window = GLFW.glfwGetCurrentContext();
                if (window != 0L) {
                    for (int k : KEY_NAMES.values()) {
                        if (GLFW.glfwGetKey(window, k) == GLFW.GLFW_PRESS) {
                            waitingForKey = false;
                            setKey(MinecraftClient.getInstance(), true, k);
                            refresh();
                            break;
                        }
                    }
                }
            }
            if (++refreshCounter % 10 == 0) refresh();
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    private static void startPoller() {
        Thread t = new Thread(() -> {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(WATCHER_URL))
                .timeout(Duration.ofMillis(800)).build();
            long lastId = -1;
            boolean baseline = false;
            while (true) {
                try {
                    {
                        String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
                        int bar = body.indexOf('|');
                        if (bar > 0) {
                            long id = Long.parseLong(body.substring(0, bar));
                            String target = body.substring(bar + 1).trim().toLowerCase(Locale.ROOT);
                            if (!baseline) {
                                baseline = true;
                                lastId = id;
                            } else if (id < lastId) {
                                lastId = id;
                            } else if (id > lastId) {
                                lastId = id;
                                if (!target.isEmpty()) incoming.set(target);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "clickbot-poller");
        t.setDaemon(true);
        t.start();
    }

    private static void onTick(MinecraftClient mc) {
        String target = incoming.getAndSet(null);
        if (mc.player == null || mc.world == null) {
            chase = null;
            chaseLabel = null;
            strafeHomeSet = false;
            return;
        }

        long window = GLFW.glfwGetCurrentContext();
        if (window != 0L) {
            boolean masterDown = mc.currentScreen == null && GLFW.glfwGetKey(window, masterKey) == GLFW.GLFW_PRESS;
            if (masterDown && !masterKeyWasDown) toggleMaster(mc);
            masterKeyWasDown = masterDown;
            boolean guiDown = mc.currentScreen == null && GLFW.glfwGetKey(window, menuKey) == GLFW.GLFW_PRESS;
            if (guiDown && !guiKeyWasDown) openGui = true;
            guiKeyWasDown = guiDown;
        }
        if (openGui && mc.currentScreen == null) {
            openGui = false;
            mc.setScreen(new ControlScreen());
        }

        if (target != null && !target.isEmpty() && enabled) {
            startChase(mc, target);
        }
        if (chase != null) {
            tickChase(mc);
        } else if (strafe) {
            tickStrafe(mc);
        } else {
            tickAfk(mc);
        }
    }

    private static void startChase(MinecraftClient mc, String wanted) {
        Entity found = findClickable(mc, wanted);
        if (found == null) {
            say(mc, "Can't click \"" + wanted + "\": " + lastDiag);
            return;
        }
        releaseKeys(mc);
        afkPhase = 0;
        afkTicks = 0;
        chase = found;
        chaseLabel = foundLabel;
        chaseName = wanted;
        chaseStart = System.currentTimeMillis();
        chaseTicks = 0;
        String labelText = foundLabel != null ? labelName(foundLabel) : "?";
        double offset = foundLabel != null ? horizontal(found, foundLabel) : 0;
        say(mc, "Running to \"" + wanted + "\": label \"" + labelText + "\" -> " + typeId(found)
            + " #" + found.getId() + " (" + fmt(offset) + " blocks from its label, "
            + fmt(mc.player.distanceTo(found)) + "m from you)...");
    }

    private static void tickChase(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!enabled || chase == null || chase.isRemoved()
            || System.currentTimeMillis() - chaseStart > GIVE_UP_MS) {
            if (chase != null) say(mc, "Gave up on \"" + chaseName + "\".");
            stopChase(mc);
            return;
        }

        if (chaseLabel != null) {
            boolean stillMatches = false;
            for (String l : labels(chaseLabel)) {
                if (textMatches(l, chaseName)) { stillMatches = true; break; }
            }
            if (chaseLabel.isRemoved() || !stillMatches) {
                say(mc, "The label for \"" + chaseName + "\" changed or disappeared. Stopping.");
                stopChase(mc);
                return;
            }
            ambiguousMsg = null;
            Entity now = resolveUnderLabel(mc, chaseLabel);
            if (now == null) {
                say(mc, ambiguousMsg != null ? ambiguousMsg
                    : "Lost the model under \"" + chaseName + "\". Stopping.");
                stopChase(mc);
                return;
            }
            chase = now;
        }

        Vec3d eye = p.getEyePos();
        Box box = chase.getBoundingBox();
        Vec3d aim = new Vec3d(chase.getX(), chase.getY() + chase.getHeight() * 0.82, chase.getZ());
        float yawError = lookAt(p, eye, aim);
        double horizontalDistance = Math.sqrt(p.squaredDistanceTo(chase.getX(), p.getY(), chase.getZ()));
        chaseTicks++;

        Vec3d closest = new Vec3d(
            MathHelper.clamp(eye.x, box.minX, box.maxX),
            MathHelper.clamp(eye.y, box.minY, box.maxY),
            MathHelper.clamp(eye.z, box.minZ, box.maxZ));

        if (eye.distanceTo(closest) <= REACH) {
            releaseKeys(mc);
            if (mc.interactionManager == null) {
                say(mc, "Cannot click right now (no interaction manager).");
                stopChase(mc);
                return;
            }
            mc.interactionManager.attackEntity(p, chase);
            p.swingHand(Hand.MAIN_HAND);
            say(mc, "Clicked \"" + chaseName + "\". Master switch is now OFF (watcher and strafing stopped). Press " + keyLabel(masterKey) + " to start again.");
            setMaster(mc, false, false);
            return;
        }

        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        boolean driftLeft = chaseTicks % 96 >= 8 && chaseTicks % 96 < 11;
        boolean driftRight = chaseTicks % 96 >= 56 && chaseTicks % 96 < 59;
        mc.options.leftKey.setPressed(driftLeft);
        mc.options.rightKey.setPressed(driftRight);
        mc.options.sprintKey.setPressed(true);

        boolean obstacle = p.horizontalCollision && p.isOnGround();
        boolean occasionalHop = sprintJump && p.isOnGround() && horizontalDistance > 9.0
            && chaseTicks % 70 == 0;
        mc.options.jumpKey.setPressed(obstacle || occasionalHop);
    }

    private static void stopChase(MinecraftClient mc) {
        chase = null;
        chaseLabel = null;
        strafeHomeSet = false;
        releaseKeys(mc);
    }

    private static void tickStrafe(MinecraftClient mc) {
        if (!enabled) return;
        ClientPlayerEntity p = mc.player;
        if (!strafeHomeSet) {
            homeX = p.getX();
            homeZ = p.getZ();
            strafeHomeSet = true;
            strafeGoLeft = true;
            strafeTicks = 0;
        }
        double dx = p.getX() - homeX;
        double dz = p.getZ() - homeZ;
        if (dx * dx + dz * dz > 64.0) {
            homeX = p.getX();
            homeZ = p.getZ();
            dx = 0;
            dz = 0;
        }

        double yaw = Math.toRadians(p.getYaw());
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double lateral = dx * leftX + dz * leftZ;
        double forward = dx * fwdX + dz * fwdZ;

        strafeTicks++;
        if (lateral >= STRAFE_RANGE) {
            strafeGoLeft = false;
            strafeTicks = 0;
        } else if (lateral <= -STRAFE_RANGE) {
            strafeGoLeft = true;
            strafeTicks = 0;
        } else if (strafeTicks > STRAFE_FLIP_TICKS) {
            strafeGoLeft = !strafeGoLeft;
            strafeTicks = 0;
        }

        mc.options.leftKey.setPressed(strafeGoLeft);
        mc.options.rightKey.setPressed(!strafeGoLeft);
        mc.options.forwardKey.setPressed(forward < -DRIFT_LIMIT);
        mc.options.backKey.setPressed(forward > DRIFT_LIMIT);
    }

    private static void tickAfk(MinecraftClient mc) {
        if (!afk || !enabled) return;
        afkTicks++;
        if (afkPhase == 0) {
            if (afkTicks >= AFK_INTERVAL_TICKS) {
                afkPhase = 1;
                afkTicks = 0;
                mc.options.forwardKey.setPressed(true);
            }
        } else if (afkPhase == 1) {
            if (afkTicks >= AFK_STEP_TICKS) {
                afkPhase = 2;
                afkTicks = 0;
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(true);
            }
        } else {
            if (afkTicks >= AFK_STEP_TICKS) {
                afkPhase = 0;
                afkTicks = 0;
                mc.options.backKey.setPressed(false);
            }
        }
    }

    private static void releaseKeys(MinecraftClient mc) {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private static float lookAt(ClientPlayerEntity p, Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float bearing = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float gentleDrift = (float) (Math.sin(chaseTicks * 0.018) * 5.5
            + Math.sin(chaseTicks * 0.043) * 1.8);
        float desiredYaw = bearing + gentleDrift;

        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        float desiredPitch = MathHelper.clamp(targetPitch, -10.0f, 10.0f);

        float yawError = MathHelper.wrapDegrees(desiredYaw - p.getYaw());
        float yawStep = MathHelper.clamp(yawError * CHASE_TURN_SMOOTHING,
            -CHASE_MAX_YAW_STEP, CHASE_MAX_YAW_STEP);
        float pitchError = desiredPitch - p.getPitch();
        float pitchStep = MathHelper.clamp(pitchError * 0.045f,
            -CHASE_MAX_PITCH_STEP, CHASE_MAX_PITCH_STEP);

        p.setYaw(p.getYaw() + yawStep);
        p.setPitch(MathHelper.clamp(p.getPitch() + pitchStep, -90.0f, 90.0f));
        return yawError;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static final String SMALL_CAPS_FROM =
        "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
        + "\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";
    private static final String SMALL_CAPS_TO = "abcdefghijklmnopqrstuvwxyz";

    private static String normalize(String s) {
        String clean = s.replaceAll("(?i)§[0-9A-FK-ORX]", "");
        String t = Normalizer.normalize(clean, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            int i = SMALL_CAPS_FROM.indexOf(c);
            sb.append(i >= 0 ? SMALL_CAPS_TO.charAt(i) : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static int matchQuality(String label, String wanted) {
        String a = normalize(label);
        String b = normalize(wanted);
        if (a.isEmpty() || b.isEmpty()) return -1;
        if (a.equals(b) || a.replace(" ", "").equals(b.replace(" ", ""))) return 0;
        String[] aw = a.split(" ");
        String[] bw = b.split(" ");
        if (aw.length > bw.length + MAX_EXTRA_WORDS) return -1;
        for (int i = 0; i + bw.length <= aw.length; i++) {
            boolean same = true;
            for (int j = 0; j < bw.length; j++) {
                if (!aw[i + j].equals(bw[j])) { same = false; break; }
            }
            if (same) return 1;
        }
        return -1;
    }

    private static boolean textMatches(String label, String wanted) {
        return matchQuality(label, wanted) >= 0;
    }

    private static String pos(Entity e) {
        return fmt(e.getX()) + ", " + fmt(e.getY()) + ", " + fmt(e.getZ());
    }

    private static String typeId(Entity e) {
        return Registries.ENTITY_TYPE.getId(e.getType()).getPath();
    }

    private static boolean isDisplay(Entity e) {
        String t = typeId(e);
        return t.equals("text_display") || t.equals("block_display") || t.equals("item_display");
    }

    private static int clickPriority(Entity e) {
        if (e instanceof PlayerEntity) return 0;
        String t = typeId(e);
        if (t.equals("interaction")) return 1;
        if (e instanceof LivingEntity) return 2;
        return 99;
    }

    private static String displayText(Entity e) {
        if (!typeId(e).equals("text_display")) return null;
        StringBuilder sb = new StringBuilder();
        try {
            for (Field f : e.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!TrackedData.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object td = f.get(null);
                if (td == null) continue;
                Object v = e.getDataTracker().get((TrackedData<?>) td);
                if (v instanceof Text t) sb.append(t.getString()).append(' ');
            }
        } catch (Throwable ignored) { }
        if (sb.length() == 0) {
            try {
                for (Method m : e.getClass().getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && Text.class.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        Object v = m.invoke(e);
                        if (v instanceof Text t) sb.append(t.getString()).append(' ');
                    }
                }
            } catch (Throwable ignored) { }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static List<String> labels(Entity e) {
        List<String> out = new ArrayList<>();
        try { out.add(e.getName().getString().toLowerCase(Locale.ROOT)); } catch (Exception ignored) { }
        try { out.add(e.getDisplayName().getString().toLowerCase(Locale.ROOT)); } catch (Exception ignored) { }
        try {
            Text custom = e.getCustomName();
            if (custom != null) out.add(custom.getString().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) { }
        try {
            String shown = displayText(e);
            if (shown != null && !shown.isEmpty()) out.add(shown.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) { }
        return out;
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Entity resolveUnderLabel(MinecraftClient mc, Entity label) {
        ClientPlayerEntity me = mc.player;
        if (label instanceof PlayerEntity) return label;

        Entity vehicle = label.getVehicle();
        if (vehicle != null && vehicle != me && clickPriority(vehicle) < 99) return vehicle;

        List<Entity> near = new ArrayList<>();
        for (Entity o : mc.world.getEntities()) {
            if (o == me || o == label || clickPriority(o) >= 99) continue;
            double below = label.getY() - o.getY();
            if (below < -1.5 || below > 5.0) continue;
            if (horizontal(o, label) > LABEL_RADIUS) continue;
            near.add(o);
        }
        if (near.isEmpty()) return isDisplay(label) ? null : label;

        near.sort(Comparator.comparingDouble(o -> horizontal(o, label)));
        Entity first = near.get(0);
        double firstH = horizontal(first, label);

        Entity chosen = first;
        for (Entity o : near) {
            if (horizontal(o, label) - firstH <= 0.05 && clickPriority(o) < clickPriority(chosen)) chosen = o;
        }

        for (Entity o : near) {
            if (horizontal(o, first) > 0.5 && horizontal(o, label) - firstH < AMBIGUITY_MARGIN) {
                ambiguousMsg = "two models are almost equally close to the label \"" + labelName(label)
                    + "\" (" + typeId(first) + " #" + first.getId() + " and " + typeId(o) + " #" + o.getId()
                    + "). Not clicking, to avoid hitting the wrong one.";
                return null;
            }
        }

        if (conflictsWithSiblingLabel(mc, label, chosen)) return null;
        return chosen;
    }

    private static boolean conflictsWithSiblingLabel(MinecraftClient mc, Entity targetLabel, Entity model) {
        String normalized = normalize(labelName(targetLabel));
        String[] parts = normalized.split(" ");
        if (parts.length < 2) return false;
        String suffix = parts[parts.length - 1];
        if (!LETTERS.contains(suffix)) return false;
        String base = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        double ownDistance = horizontal(model, targetLabel);

        for (Entity otherLabel : mc.world.getEntities()) {
            if (otherLabel == targetLabel || !typeId(otherLabel).equals("text_display")) continue;
            String otherText = normalize(labelName(otherLabel));
            String[] otherParts = otherText.split(" ");
            if (otherParts.length < 2) continue;
            String otherSuffix = otherParts[otherParts.length - 1];
            if (!LETTERS.contains(otherSuffix) || otherSuffix.equals(suffix)) continue;
            String otherBase = String.join(" ", Arrays.copyOf(otherParts, otherParts.length - 1));
            if (!base.equals(otherBase)) continue;

            double siblingDistance = horizontal(model, otherLabel);
            if (siblingDistance <= ownDistance + AMBIGUITY_MARGIN) {
                ambiguousMsg = "model " + typeId(model) + " #" + model.getId()
                    + " is also as close to sibling label \"" + labelName(otherLabel)
                    + "\" as to \"" + labelName(targetLabel)
                    + "\". Not clicking, to avoid confusing the model letters.";
                return true;
            }
        }
        return false;
    }

    private static String labelName(Entity label) {
        String shown = displayText(label);
        if (shown != null && !shown.isEmpty()) return shown;
        try { return label.getName().getString(); } catch (Exception e) { return "?"; }
    }

    private static Entity findClickable(MinecraftClient mc, String wanted) {
        ClientPlayerEntity me = mc.player;

        String[] words = normalize(wanted).split(" ");
        if (!LETTERS.contains(words[words.length - 1])) {
            Set<String> letters = new HashSet<>();
            for (Entity m : mc.world.getEntities()) {
                if (m == me) continue;
                for (String l : labels(m)) {
                    if (!textMatches(l, wanted)) continue;
                    String[] t = normalize(l).split(" ");
                    String last = t[t.length - 1];
                    if (LETTERS.contains(last)) letters.add(last);
                    break;
                }
            }
            if (letters.size() > 1) {
                lastDiag = "\"" + wanted + "\" matches several models (letters " + letters
                    + ") but the message has no letter. Not clicking.";
                return null;
            }
        }
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        int labelsFound = 0;
        Entity firstLabel = null;
        Entity bestLabel = null;
        ambiguousMsg = null;
        foundLabel = null;

        lastExplain.setLength(0);

        List<Entity> cands = new ArrayList<>();
        List<Integer> quality = new ArrayList<>();
        int minQ = Integer.MAX_VALUE;
        for (Entity m : mc.world.getEntities()) {
            if (m == me) continue;
            int q = -1;
            for (String l : labels(m)) {
                int lq = matchQuality(l, wanted);
                if (lq >= 0 && (q < 0 || lq < q)) q = lq;
            }
            if (q < 0) continue;
            cands.add(m);
            quality.add(q);
            if (q < minQ) minQ = q;
        }
        labelsFound = cands.size();
        if (!cands.isEmpty()) firstLabel = cands.get(0);

        for (int i = 0; i < cands.size(); i++) {
            Entity m = cands.get(i);
            int q = quality.get(i);
            String text = labelName(m);
            if (q != minQ) {
                lastExplain.append("label \"").append(text).append("\" at ").append(pos(m))
                    .append(" -> ignored (only a loose match; a better label exists)\n");
                continue;
            }

            Entity resolved = resolveUnderLabel(mc, m);

            lastExplain.append("label \"").append(text).append("\" (").append(q == 0 ? "exact" : "loose")
                .append(") at ").append(pos(m)).append(" -> ");
            if (resolved == null) {
                lastExplain.append("nothing clickable under it, or too close to another model\n");
            } else {
                lastExplain.append(typeId(resolved)).append(" #").append(resolved.getId()).append(", ")
                    .append(fmt(horizontal(resolved, m))).append(" blocks from the label, ")
                    .append(fmt(me.distanceTo(resolved))).append("m from you\n");
                double d = me.distanceTo(resolved);
                if (d < bestScore) { bestScore = d; best = resolved; bestLabel = m; }
            }
        }

        foundLabel = best != null ? bestLabel : null;

        if (best == null) {
            if (labelsFound > 0 && ambiguousMsg != null) {
                lastDiag = ambiguousMsg;
            } else if (labelsFound == 0) {
                int total = 0;
                List<Entity> readable = new ArrayList<>();
                for (Entity o : mc.world.getEntities()) {
                    if (!typeId(o).equals("text_display")) continue;
                    total++;
                    String shown = displayText(o);
                    if (shown != null && !shown.isEmpty()) readable.add(o);
                }
                readable.sort(Comparator.comparingDouble(o -> me.squaredDistanceTo(o)));
                StringBuilder sample = new StringBuilder();
                for (int i = 0; i < Math.min(6, readable.size()); i++) {
                    String shown = displayText(readable.get(i));
                    if (shown.length() > 40) shown = shown.substring(0, 40);
                    if (i > 0) sample.append(" | ");
                    sample.append('"').append(shown).append('"');
                }
                lastDiag = "no name/text matching that is loaded near you. Text displays loaded: " + total
                    + " (readable: " + readable.size() + "). Nearest texts: " + sample;
            } else {
                StringBuilder near = new StringBuilder();
                for (Entity o : mc.world.getEntities()) {
                    if (o == me || o == firstLabel) continue;
                    if (o.distanceTo(firstLabel) <= 4.0) {
                        if (near.length() > 0) near.append(", ");
                        near.append(typeId(o));
                    }
                }
                lastDiag = "found the label but nothing clickable under it. Entities within 4 blocks of it: ["
                    + near + "].";
            }
        }
        return best;
    }

    private static void say(MinecraftClient mc, String msg) {
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal("[ClickBot] " + msg), false);
        }
    }
}
