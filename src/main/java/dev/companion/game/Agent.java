package dev.companion.game;

import dev.companion.core.CompanionConfig;
import dev.companion.core.CompanionState;
import dev.companion.core.ItemNames;
import dev.companion.core.ai.*;
import dev.companion.core.craft.MiningRules;
import dev.companion.core.craft.Recipes;
import dev.companion.core.schem.LitematicParser;
import dev.companion.core.schem.Schematic;
import dev.companion.game.bridge.Baritone;
import dev.companion.game.bridge.Litematica;
import dev.companion.game.task.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.*;

/**
 * The brain/controller. Chat in -> ReAct loop (Ollama) or offline rules -> tools -> tasks, with the
 * Safety reflex layer able to interrupt the running task at any tick.
 */
public final class Agent {
    public final CompanionConfig cfg;
    public final CompanionState state;
    private final Path statePath;

    /** Item ids (and "#logs"/"#planks") that storing must never take away: the current plan's materials. */
    public final Set<String> keep = new HashSet<>();
    /** True while SecureTask is running: creepers are then fought rather than fled from. */
    public boolean guarding;

    private final Safety safety = new Safety();
    private ReActAgent react;
    private Task current, background;
    private final Deque<AgentAction> script = new ArrayDeque<>();
    private boolean active, thinking, llmMode, reflexPaused;
    private int generation, llmErrors;
    private String goal = "";

    public Agent(CompanionConfig cfg, CompanionState state, Path statePath) {
        this.cfg = cfg;
        this.state = state;
        this.statePath = statePath;
    }

    public void saveState() { state.save(statePath); }

    public Set<String> keepSet() { return new HashSet<>(keep); }

    // ------------------------------------------------------------------ chat in

    /** @return true if the chat line was a command for the agent (and must not be sent to the server) */
    public boolean handleChat(String msg) {
        String cmd = null;
        String lower = msg.toLowerCase();
        for (String t : cfg.triggers) {
            if (!t.isEmpty() && lower.startsWith(t.toLowerCase())) { cmd = msg.substring(t.length()).trim(); break; }
        }
        if (cmd == null) {
            if (!cfg.listenToAll || msg.startsWith("/")) return false;
            cmd = msg.trim();
        }
        if (G.mc().inGameHud != null) G.mc().inGameHud.getChatHud().addToMessageHistory(msg);
        G.say("§7> " + cmd);
        String c = cmd.toLowerCase().replaceAll("[.!]+$", "");
        if (c.isEmpty() || c.equals("help")) {
            G.say("Say what you want in plain English, e.g. \"" + cfg.triggers.get(0) + "gather 64 iron ore\", \"build\", \"secure the area\". "
                    + "Other: stop, status, chests, reload. Key K = emergency stop.");
        } else if (c.matches("^(stop|cancel|abort|halt|enough)$")) {
            stopAll("Stopped.");
        } else if (c.equals("status")) {
            G.say(snapshot());
        } else if (c.equals("chests")) {
            G.say(state.chests.summary(30));
        } else if (c.equals("reload")) {
            G.say("Config is read at startup; restart the game to apply changes to config/companion.json.");
        } else {
            startGoal(cmd);
        }
        return true;
    }

    // ------------------------------------------------------------------ lifecycle

    public void stopAll(String why) {
        generation++;
        thinking = false;
        script.clear();
        if (current != null) { current.cancel(this); current = null; }
        if (background != null) { background.cancel(this); background = null; }
        active = false;
        guarding = false;
        keep.clear();
        if (G.ready()) {
            Baritone.cancel();
            safety.reset();
            if (G.p().currentScreenHandler != G.p().playerScreenHandler) G.p().closeHandledScreen();
            if (why != null && !why.isEmpty()) G.say(why);
        }
    }

    private void startGoal(String text) {
        stopAll(null);
        goal = text;
        active = true;
        llmErrors = 0;
        llmMode = cfg.useLlm;
        if (!Baritone.available()) {
            G.say("§eBaritone was not found: moving, mining and building will fail. Install Baritone (fabric) for full functionality.");
        }
        if (llmMode) {
            react = new ReActAgent(new OllamaClient(cfg.ollamaUrl, cfg.model, cfg.temperature, cfg.llmTimeoutSeconds), cfg.maxHistoryMessages);
            react.begin(text);
            requestNext(snapshot());
        } else {
            offlinePlan("Ollama disabled in config");
        }
    }

