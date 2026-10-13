package dev.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** User-editable settings, stored as config/companion.json. */
public final class CompanionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String ollamaUrl = "http://localhost:11434";
    public String model = "llama3.1:8b";
    public double temperature = 0.2;
    public int llmTimeoutSeconds = 90;
    /** Use the LLM at all. When false (or Ollama is down) the rule-based planner is used. */
    public boolean useLlm = true;

    /**
     * The agent drives YOUR player (like Meteor Client). A chat line you type is taken as a command
     * (and is NOT sent to the server) if it starts with one of these, case-insensitive.
     * Note: "#" is Baritone's own prefix, so it is not a default trigger.
     */
    public List<String> triggers = new ArrayList<>(List.of("!", "@ai ", "ai,"));
    /** If true every chat line you type goes to the agent instead of the server. */
    public boolean listenToAll = false;
    public boolean chatReplies = true;

    public int maxReactSteps = 30;
    public int maxHistoryMessages = 24;

    // survival
    public int eatBelowFoodLevel = 14;
    public double fleeBelowHealth = 8.0;
    public double hostileScanRadius = 14.0;
    public double creeperSafeDistance = 7.0;

    // storage / building
    public int storageRadius = 5;
    /** "native" = the mod places blocks itself; "printer" = navigate/supply only, Litematica-Printer places. */
    public String placementMode = "native";
    /** Optional chat command sent before a printer-mode build, e.g. a Litematica-Printer toggle. */
    public String printerEnableCommand = "";
    public double reach = 4.4;
    /** Let Baritone place scaffolding blocks while building (can leave stray blocks). */
    public boolean buildAllowScaffold = false;

    public static CompanionConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                CompanionConfig c = GSON.fromJson(Files.readString(file), CompanionConfig.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        CompanionConfig c = new CompanionConfig();
        c.save(file);
        return c;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
