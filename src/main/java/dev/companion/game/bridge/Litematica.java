package dev.companion.game.bridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds the schematic the player has loaded in Litematica (reflection; works without Litematica too). */
public final class Litematica {
    private Litematica() {}

    public record Active(Path file, BlockPos origin, String rotation, String mirror, boolean fromLitematica) {}

    public static boolean loaded() {
        try {
            Class.forName("fi.dy.masa.litematica.data.DataManager");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The selected Litematica placement, or empty. */
    public static Optional<Active> selectedPlacement() {
        try {
            Class<?> dm = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object mgr = dm.getMethod("getSchematicPlacementManager").invoke(null);
            Object sel = mgr.getClass().getMethod("getSelectedSchematicPlacement").invoke(mgr);
            if (sel == null) return Optional.empty();
            Object f = sel.getClass().getMethod("getSchematicFile").invoke(sel);
            Path file = f instanceof Path pp ? pp : f instanceof File ff ? ff.toPath() : null;
            if (file == null) return Optional.empty();
            BlockPos origin = (BlockPos) sel.getClass().getMethod("getOrigin").invoke(sel);
            String rot = String.valueOf(sel.getClass().getMethod("getRotation").invoke(sel));
            String mir = String.valueOf(sel.getClass().getMethod("getMirror").invoke(sel));
            return Optional.of(new Active(file, origin, rot, mir, true));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Fallback: newest .litematic in the schematics folder, placed at the given origin. */
    public static Optional<Active> newestFile(BlockPos origin) {
        try {
            Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("schematics");
            if (!Files.isDirectory(dir)) return Optional.empty();
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> p.toString().endsWith(".litematic"))
                        .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                        .map(p -> new Active(p, origin, "NONE", "NONE", false));
            }
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
