package dev.companion.core.schem;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Tiny NBT reader. Compounds become Map<String,Object>, lists List<Object>, arrays long[]/int[]/byte[],
 * numbers Byte/Short/Integer/Long/Float/Double, strings String.
 */
public final class Nbt {
    private Nbt() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readGzipped(InputStream in) throws IOException {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)))) {
            int type = d.readUnsignedByte();
            if (type != 10) throw new IOException("root tag is not a compound");
            d.readUTF();
            return (Map<String, Object>) read(d, 10);
        }
    }

    private static Object read(DataInput d, int type) throws IOException {
        switch (type) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: { byte[] a = new byte[d.readInt()]; d.readFully(a); return a; }
            case 8: return d.readUTF();
            case 9: {
                int t = d.readUnsignedByte();
                int n = d.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) l.add(read(d, t));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int t = d.readUnsignedByte();
                    if (t == 0) return m;
                    String name = d.readUTF();
                    m.put(name, read(d, t));
                }
            }
            case 11: { int[] a = new int[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readInt(); return a; }
            case 12: { long[] a = new long[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readLong(); return a; }
            default: throw new IOException("unknown NBT tag " + type);
        }
    }
}