    private void offlinePlan(String reason) {
        llmMode = false;
        List<AgentAction> plan = RuleBasedPlanner.plan(goal);
        if (plan.isEmpty()) {
            G.say("§c" + reason + ", and I could not parse \"" + goal + "\" with offline rules. Start Ollama (" + cfg.ollamaUrl + ", model " + cfg.model + ") for free-form instructions.");
            active = false;
            return;
        }
        G.say("§e" + reason + "; using offline rules: " + plan.stream().map(AgentAction::toString).toList());
        script.addAll(plan);
        runNextScripted();
    }

    private void runNextScripted() {
        AgentAction next = script.poll();
        if (next == null) { endGoal("Done."); return; }
        execute(next);
    }

    private void endGoal(String summary) {
        active = false;
        thinking = false;
        script.clear();
        if (summary != null && !summary.isEmpty()) G.say("§a" + summary);
    }

    // ------------------------------------------------------------------ ReAct loop

    private void requestNext(String observation) {
        if (react.steps() >= cfg.maxReactSteps) { endGoal("Step limit (" + cfg.maxReactSteps + ") reached, stopping. Last state: " + observation.lines().findFirst().orElse("")); return; }
        thinking = true;
        final int gen = generation;
        final boolean first = react.steps() == 0;
        react.next(observation).whenComplete((act, err) -> G.mc().execute(() -> {
            if (gen != generation) return; // stopped or superseded meanwhile
            thinking = false;
            if (err != null) {
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                if (first) { offlinePlan("Ollama unreachable or invalid (" + cause.getMessage() + ")"); return; }
                if (++llmErrors <= 2) { G.say("§eLLM error, retrying: " + cause.getMessage()); requestNext(observation); return; }
                endGoal("Giving up: " + cause.getMessage());
                return;
            }
            llmErrors = 0;
            if (cfg.chatReplies && !act.thought().isBlank()) G.say("§7" + act.thought());
            execute(act);
        }));
    }

    /** Result of a tool call: either an instant observation, a task to run, or the end of the goal. */
    private void execute(AgentAction act) {
        Object r;
        try {
            r = dispatch(act);
        } catch (RuntimeException e) {
            r = "FAILURE: " + act.action() + " crashed: " + e;
        }
        if (r instanceof Task t) {
            if (t.background()) {
                if (background != null) background.cancel(this);
                background = t;
                t.begin(this);
                observe(t.status() == Task.Status.FAILED ? "FAILURE: " + t.message() : "OK: " + t.describe() + " is now running in the background");
            } else {
                if (background != null) { background.cancel(this); background = null; }
                current = t;
                G.say("§8" + t.describe());
                t.begin(this);
                if (t.status() != Task.Status.RUNNING) taskFinished(t);
            }
        } else if (r instanceof String s) {
            if (s.startsWith("#FINISH ")) endGoal(s.substring(8));
            else observe(s);
        }
    }

    private void observe(String obs) {
        if (llmMode) requestNext(obs + "\n" + snapshot());
        else if (obs.startsWith("FAILURE")) endGoal("Stopped: " + obs);
        else runNextScripted();
    }

    private void taskFinished(Task t) {
        current = null;
        boolean ok = t.status() == Task.Status.DONE;
        G.say((ok ? "§2✓ " : "§c✗ ") + t.message());
        String reflex = safety.takeEvent();
        String obs = (ok ? "SUCCESS: " : "FAILURE: ") + t.message() + (reflex.isEmpty() ? "" : " [reflex: " + reflex + "]");
        observe(obs);
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (!G.ready()) {
            if (active || current != null || background != null) stopAll(null);
            return;
        }
        if (!active && current == null && background == null) return;

        boolean wasBusy = safety.active();
        safety.tick(this);
        boolean busy = safety.active();
        if (busy && !wasBusy) {
            if (current != null) current.pause(this);
            if (background != null) background.pause(this);
            reflexPaused = true;
        } else if (!busy && wasBusy && reflexPaused) {
            reflexPaused = false;
            if (current != null) current.resume(this);
            if (background != null) background.resume(this);
        }
        if (busy) return;

        if (background != null) {
            background.update(this);
            if (background.status() != Task.Status.RUNNING) {
                G.say("§e" + background.message());
                background = null;
            }
        }
        if (current != null) {
            current.update(this);
            if (current.status() != Task.Status.RUNNING) taskFinished(current);
        }
    }

