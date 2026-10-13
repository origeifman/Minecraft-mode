package dev.companion.core.ai;

import com.google.gson.*;

/** Extracts a tool call from whatever the model printed (fences, prose, alias keys). */
public final class ActionParser {
    private ActionParser() {}

    public static final class ParseException extends Exception {
        public ParseException(String m) { super(m); }
    }

    public static AgentAction parse(String raw) throws ParseException {
        if (raw == null || raw.isBlank()) throw new ParseException("empty reply");
        String json = firstJsonObject(raw);
        if (json == null) throw new ParseException("no JSON object found in reply");
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new ParseException("reply is not a JSON object");
            o = e.getAsJsonObject();
        } catch (JsonSyntaxException ex) {
            throw new ParseException("invalid JSON: " + ex.getMessage());
        }
        String thought = text(o, "thought", "reasoning", "thinking", "reason");
        String action = text(o, "action", "tool", "name", "function", "command");
        if (action == null || action.isBlank()) throw new ParseException("missing \"action\" field");
        JsonObject args = new JsonObject();
        for (String k : new String[]{"args", "arguments", "parameters", "input", "params"}) {
            if (o.has(k) && o.get(k).isJsonObject()) { args = o.getAsJsonObject(k); break; }
        }
        // models often put arguments at the top level instead of nested
        if (args.size() == 0) {
            for (var en : o.entrySet()) {
                String k = en.getKey();
                if (!k.equals("thought") && !k.equals("action") && !k.equals("tool") && !k.equals("reasoning")
                        && !k.equals("thinking") && !k.equals("reason") && !k.equals("name") && !k.equals("function")
                        && !k.equals("command") && !en.getValue().isJsonObject() && !en.getValue().isJsonArray()) {
                    args.add(k, en.getValue());
                }
            }
        }
        return new AgentAction(thought == null ? "" : thought, action.trim().toLowerCase().replace('-', '_').replace(' ', '_'), args);
    }

    private static String text(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) return o.get(k).getAsString();
        }
        return null;
    }

    /** First balanced {...} block, ignoring braces inside strings. */
    static String firstJsonObject(String s) {
        int start = s.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inStr = false, esc = false;
            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inStr = false;
                } else if (c == '"') inStr = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) return s.substring(start, i + 1);
            }
            start = s.indexOf('{', start + 1);
        }
        return null;
    }
}
