package dev.companion.core.craft;

import java.util.Map;
import java.util.Optional;

/** Which tool (and tier) a block needs in order to drop its item. */
public final class MiningRules {
    private MiningRules() {}

    public record Requirement(String tool, ToolTier tier) {}

    /** Best tool tier in the inventory for the given tool type ("pickaxe", "axe", ...). */
    public static ToolTier bestTier(Map<String, Integer> inv, String toolType) {
        ToolTier best = ToolTier.NONE;
        for (Map.Entry<String, Integer> e : inv.entrySet()) {
            if (e.getValue() > 0 && e.getKey().endsWith("_" + toolType)) {
                ToolTier t = ToolTier.ofItem(e.getKey());
                if (t.level > best.level) best = t;
            }
        }
        return best;
    }

    public static Requirement requirement(String blockId) {
        String b = blockId.replace("minecraft:", "");
        if (b.equals("obsidian") || b.equals("crying_obsidian") || b.equals("ancient_debris") || b.equals("respawn_anchor")
                || b.equals("netherite_block")) return new Requirement("pickaxe", ToolTier.DIAMOND);
        if (b.endsWith("diamond_ore") || b.endsWith("gold_ore") || b.endsWith("redstone_ore") || b.endsWith("emerald_ore")
                || b.equals("raw_gold_block") || b.equals("gold_block") || b.equals("diamond_block")
                || b.equals("emerald_block") || b.equals("redstone_block")) return new Requirement("pickaxe", ToolTier.IRON);
        if (b.endsWith("iron_ore") || b.endsWith("copper_ore") || b.endsWith("lapis_ore") || b.equals("iron_block")
                || b.equals("lapis_block") || b.equals("copper_block") || b.equals("raw_iron_block")) return new Requirement("pickaxe", ToolTier.STONE);
        if (b.equals("stone") || b.equals("cobblestone") || b.endsWith("coal_ore") || b.equals("deepslate") || b.equals("cobbled_deepslate")
                || b.endsWith("nether_gold_ore") || b.endsWith("nether_quartz_ore") || b.equals("netherrack") || b.equals("andesite")
                || b.equals("diorite") || b.equals("granite") || b.equals("sandstone") || b.equals("furnace") || b.equals("blackstone")
                || b.equals("basalt") || b.equals("bricks") || b.endsWith("_stone_bricks") || b.equals("stone_bricks")
                || b.equals("terracotta") || b.equals("end_stone") || b.equals("mossy_cobblestone")
                || b.equals("coal_block") || b.equals("smooth_stone")) return new Requirement("pickaxe", ToolTier.WOODEN);
        return new Requirement("none", ToolTier.NONE);
    }

    /**
     * @return the tool item to craft so that the block can be mined, empty if the inventory already suffices
     */
    public static Optional<String> missingTool(String blockId, Map<String, Integer> inv) {
        Requirement r = requirement(blockId);
        if (r.tool().equals("none")) return Optional.empty();
        if (bestTier(inv, r.tool()).level >= r.tier().level) return Optional.empty();
        return Optional.of(r.tier().prefix() + "_" + r.tool());
    }
}