    // ------------------------------------------------------------------ tools

    private static BlockPos blockPos(AgentAction a) {
        return new BlockPos(a.integer("x", 0), a.integer("y", 64), a.integer("z", 0));
    }

    private Object dispatch(AgentAction a) {
        switch (a.action()) {
            case "goto" -> { return new GotoTask(blockPos(a), 1); }
            case "goto_waypoint" -> {
                int[] w = state.waypoints.get(a.str("name", "").toLowerCase());
                if (w == null) return "FAILURE: no waypoint '" + a.str("name", "") + "'. Known: " + state.waypoints.keySet();
                return new GotoTask(new BlockPos(w[0], w[1], w[2]), 1);
            }
            case "set_waypoint" -> {
                BlockPos p = a.has("x") ? blockPos(a) : G.pos();
                state.waypoints.put(a.str("name", "home").toLowerCase(), new int[]{p.getX(), p.getY(), p.getZ()});
                saveState();
                return "OK: waypoint '" + a.str("name", "home").toLowerCase() + "' saved at " + p.toShortString();
            }
            case "follow" -> { return new FollowTask(a.str("player", null)); }
            case "stop" -> {
                stopAll("Stopped.");
                return "#FINISH Stopped.";
            }
            case "gather" -> {
                String item = ItemNames.normalize(a.str("item", ""));
                int n = Math.max(1, a.integer("count", 1));
                if (Recipes.craftFor(item) != null || Recipes.smeltFor(item) != null) return new CraftTask(item, G.count(item) + n);
                return new GatherTask(item, n);
            }
            case "craft", "smelt" -> {
                String item = ItemNames.normalize(a.str("item", ""));
                if (item.equals("planks")) item = "oak_planks";
                return new CraftTask(item, Math.max(1, a.integer("count", 1)));
            }
            case "ensure_tool" -> {
                String tool = ItemNames.normalize(a.str("tool", ""));
                if (!tool.contains("_")) return "FAILURE: ensure_tool needs a full item name like iron_pickaxe";
                String type = tool.substring(tool.indexOf('_') + 1);
                var want = dev.companion.core.craft.ToolTier.ofItem(tool);
                if (MiningRules.bestTier(G.inventory(), type).level >= want.level) return "OK: already have a " + type + " of tier " + MiningRules.bestTier(G.inventory(), type);
                return new CraftTask(tool, 1);
            }
            case "schematic_info" -> { return schematicInfo(); }
            case "build" -> { return new BuildTask(a.has("x") ? blockPos(a) : null); }
            case "deposit" -> {
                String it = a.has("item") ? ItemNames.normalize(a.str("item", "")) : null;
                return new StoreTask(it, keepSet());
            }
            case "fetch" -> { return new FetchTask(ItemNames.normalize(a.str("item", "")), Math.max(1, a.integer("count", 1))); }
            case "place_chest" -> { return new PlaceChestTask(); }
            case "set_storage_zone" -> {
                BlockPos p = a.has("x") ? blockPos(a) : G.pos();
                state.storageZone = new int[]{p.getX(), p.getY(), p.getZ()};
                saveState();
                return "OK: storage zone centre set to " + p.toShortString();
            }
            case "deliver" -> {
                return new DeliverTask(ItemNames.normalize(a.str("item", "")), Math.max(1, a.integer("count", 1)), a.str("player", null));
            }
            case "secure_area" -> { return new SecureTask(a.integer("radius", 20)); }
            case "eat" -> {
                safety.forceEat = true;
                return "OK: eating (reflex) - food level " + G.p().getHungerManager().getFoodLevel() + "/20";
            }
            case "look_around" -> { return snapshot(); }
            case "say" -> {
                G.say(a.str("message", ""));
                return "OK: said it";
            }
            case "finish" -> { return "#FINISH " + a.str("summary", "Done."); }
            default -> { return "FAILURE: unknown tool " + a.action(); }
        }
    }

    // ------------------------------------------------------------------ observations

    private Optional<Litematica.Active> activeSchematic() {
        Optional<Litematica.Active> a = Litematica.selectedPlacement();
        return a.isPresent() ? a : Litematica.newestFile(G.pos());
    }

