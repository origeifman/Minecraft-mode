package dev.companion.game.task;

import dev.companion.game.G;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.function.Predicate;

/** Block placement and block searching helpers. */
public final class Placer {
    private Placer() {}

    private static final Direction[] ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

    public static boolean replaceable(BlockPos pos) {
        BlockState s = G.w().getBlockState(pos);
        return s.isAir() || (s.getCollisionShape(G.w(), pos).isEmpty() && !s.isOf(Blocks.LAVA));
    }

    /** Blocks that would open a GUI instead of accepting a placement click. */
    private static boolean interactive(BlockPos pos) {
        String id = Registries.BLOCK.getId(G.w().getBlockState(pos).getBlock()).getPath();
        return id.contains("chest") || id.contains("barrel") || id.contains("furnace") || id.contains("smoker") || id.equals("crafting_table")
                || id.contains("door") || id.contains("gate") || id.contains("button") || id.equals("lever") || id.contains("anvil")
                || id.contains("bed") || id.contains("shulker") || id.equals("enchanting_table") || id.contains("sign") || id.equals("note_block")
                || id.equals("hopper") || id.equals("dispenser") || id.equals("dropper") || id.equals("cartography_table") || id.equals("loom")
                || id.equals("grindstone") || id.equals("stonecutter") || id.equals("smithing_table") || id.equals("fletching_table");
    }

    /** True if the player's body overlaps the block cell (placement would be refused). */
    public static boolean playerInside(BlockPos pos) {
        Box cell = new Box(pos);
        return G.p().getBoundingBox().intersects(cell);
    }

    /** Send one placement click against a neighbouring solid block. @return false if impossible right now */
    public static boolean place(BlockPos target, String itemPath, double reach) {
        if (!replaceable(target) || playerInside(target)) return false;
        if (G.findSlot(itemPath) < 0) return false;
        for (Direction d : ORDER) {
            BlockPos n = target.offset(d);
            if (!G.isSolid(n) || interactive(n)) continue;
            if (G.distToCenter(n) > reach) continue;
            if (!G.select(itemPath)) return false;
            G.useBlock(n, d.getOpposite());
            return true;
        }
        return false;
    }

    /** A free standing-height cell near the player where a station block can go. */
    public static BlockPos freeSpotNear(BlockPos around, int radius, Predicate<BlockPos> extraOk) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = around.add(dx, dy, dz);
                    if (!G.isAir(p) || !G.isSolid(p.down()) || !G.isAir(p.up())) continue;
                    if (playerInside(p) || p.equals(G.pos()) || p.equals(G.pos().up())) continue;
                    if (extraOk != null && !extraOk.test(p)) continue;
                    double d = p.getSquaredDistance(around) + Math.abs(dy) * 2;
                    if (d < bestD) { bestD = d; best = p; }
                }
            }
        }
        return best;
    }

    /** Nearest block whose id is in the set, within a cube radius of the player. */
    public static BlockPos findBlock(java.util.Set<String> ids, int radius) {
        BlockPos c = G.pos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = c.add(dx, dy, dz);
                    if (ids.contains(G.blockId(p))) {
                        double d = p.getSquaredDistance(c);
                        if (d < bestD) { bestD = d; best = p; }
                    }
                }
            }
        }
        return best;
    }
}
