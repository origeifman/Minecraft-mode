package dev.companion.core;

import java.util.Map;
import java.util.Set;

/** Turns player wording ("64 iron ores", "oak logs") into registry paths ("iron_ore", "oak_log"). */
public final class ItemNames {
    private ItemNames() {}

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("wood", "oak_log"), Map.entry("log", "oak_log"), Map.entry("tree", "oak_log"),
            Map.entry("stone", "cobblestone"), Map.entry("iron", "iron_ingot"),
            Map.entry("gold", "gold_ingot"), Map.entry("copper", "copper_ingot"),
            Map.entry("plank", "oak_planks"), Map.entry("planks", "oak_planks"),
            Map.entry("lapis", "lapis_lazuli"), Map.entry("wool", "white_wool"),
            Map.entry("food", "bread"), Map.entry("table", "crafting_table"),
            Map.entry("workbench", "crafting_table"), Map.entry("pickaxe", "iron_pickaxe"),
            Map.entry("sword", "iron_sword"), Map.entry("axe", "iron_axe"),
            Map.entry("shovel", "iron_shovel"), Map.entry("seeds", "wheat_seeds"));

    private static final Set<String> KEEP_S = Set.of("planks", "stairs", "leaves", "bars", "glass", "grass",
            "moss", "cactus", "redstone", "lapis_lazuli", "coal", "sandstone", "gravel", "cobblestone",
            "obsidian", "netherrack", "clay", "sugar", "string", "bread");

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase().replace("minecraft:", "");
        s = s.replaceAll("[^a-z0-9_ #]", " ").replaceAll("\\b(some|of|the|a|an|more|many|lots|pieces|piece|blocks? of)\\b", " ");
        s = s.trim().replaceAll("\\s+", "_");
        if (s.isEmpty()) return s;
        if (ALIASES.containsKey(s)) return ALIASES.get(s);
        if (s.endsWith("planks") || s.endsWith("stairs") || s.endsWith("leaves") || KEEP_S.contains(s)) {
            return ALIASES.getOrDefault(s, s);
        }
        if (s.endsWith("ies")) s = s.substring(0, s.length() - 3) + "y";
        else if (s.endsWith("s") && !s.endsWith("ss") && s.length() > 3) s = s.substring(0, s.length() - 1);
        return ALIASES.getOrDefault(s, s);
    }
}
