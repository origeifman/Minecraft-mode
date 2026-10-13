package dev.companion.game.bridge;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Baritone is driven purely through reflection so the mod compiles and loads without it. Everything
 * that needs Baritone checks available() first.
 */
public final class Baritone {
    private Baritone() {}

    private static Object instance;
    private static boolean configured;

    private static Class<?> cls(String n) throws ClassNotFoundException { return Class.forName(n); }

    public static Object get() {
        if (instance != null) return instance;
        try {
            Object provider = cls("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            instance = cls("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
        } catch (Throwable t) {
            instance = null;
        }
        return instance;
    }

    public static boolean available() { return get() != null; }

    private static Object call(Object target, String iface, String method, Object... args) throws Exception {
        for (Method m : cls(iface).getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(iface + "#" + method);
    }

    /** Safe defaults, applied once on first use. */
    private static void configure() {
        if (configured) return;
        configured = true;
        exec("set avoidance true");
        exec("set allowBreak true");
        exec("set allowPlace true");
    }

    /** Run a Baritone command (without prefix). Falls back to nothing if unavailable. */
    public static boolean exec(String command) {
        Object b = get();
        if (b == null) return false;
        try {
            Object cm = call(b, "baritone.api.IBaritone", "getCommandManager");
            Object r = call(cm, "baritone.api.command.manager.ICommandManager", "execute", command);
            return !(r instanceof Boolean bo) || bo;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void cancel() {
        Object b = get();
        if (b == null) return;
        try {
            Object pb = call(b, "baritone.api.IBaritone", "getPathingBehavior");
            call(pb, "baritone.api.behavior.IPathingBehavior", "cancelEverything");
        } catch (Throwable t) {
            exec("cancel");
        }
    }

    /** True while any Baritone process (goto, mine, follow, build, explore) is doing something. */
    public static boolean busy() {
        Object b = get();
        if (b == null) return false;
        try {
            for (String getter : List.of("getCustomGoalProcess", "getMineProcess", "getFollowProcess", "getBuilderProcess", "getExploreProcess", "getGetToBlockProcess")) {
                Object proc = call(b, "baritone.api.IBaritone", getter);
                if (proc != null && (Boolean) call(proc, "baritone.api.process.IBaritoneProcess", "isActive")) return true;
            }
            Object pb = call(b, "baritone.api.IBaritone", "getPathingBehavior");
            return (Boolean) call(pb, "baritone.api.behavior.IPathingBehavior", "isPathing");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Walk to within `range` blocks of pos. */
    public static boolean gotoNear(BlockPos pos, int range) {
        Object b = get();
        if (b == null) return false;
        configure();
        try {
            Class<?> goalCls = cls("baritone.api.pathing.goals.GoalNear");
            Constructor<?> c = goalCls.getConstructor(BlockPos.class, int.class);
            Object goal = c.newInstance(pos, range);
            Object proc = call(b, "baritone.api.IBaritone", "getCustomGoalProcess");
            Method m = cls("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath", cls("baritone.api.pathing.goals.Goal"));
            m.invoke(proc, goal);
            return true;
        } catch (Throwable t) {
            return exec("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
    }

    public static boolean mine(List<String> blockIds) {
        if (!available()) return false;
        configure();
        return exec("mine " + String.join(" ", blockIds));
    }

    public static boolean follow(String playerName) {
        if (!available()) return false;
        configure();
        return exec("follow player " + playerName);
    }
}
