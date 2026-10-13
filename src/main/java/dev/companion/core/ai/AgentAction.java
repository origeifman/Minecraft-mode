package dev.companion.core.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** One decided step: a short rationale, a tool name and its arguments. */
public record AgentAction(String thought, String action, JsonObject args) {

    public static AgentAction of(String action, Object... kv) {
        JsonObject a = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = String.valueOf(kv[i]);
            if (v instanceof Number n) a.add(k, new JsonPrimitive(n));
            else if (v instanceof Boolean b) a.add(k, new JsonPrimitive(b));
            else a.add(k, new JsonPrimitive(String.valueOf(v)));
        }
        return new AgentAction("", action, a);
    }

    public String str(String key, String def) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return def;
        return args.get(key).getAsString();
    }

    public int integer(String key, int def) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return def;
        try {
            return (int) Math.round(args.get(key).getAsDouble());
        } catch (Exception e) {
            return def;
        }
    }

    public boolean has(String key) { return args != null && args.has(key) && !args.get(key).isJsonNull(); }

    @Override
    public String toString() { return action + (args == null ? "{}" : args.toString()); }
}
