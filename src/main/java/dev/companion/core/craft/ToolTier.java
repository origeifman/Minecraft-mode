package dev.companion.core.craft;

public enum ToolTier {
    NONE(0), WOODEN(1), STONE(2), IRON(3), DIAMOND(4), NETHERITE(5);

    public final int level;

    ToolTier(int level) { this.level = level; }

    public static ToolTier ofLevel(int l) {
        for (ToolTier t : values()) if (t.level == l) return t;
        return l > 5 ? NETHERITE : NONE;
    }

    /** "iron_pickaxe" -> IRON; golden tools count as wooden. */
    public static ToolTier ofItem(String id) {
        if (id.startsWith("wooden_") || id.startsWith("golden_")) return WOODEN;
        if (id.startsWith("stone_")) return STONE;
        if (id.startsWith("iron_")) return IRON;
        if (id.startsWith("diamond_")) return DIAMOND;
        if (id.startsWith("netherite_")) return NETHERITE;
        return NONE;
    }

    /** Cheapest craftable item prefix that reaches this tier. */
    public String prefix() {
        return switch (this) {
            case NONE, WOODEN -> "wooden";
            case STONE -> "stone";
            case IRON -> "iron";
            case DIAMOND -> "diamond";
            case NETHERITE -> "netherite";
        };
    }
}