    private String schematicInfo() {
        Optional<Litematica.Active> act = activeSchematic();
        if (act.isEmpty()) return "FAILURE: no schematic loaded in Litematica and no .litematic file in the schematics folder";
        Schematic s;
        try {
            s = LitematicParser.parse(act.get().file());
        } catch (Exception e) {
            return "FAILURE: cannot read " + act.get().file().getFileName() + ": " + e.getMessage();
        }
        Map<String, Integer> need = s.materials(), have = G.inventory();
        StringBuilder sb = new StringBuilder("Schematic '" + s.name + "' (" + (act.get().fromLitematica() ? "Litematica placement" : "newest file in schematics/")
                + "): " + s.blocks().size() + " blocks, " + s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ() + ", " + s.layerCount()
                + " layers, origin " + act.get().origin().toShortString() + "\nMaterials (needed / inventory / chests):\n");
        int shown = 0;
        StringBuilder missing = new StringBuilder();
        for (var e : need.entrySet()) {
            int inv = have.getOrDefault(e.getKey(), 0), ch = state.chests.total(e.getKey());
            if (shown++ < 30) sb.append("  ").append(e.getKey()).append(' ').append(e.getValue()).append(" / ").append(inv).append(" / ").append(ch).append('\n');
            int lack = e.getValue() - inv - ch;
            if (lack > 0) missing.append(e.getKey()).append(" x").append(lack).append(", ");
        }
        if (need.size() > 30) sb.append("  ... ").append(need.size() - 30).append(" more item types\n");
        sb.append(missing.length() == 0 ? "All materials are available (inventory + chests)."
                : "Still to gather/craft: " + missing);
        return sb.toString();
    }

    public String snapshot() {
        var p = G.p();
        StringBuilder sb = new StringBuilder();
        BlockPos pos = G.pos();
        sb.append("Position ").append(pos.toShortString()).append(" in ").append(G.dim())
                .append("; health ").append((int) p.getHealth()).append("/").append((int) p.getMaxHealth())
                .append(", food ").append(p.getHungerManager().getFoodLevel()).append("/20")
                .append(", day time ").append(G.w().getTimeOfDay() % 24000).append('\n');
        Map<String, Integer> inv = G.inventory();
        sb.append("Inventory (").append(G.emptySlots()).append(" free slots): ");
        if (inv.isEmpty()) sb.append("empty");
        else {
            int n = 0;
            for (var e : inv.entrySet()) { if (n++ >= 30) { sb.append("..."); break; } sb.append(e.getKey()).append('x').append(e.getValue()).append(' '); }
        }
        sb.append("\nBest tools: pickaxe=").append(MiningRules.bestTier(inv, "pickaxe")).append(", axe=").append(MiningRules.bestTier(inv, "axe"))
                .append(", sword=").append(MiningRules.bestTier(inv, "sword")).append(", shovel=").append(MiningRules.bestTier(inv, "shovel")).append('\n');
        List<LivingEntity> hs = Mobs.hostiles(cfg.hostileScanRadius);
        sb.append("Hostiles within ").append((int) cfg.hostileScanRadius).append(": ");
        if (hs.isEmpty()) sb.append("none");
        else for (int i = 0; i < Math.min(5, hs.size()); i++) sb.append(hs.get(i).getName().getString()).append('(').append((int) p.distanceTo(hs.get(i))).append("m) ");
        List<String> players = new ArrayList<>();
        for (var pl : G.w().getPlayers()) if (pl != p) players.add(pl.getName().getString() + "(" + (int) p.distanceTo(pl) + "m)");
        sb.append("\nOther players nearby: ").append(players.isEmpty() ? "none" : String.join(", ", players));
        sb.append("\nChests: ").append(state.chests.summary(15));
        sb.append("\nStorage zone: ").append(state.storageZone == null ? "not set" : Arrays.toString(state.storageZone));
        sb.append("\nWaypoints: ").append(state.waypoints.isEmpty() ? "none" : formatWaypoints());
        sb.append("\nBaritone: ").append(Baritone.available() ? "available" : "NOT INSTALLED").append("; Litematica: ").append(Litematica.loaded() ? "loaded" : "not loaded");
        sb.append("\nRunning: ").append(current == null ? "nothing" : current.describe());
        if (background != null) sb.append("; background: ").append(background.describe());
        return sb.toString();
    }

    private String formatWaypoints() {
        StringBuilder sb = new StringBuilder();
        state.waypoints.forEach((k, v) -> sb.append(k).append('=').append(v[0]).append(',').append(v[1]).append(',').append(v[2]).append(' '));
        return sb.toString().trim();
    }
}
