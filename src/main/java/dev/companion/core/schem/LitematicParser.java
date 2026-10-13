package dev.companion.core.schem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Reads .litematic files (gzip NBT, bit-packed palette regions) without any Minecraft classes. */
public final class LitematicParser {
    private LitematicParser() {}

    public static Schematic parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            String n = file.getFileName().toString();
            return parse(in, n.endsWith(".litematic") ? n.substring(0, n.length() - 10) : n);
        }
    }

    @SuppressWarnings("unchecked")
    public static Schematic parse(InputStream in, String name) throws IOException {
        Map<String, Object> root = Nbt.readGzipped(in);
        Object regionsObj = root.get("Regions");
        if (!(regionsObj instanceof Map)) throw new IOException("not a litematic file (no Regions)");
        Map<String, Object> regions = (Map<String, Object>) regionsObj;

        List<Schematic.Block> blocks = new ArrayList<>();
        for (Object r : regions.values()) {
            Map<String, Object> reg = (Map<String, Object>) r;
            int[] pos = vec(reg, "Position"), size = vec(reg, "Size");
            int sx = Math.abs(size[0]), sy = Math.abs(size[1]), sz = Math.abs(size[2]);
            // coordinates stay relative to the placement origin (Litematica's region Position semantics)
            int ox = pos[0] + (size[0] < 0 ? size[0] + 1 : 0);
            int oy = pos[1] + (size[1] < 0 ? size[1] + 1 : 0);
            int oz = pos[2] + (size[2] < 0 ? size[2] + 1 : 0);

            List<Object> paletteTag = (List<Object>) reg.get("BlockStatePalette");
            List<Schematic.State> palette = new ArrayList<>();
            for (Object o : paletteTag) {
                Map<String, Object> c = (Map<String, Object>) o;
                Map<String, String> props = new LinkedHashMap<>();
                Object pr = c.get("Properties");
                if (pr instanceof Map) ((Map<String, Object>) pr).forEach((k, v) -> props.put(k, String.valueOf(v)));
                palette.add(new Schematic.State((String) c.get("Name"), props));
            }
            long[] data = (long[]) reg.get("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long mask = (1L << bits) - 1;
            long total = (long) sx * sy * sz;
            for (long i = 0; i < total; i++) {
                long startBit = i * bits;
                int word = (int) (startBit >>> 6);
                int off = (int) (startBit & 63);
                if (word >= data.length) break;
                long v = data[word] >>> off;
                if (off + bits > 64 && word + 1 < data.length) v |= data[word + 1] << (64 - off);
                int idx = (int) (v & mask);
                if (idx >= palette.size()) continue;
                Schematic.State st = palette.get(idx);
                if (st.isAir()) continue;
                int x = (int) (i % sx);
                int z = (int) ((i / sx) % sz);
                int y = (int) (i / ((long) sx * sz));
                blocks.add(new Schematic.Block(ox + x, oy + y, oz + z, st));
            }
        }
        return new Schematic(name, blocks);
    }

    @SuppressWarnings("unchecked")
    private static int[] vec(Map<String, Object> reg, String key) throws IOException {
        Object o = reg.get(key);
        if (!(o instanceof Map)) throw new IOException("region missing " + key);
        Map<String, Object> m = (Map<String, Object>) o;
        return new int[]{((Number) m.get("x")).intValue(), ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue()};
    }
}
