package dev.companion.core.craft;

import java.util.List;

/** Where raw items come from in the world. */
public final class Sources {
    private Sources() {}

    public static final List<String> ALL_LOGS = List.of("oak_log", "spruce_log", "birch_log", "jungle_log",
            "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log");

    /** Block ids to mine in order to obtain the item. Falls back to the item's own id (it is a block). */
    public static List<String> blocksFor(String item) {
        return switch (item) {
            case "#logs" -> ALL_LOGS;
            case "cobblestone" -> List.of("stone", "cobblestone", "andesite", "diorite", "granite");
            case "coal" -> List.of("coal_ore", "deepslate_coal_ore");
            case "raw_iron", "iron_ore" -> List.of("iron_ore", "deepslate_iron_ore");
            case "raw_gold" -> List.of("gold_ore", "deepslate_gold_ore");
            case "raw_copper" -> List.of("copper_ore", "deepslate_copper_ore");
            case "diamond" -> List.of("diamond_ore", "deepslate_diamond_ore");
            case "emerald" -> List.of("emerald_ore", "deepslate_emerald_ore");
            case "redstone" -> List.of("redstone_ore", "deepslate_redstone_ore");
            case "lapis_lazuli" -> List.of("lapis_ore", "deepslate_lapis_ore");
            case "dirt" -> List.of("dirt", "grass_block");
            case "flint" -> List.of("gravel");
            case "clay_ball" -> List.of("clay");
            case "quartz" -> List.of("nether_quartz_ore");
            case "wheat" -> List.of("wheat");
            case "sand" -> List.of("sand");
            case "gravel" -> List.of("gravel");
            default -> List.of(item);
        };
    }

    /** Item ids that count towards having "collected" the requested item. */
    public static List<String> countedAs(String item) {
        if (item.equals("#logs")) return ALL_LOGS;
        if (item.equals("cobblestone")) return List.of("cobblestone");
        if (item.equals("iron_ore")) return List.of("raw_iron");
        return List.of(item);
    }
}
