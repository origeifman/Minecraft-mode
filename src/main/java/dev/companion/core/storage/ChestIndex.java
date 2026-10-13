package dev.companion.core.storage;

import java.util.*;

/** Memory map of every chest the agent knows, and what is inside each. */
public final class ChestIndex {

    public static final class Entry {
        public String dim = "minecraft:overworld";
        public int x, y, z;
        public int slots = 27;
        public int usedSlots;
        public long updated;
        public Map<String, Integer> items = new TreeMap<>();

        public double distSq(int ox, int oy, int oz) {
            double dx = x - ox, dy = y - oy, dz = z - oz;
            return dx * dx + dy * dy + dz * dz;
        }

        public int freeSlots() { return Math.max(0, slots - usedSlots); }
    }

    public List<Entry> chests = new ArrayList<>();

    public Entry get(String dim, int x, int y, int z) {
        for (Entry e : chests) if (e.dim.equals(dim) && e.x == x && e.y == y && e.z == z) return e;
        return null;
    }

    /** Record (or refresh) the observed contents of a chest. */
    public Entry update(String dim, int x, int y, int z, int slots, int usedSlots, Map<String, Integer> contents) {
        Entry e = get(dim, x, y, z);
        if (e == null) {
            e = new Entry();
            e.dim = dim; e.x = x; e.y = y; e.z = z;
            chests.add(e);
        }
        e.slots = slots;
        e.usedSlots = usedSlots;
        e.updated = System.currentTimeMillis();
        e.items = new TreeMap<>(contents);
        return e;
    }

    /** Apply a known change without re-opening the chest. */
    public void adjust(String dim, int x, int y, int z, String item, int delta, int usedSlotsDelta) {
        Entry e = get(dim, x, y, z);
        if (e == null) return;
        int n = e.items.getOrDefault(item, 0) + delta;
        if (n <= 0) e.items.remove(item); else e.items.put(item, n);
        e.usedSlots = Math.max(0, Math.min(e.slots, e.usedSlots + usedSlotsDelta));
    }

    public void remove(String dim, int x, int y, int z) {
        chests.removeIf(e -> e.dim.equals(dim) && e.x == x && e.y == y && e.z == z);
    }

    public int total(String item) {
        int n = 0;
        for (Entry e : chests) n += e.items.getOrDefault(item, 0);
        return n;
    }

    public Map<String, Integer> totals() {
        Map<String, Integer> m = new TreeMap<>();
        for (Entry e : chests) e.items.forEach((k, v) -> m.merge(k, v, Integer::sum));
        return m;
    }

    /** Chests in this dimension holding the item, nearest first. */
    public List<Entry> withItem(String dim, String item, int ox, int oy, int oz) {
        List<Entry> r = new ArrayList<>();
        for (Entry e : chests) if (e.dim.equals(dim) && e.items.getOrDefault(item, 0) > 0) r.add(e);
        r.sort(Comparator.comparingDouble(e -> e.distSq(ox, oy, oz)));
        return r;
    }

    /** Nearest chest with at least one free slot, or null. */
    public Entry withSpace(String dim, int ox, int oy, int oz) {
        Entry best = null;
        for (Entry e : chests) {
            if (!e.dim.equals(dim) || e.freeSlots() == 0) continue;
            if (best == null || e.distSq(ox, oy, oz) < best.distSq(ox, oy, oz)) best = e;
        }
        return best;
    }

    public String summary(int maxItems) {
        if (chests.isEmpty()) return "no chests known";
        StringBuilder sb = new StringBuilder(chests.size() + " chest(s): ");
        List<Map.Entry<String, Integer>> all = new ArrayList<>(totals().entrySet());
        all.sort((a, b) -> b.getValue() - a.getValue());
        int i = 0;
        for (Map.Entry<String, Integer> en : all) {
            if (i++ >= maxItems) { sb.append("..."); break; }
            sb.append(en.getKey()).append('x').append(en.getValue()).append(' ');
        }
        return sb.toString().trim();
    }
}
