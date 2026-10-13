package dev.companion.core.craft;

/** One executable step of a resource plan. */
public record Step(Kind kind, String item, int count, boolean table, String note) {
    public enum Kind { GATHER, CRAFT, SMELT }

    public static Step gather(String item, int count) { return new Step(Kind.GATHER, item, count, false, ""); }

    /** @param times number of craft operations (each yields recipe-yield items) */
    public static Step craft(String item, int times, boolean table) { return new Step(Kind.CRAFT, item, times, table, ""); }

    public static Step smelt(String item, int count) { return new Step(Kind.SMELT, item, count, false, ""); }

    @Override
    public String toString() { return kind + " " + item + " x" + count + (table ? " [table]" : ""); }
}
