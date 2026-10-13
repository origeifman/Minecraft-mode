package dev.companion.core;

import dev.companion.core.schem.LitematicParser;
import dev.companion.core.schem.Schematic;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SchematicTest {

    // --- minimal NBT writer for building a fixture ---
    private static void name(DataOutputStream o, int type, String n) throws IOException { o.writeByte(type); o.writeUTF(n); }

    private static byte[] litematic(int[] size, int[] pos, List<String[]> palette, int[] indices) throws IOException {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long total = (long) Math.abs(size[0]) * Math.abs(size[1]) * Math.abs(size[2]);
        long[] data = new long[(int) ((total * bits + 63) / 64)];
        for (int i = 0; i < indices.length; i++) {
            long start = (long) i * bits;
            int w = (int) (start >>> 6), off = (int) (start & 63);
            data[w] |= ((long) indices[i]) << off;
            if (off + bits > 64) data[w + 1] |= ((long) indices[i]) >>> (64 - off);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(bos))) {
            name(o, 10, ""); // root
            name(o, 10, "Regions");
            name(o, 10, "main");
            name(o, 10, "Position"); name(o, 3, "x"); o.writeInt(pos[0]); name(o, 3, "y"); o.writeInt(pos[1]); name(o, 3, "z"); o.writeInt(pos[2]); o.writeByte(0);
            name(o, 10, "Size"); name(o, 3, "x"); o.writeInt(size[0]); name(o, 3, "y"); o.writeInt(size[1]); name(o, 3, "z"); o.writeInt(size[2]); o.writeByte(0);
            name(o, 9, "BlockStatePalette"); o.writeByte(10); o.writeInt(palette.size());
            for (String[] p : palette) {
                name(o, 8, "Name"); o.writeUTF(p[0]);
                if (p.length > 1) {
                    name(o, 10, "Properties");
                    for (int i = 1; i + 1 < p.length; i += 2) { name(o, 8, p[i]); o.writeUTF(p[i + 1]); }
                    o.writeByte(0);
                }
                o.writeByte(0);
            }
            name(o, 12, "BlockStates"); o.writeInt(data.length);
            for (long l : data) o.writeLong(l);
            o.writeByte(0); // end main
            o.writeByte(0); // end Regions
            o.writeByte(0); // end root
        }
        return bos.toByteArray();
    }

    @Test
    void parsesPaletteBitPackingAndMaterials() throws Exception {
        // 2 x 2 x 2 : layer 0 = stone, stone, air, oak_stairs ; layer 1 = double slab, slab(bottom), lower door, upper door
        List<String[]> pal = List.of(
                new String[]{"minecraft:air"}, new String[]{"minecraft:stone"},
                new String[]{"minecraft:oak_stairs", "facing", "north"},
                new String[]{"minecraft:stone_slab", "type", "double"},
                new String[]{"minecraft:stone_slab", "type", "bottom"},
                new String[]{"minecraft:oak_door", "half", "lower"},
                new String[]{"minecraft:oak_door", "half", "upper"});
        // index = y*4 + z*2 + x
        int[] idx = {1, 1, 0, 2, 3, 4, 5, 6};
        Schematic s = LitematicParser.parse(new ByteArrayInputStream(litematic(new int[]{2, 2, 2}, new int[]{0, 0, 0}, pal, idx)), "t");
        assertEquals(7, s.blocks().size()); // one air skipped
        assertEquals(2, s.layerCount());
        Map<String, Integer> m = s.materials();
        assertEquals(2, m.get("stone"));
        assertEquals(1, m.get("oak_stairs"));
        assertEquals(3, m.get("stone_slab"));  // double counts 2 + bottom 1
        assertEquals(1, m.get("oak_door"));     // upper half is free
        assertEquals(3, s.layer(0).size());
        assertEquals(4, s.layer(1).size());
        Schematic.Block stairs = s.layer(0).stream().filter(b -> b.state().id().equals("oak_stairs")).findFirst().orElseThrow();
        assertEquals(1, stairs.x()); assertEquals(1, stairs.z()); assertEquals(0, stairs.y());
    }

    @Test
    void wideRegionBitsSpanLongBoundaries() throws Exception {
        // 40 distinct blocks => 6 bits per entry, entries straddle 64-bit words
        List<String[]> pal = new ArrayList<>();
        pal.add(new String[]{"minecraft:air"});
        for (int i = 1; i < 40; i++) pal.add(new String[]{"minecraft:block_" + i});
        int[] idx = new int[40];
        for (int i = 0; i < 40; i++) idx[i] = i;
        Schematic s = LitematicParser.parse(new ByteArrayInputStream(litematic(new int[]{40, 1, 1}, new int[]{0, 0, 0}, pal, idx)), "t");
        assertEquals(39, s.blocks().size());
        for (Schematic.Block b : s.blocks()) assertEquals("block_" + b.x(), b.state().id());
    }

    @Test
    void negativeSizeExtendsBackwards() throws Exception {
        List<String[]> pal = List.of(new String[]{"minecraft:air"}, new String[]{"minecraft:dirt"});
        Schematic s = LitematicParser.parse(new ByteArrayInputStream(litematic(new int[]{-3, 1, 1}, new int[]{5, 0, 0}, pal, new int[]{1, 1, 1})), "t");
        assertEquals(3, s.sizeX());
        assertEquals(3, s.minX);
        assertEquals(5, s.maxX);
    }

    @Test
    void layerOrderIsSnake() throws Exception {
        List<String[]> pal = List.of(new String[]{"minecraft:air"}, new String[]{"minecraft:dirt"});
        Schematic s = LitematicParser.parse(new ByteArrayInputStream(litematic(new int[]{2, 1, 2}, new int[]{0, 0, 0}, pal, new int[]{1, 1, 1, 1})), "t");
        List<Schematic.Block> l = s.layer(0);
        assertEquals(List.of(0, 1, 1, 0), l.stream().map(Schematic.Block::x).toList());
    }

    @Test
    void rotation() {
        assertArrayEquals(new int[]{1, 0}, Schematic.rotate(0, -1, "CLOCKWISE_90")); // north -> east
        assertArrayEquals(new int[]{-2, -3}, Schematic.rotate(2, 3, "CLOCKWISE_180"));
        assertArrayEquals(new int[]{3, -2}, Schematic.rotate(2, 3, "COUNTERCLOCKWISE_90"));
        assertArrayEquals(new int[]{2, 3}, Schematic.rotate(2, 3, "NONE"));
    }

    @Test
    void rejectsNonLitematic() {
        assertThrows(IOException.class, () -> LitematicParser.parse(new ByteArrayInputStream(new byte[]{1, 2, 3}), "x"));
    }
}
