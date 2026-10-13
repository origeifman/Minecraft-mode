package dev.companion.core.craft;

import java.util.*;

/**
 * Planning-level recipe table (quantities only). The game itself performs the crafting through the
 * client recipe manager; this table just lets the planner work out what must be gathered first.
 * Ingredient "#planks" / "#logs" mean "any planks" / "any log".
 */
public final class Recipes {
    private Recipes() {}

    public record Craft(String output, int yield, Map<String, Integer> ingredients, boolean table) {}

    public record Smelt(String output, String input) {}

    private static final Map<String, Craft> CRAFTS = new HashMap<>();
    private static final Map<String, Smelt> SMELTS = new HashMap<>();

    public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");

    private static void craft(String out, int yield, boolean table, Object... ing) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < ing.length; i += 2) m.put((String) ing[i], (Integer) ing[i + 1]);
        CRAFTS.put(out, new Craft(out, yield, m, table));
    }

    static {
        craft("#planks", 4, false, "#logs", 1);
        craft("stick", 4, false, "#planks", 2);
        craft("crafting_table", 1, false, "#planks", 4);
        craft("chest", 1, true, "#planks", 8);
        craft("furnace", 1, true, "cobblestone", 8);
        craft("torch", 4, false, "coal", 1, "stick", 1);
        craft("bucket", 1, true, "iron_ingot", 3);
        craft("shield", 1, true, "#planks", 6, "iron_ingot", 1);
        craft("iron_block", 1, true, "iron_ingot", 9);
        craft("bread", 1, true, "wheat", 3);
        craft("white_bed", 1, true, "white_wool", 3, "#planks", 3);
        String[][] mats = {{"wooden", "#planks"}, {"stone", "cobblestone"}, {"iron", "iron_ingot"}, {"diamond", "diamond"}};
        for (String[] m : mats) {
            craft(m[0] + "_pickaxe", 1, true, m[1], 3, "stick", 2);
            craft(m[0] + "_axe", 1, true, m[1], 3, "stick", 2);
            craft(m[0] + "_shovel", 1, true, m[1], 1, "stick", 2);
            craft(m[0] + "_sword", 1, true, m[1], 2, "stick", 1);
            craft(m[0] + "_hoe", 1, true, m[1], 2, "stick", 2);
        }
        SMELTS.put("iron_ingot", new Smelt("iron_ingot", "raw_iron"));
        SMELTS.put("gold_ingot", new Smelt("gold_ingot", "raw_gold"));
        SMELTS.put("copper_ingot", new Smelt("copper_ingot", "raw_copper"));
        SMELTS.put("glass", new Smelt("glass", "sand"));
        SMELTS.put("stone", new Smelt("stone", "cobblestone"));
        SMELTS.put("charcoal", new Smelt("charcoal", "#logs"));
        SMELTS.put("cooked_beef", new Smelt("cooked_beef", "beef"));
        SMELTS.put("cooked_porkchop", new Smelt("cooked_porkchop", "porkchop"));
        SMELTS.put("cooked_chicken", new Smelt("cooked_chicken", "chicken"));
    }

    public static Craft craftFor(String item) {
        Craft c = CRAFTS.get(item);
        if (c != null) return c;
        // concrete planks: oak_planks <- oak_log
        if (item.endsWith("_planks")) {
            String wood = item.substring(0, item.length() - "_planks".length());
            return new Craft(item, 4, Map.of(wood + "_log", 1), false);
        }
        return null;
    }

    public static Smelt smeltFor(String item) { return SMELTS.get(item); }

    public static boolean isLog(String id) { return id.endsWith("_log") || id.endsWith("_stem") && !id.startsWith("stripped"); }

    public static boolean isPlanks(String id) { return id.endsWith("_planks"); }
}
