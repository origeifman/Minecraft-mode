package dev.companion.core.schem;

import java.util.*;

/** A parsed Litematica schematic, flattened to world-relative block positions. */
public final class Schematic {

    public record State(String name, Map<String, String> props) {
        public boolean isAir() { return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air"); }

        /** Registry path without namespace. */
        public String id() { return name.startsWith("minecraft:") ? name.substring(10) : name; }
    }

    /** x,y,z are relative to the placement origin. */
    public record Block(int x, int y, int z, State state) {}

    public final String name;
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    private final List<Block> blocks;

    public Schematic(String name, List<Block> blocks) {
        this.name = name;
        this.blocks = blocks;
        int a = Integer.MAX_VALUE, b = Integer.MAX_VALUE, c = Integer.MAX_VALUE, d = Integer.MIN_VALUE, e = Integer.MIN_VALUE, f = Integer.MIN_VALUE;
        for (Block k : blocks) {
            a = Math.min(a, k.x); b = Math.min(b, k.y); c = Math.min(c, k.z);
            d = Math.max(d, k.x); e = Math.max(e, k.y); f = Math.max(f, k.z);
        }
        if (blocks.isEmpty()) { a = b = c = d = e = f = 0; }
        minX = a; minY = b; minZ = c; maxX = d; maxY = e; maxZ = f;
    }

    /** Non-air blocks only. */
    public List<Block> blocks() { return blocks; }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    /** Number of layers (y levels) from the lowest to the highest block. */
    public int layerCount() { return blocks.isEmpty() ? 0 : maxY - minY + 1; }

    /** Blocks at relative height y, ordered in a snake pattern so the builder walks without backtracking. */
    public List<Block> layer(int y) {
        List<Block> l = new ArrayList<>();
        for (Block b : blocks) if (b.y == y) l.add(b);
        l.sort((a, b) -> {
            if (a.z != b.z) return Integer.compare(a.z, b.z);
            return (a.z % 2 == 0) ? Integer.compare(a.x, b.x) : Integer.compare(b.x, a.x);
        });
        return l;
    }

    /** Rotate a horizontal offset like Minecraft's BlockRotation name (NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90). */
    public static int[] rotate(int x, int z, String rotation) {
        return switch (rotation == null ? "NONE" : rotation) {
            case "CLOCKWISE_90" -> new int[]{-z, x};
            case "CLOCKWISE_180" -> new int[]{-x, -z};
            case "COUNTERCLOCKWISE_90" -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    /** Item needed to place this block, or null if it needs none (fluids, technical blocks, upper halves). */
    public static String itemFor(State s) {
        String id = s.id();
        if (s.isAir()) return null;
        switch (id) {
            case "water", "lava", "fire", "soul_fire", "moving_piston", "piston_head", "nether_portal", "end_portal",
                 "end_gateway", "bubble_column", "light" -> { return null; }
            case "wall_torch" -> { return "torch"; }
            case "soul_wall_torch" -> { return "soul_torch"; }
            case "redstone_wall_torch" -> { return "redstone_torch"; }
            case "redstone_wire" -> { return "redstone"; }
            case "tripwire" -> { return "string"; }
            case "wheat" -> { return "wheat_seeds"; }
            case "carrots" -> { return "carrot"; }
            case "potatoes" -> { return "potato"; }
            case "beetroots" -> { return "beetroot_seeds"; }
            case "cake" -> { return "cake"; }
            default -> { }
        }
        Map<String, String> p = s.props();
        if ("upper".equals(p.get("half"))) return null;               // doors, tall plants
        if ("head".equals(p.get("part"))) return null;                // beds
        if (id.endsWith("_wall_sign") || id.endsWith("_wall_hanging_sign") || id.endsWith("_wall_banner") || id.endsWith("_wall_head") || id.endsWith("_wall_skull")) {
            return id.replace("_wall_", "_");
        }
        if (id.equals("lava_cauldron") || id.equals("water_cauldron") || id.equals("powder_snow_cauldron")) return "cauldron";
        return id;
    }

    /** Number of items consumed placing this block (double slabs use two). */
    public static int quantityFor(State s) {
        return "double".equals(s.props().get("type")) ? 2 : 1;
    }

    /** Item id -> total count needed for the whole schematic. */
    public Map<String, Integer> materials() { return materials(blocks); }

    public static Map<String, Integer> materials(Collection<Block> bs) {
        Map<String, Integer> m = new TreeMap<>();
        for (Block b : bs) {
            String item = itemFor(b.state);
            if (item != null) m.merge(item, quantityFor(b.state), Integer::sum);
        }
        return m;
    }
}
