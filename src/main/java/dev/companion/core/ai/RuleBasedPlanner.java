package dev.companion.core.ai;

import dev.companion.core.ItemNames;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline fallback: turns common English commands into tool calls when Ollama is unavailable. */
public final class RuleBasedPlanner {
    private RuleBasedPlanner() {}

    private static final Pattern COORDS = Pattern.compile("(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)");
    private static final Pattern QTY_ITEM = Pattern.compile("(?:(\\d+)\\s+)?([a-z_ ]+?)\\s*$");
    private static final Pattern VERB = Pattern.compile(
            "^(?:go\\s+(?:and\\s+)?)?(?:gather|collect|mine|fetch|get|grab|bring(?: me)?|give me|find|chop|harvest)\\s+(?:me\\s+)?(.+)$");

    public static List<AgentAction> plan(String text) {
        List<AgentAction> out = new ArrayList<>();
        String t = text.toLowerCase().trim().replaceAll("[.!?]+$", "");
        t = t.replaceFirst("^(please|hey|ok|okay)[, ]+", "");
        if (t.isEmpty()) return out;

        if (t.matches("^(stop|halt|cancel|abort|wait|stand still|never ?mind)\\b.*")) {
            out.add(AgentAction.of("stop"));
            out.add(AgentAction.of("finish", "summary", "Stopped."));
            return out;
        }
        if (t.startsWith("build") || t.contains("schematic")) {
            out.add(AgentAction.of("schematic_info"));
            Matcher m = COORDS.matcher(t);
            out.add(m.find() ? AgentAction.of("build", "x", m.group(1), "y", m.group(2), "z", m.group(3)) : AgentAction.of("build"));
            out.add(AgentAction.of("finish", "summary", "Build finished."));
            return out;
        }
        if (t.contains("secure") || t.contains("guard") || t.contains("defend") || t.contains("clear the area")) {
            out.add(AgentAction.of("secure_area"));
            out.add(AgentAction.of("finish", "summary", "Area secured."));
            return out;
        }
        if (t.startsWith("follow")) {
            String who = t.replaceFirst("^follow\\s*(?:me|the player)?\\s*(?:to\\s+\\w+)?", "").trim();
            out.add(who.isEmpty() || who.equals("me") ? AgentAction.of("follow") : AgentAction.of("follow", "player", who));
            out.add(AgentAction.of("finish", "summary", "Following."));
            return out;
        }
        if (t.startsWith("deposit") || t.startsWith("store") || t.startsWith("put away") || t.contains("empty inventory")) {
            out.add(AgentAction.of("deposit"));
            out.add(AgentAction.of("finish", "summary", "Items stored."));
            return out;
        }
        if (t.startsWith("eat")) {
            out.add(AgentAction.of("eat"));
            out.add(AgentAction.of("finish", "summary", "Ate."));
            return out;
        }
        Matcher wp = Pattern.compile("^(?:set|save|mark)\\s+(?:a\\s+)?waypoint\\s+(?:called\\s+|named\\s+)?(\\w+)").matcher(t);
        if (wp.find()) {
            out.add(AgentAction.of("set_waypoint", "name", wp.group(1)));
            out.add(AgentAction.of("finish", "summary", "Waypoint saved."));
            return out;
        }
        if (t.startsWith("set storage") || t.startsWith("storage zone") || t.startsWith("make storage")) {
            Matcher c = COORDS.matcher(t);
            out.add(c.find() ? AgentAction.of("set_storage_zone", "x", c.group(1), "y", c.group(2), "z", c.group(3)) : AgentAction.of("set_storage_zone"));
            out.add(AgentAction.of("finish", "summary", "Storage zone set."));
            return out;
        }
        if (t.matches("^(?:go|walk|run|travel|navigate|head|come)\\b.*")) {
            Matcher c = COORDS.matcher(t);
            if (c.find()) {
                out.add(AgentAction.of("goto", "x", c.group(1), "y", c.group(2), "z", c.group(3)));
                out.add(AgentAction.of("finish", "summary", "Arrived."));
                return out;
            }
            Matcher w = Pattern.compile("(?:to|at)\\s+(?:the\\s+)?(?:waypoint\\s+)?(\\w+)$").matcher(t);
            if (w.find() && !VERB.matcher(t).matches()) {
                out.add(AgentAction.of("goto_waypoint", "name", w.group(1)));
                out.add(AgentAction.of("finish", "summary", "Arrived."));
                return out;
            }
        }
        Matcher v = VERB.matcher(t);
        if (v.find()) {
            String rest = v.group(1).replaceAll("\\bfor me\\b", "").replaceAll("\\bto (?:me|the base|base)\\b", "").trim();
            boolean give = t.matches("^(bring|give me).*") || t.contains("for me") || t.contains("to me");
            Matcher q = QTY_ITEM.matcher(rest);
            if (q.find() && !q.group(2).isBlank()) {
                int count = q.group(1) == null ? 1 : Integer.parseInt(q.group(1));
                String item = ItemNames.normalize(q.group(2));
                if (!item.isEmpty()) {
                    out.add(item.matches(".*_(pickaxe|axe|shovel|sword|hoe)$") || item.equals("crafting_table") || item.equals("furnace") || item.equals("chest")
                            ? AgentAction.of("craft", "item", item, "count", count)
                            : AgentAction.of("gather", "item", item, "count", count));
                    if (give) out.add(AgentAction.of("deliver", "item", item, "count", count));
                    out.add(AgentAction.of("finish", "summary", "Done."));
                    return out;
                }
            }
        }
        Matcher cr = Pattern.compile("^(?:craft|make)\\s+(?:me\\s+)?(?:(\\d+)\\s+)?(.+)$").matcher(t);
        if (cr.find()) {
            out.add(AgentAction.of("craft", "item", ItemNames.normalize(cr.group(2)), "count", cr.group(1) == null ? 1 : Integer.parseInt(cr.group(1))));
            out.add(AgentAction.of("finish", "summary", "Crafted."));
            return out;
        }
        return out; // not understood
    }
}
