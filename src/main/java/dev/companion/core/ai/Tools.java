package dev.companion.core.ai;

import java.util.*;

/** The catalogue of atomic tools the model may call, with argument validation. */
public final class Tools {
    private Tools() {}

    public record Spec(String name, String args, String description, List<String> required) {}

    private static Spec t(String n, String a, String d, String... req) { return new Spec(n, a, d, List.of(req)); }

    public static final Map<String, Spec> ALL = new LinkedHashMap<>();

    static {
        for (Spec s : List.of(
                t("goto", "x,y,z", "Pathfind to coordinates (Baritone).", "x", "y", "z"),
                t("goto_waypoint", "name", "Pathfind to a saved waypoint.", "name"),
                t("set_waypoint", "name,[x,y,z]", "Save a waypoint (current position if no coords).", "name"),
                t("follow", "[player]", "Follow a player continuously (nearest player if omitted). Runs in background until stop."),
                t("stop", "", "Cancel everything and stand still."),
                t("gather", "item,count", "Collect raw items/blocks (logs, cobblestone, raw_iron, coal, diamond, sand...). Auto-crafts the needed tool and stores items when the inventory is full.", "item", "count"),
                t("craft", "item,count", "Craft an item. Missing ingredients, crafting table, furnace and tools are gathered/made automatically.", "item", "count"),
                t("smelt", "item,count", "Smelt (iron_ingot, gold_ingot, glass, stone...) using a furnace; gathers inputs and fuel as needed.", "item", "count"),
                t("ensure_tool", "tool", "Make sure a tool (e.g. iron_pickaxe) is in the inventory, crafting it if missing.", "tool"),
                t("schematic_info", "", "Report the active Litematica schematic, materials needed vs inventory vs chests."),
                t("build", "[x,y,z]", "Build the active Litematica schematic layer by layer at its placement origin (or x,y,z). Supplies materials from chests, mining and crafting."),
                t("deposit", "[item]", "Walk to the storage zone and store items in chests (everything non-essential if item omitted). Places a chest if needed."),
                t("fetch", "item,count", "Take items out of known chests.", "item", "count"),
                t("place_chest", "", "Craft (if needed) and place a chest in the storage zone."),
                t("set_storage_zone", "[x,y,z]", "Designate the storage zone centre (current position if omitted)."),
                t("deliver", "item,count,[player]", "Bring items to a player and drop them at their feet (nearest player if omitted).", "item", "count"),
                t("secure_area", "[radius]", "Hunt down and kill hostile mobs around, then stand guard until the area is clear."),
                t("eat", "", "Eat food now."),
                t("look_around", "", "Return current status: position, health, hunger, inventory, mobs, chests."),
                t("say", "message", "Print a message to the player in chat.", "message"),
                t("finish", "summary", "Call when the whole request is complete (or impossible). Required to end.", "summary")
        )) ALL.put(s.name(), s);
    }

    /** @return an error message, or null if the action is valid */
    public static String validate(AgentAction a) {
        Spec s = ALL.get(a.action());
        if (s == null) return "unknown tool '" + a.action() + "'. Valid tools: " + String.join(", ", ALL.keySet());
        for (String r : s.required()) {
            if (!a.has(r)) return "tool '" + a.action() + "' is missing required argument '" + r + "' (args: " + s.args() + ")";
        }
        return null;
    }

    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Spec s : ALL.values()) {
            sb.append("- ").append(s.name()).append('(').append(s.args()).append("): ").append(s.description()).append('\n');
        }
        return sb.toString();
    }
}
