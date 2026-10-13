package dev.companion.core.craft;

import java.util.*;

/**
 * Expands "I want N of X" into an ordered list of gather / craft / smelt steps, given what is already
 * in the inventory. Dependencies always come before the steps that consume them.
 */
public final class CraftPlanner {
    private final Map<String, Integer> have;
    private final List<Step> steps = new ArrayList<>();
    private boolean furnaceKnown;

    private CraftPlanner(Map<String, Integer> inv) {
        this.have = new HashMap<>(inv);
    }

    public static List<Step> plan(String item, int count, Map<String, Integer> inventory) {
        CraftPlanner p = new CraftPlanner(inventory);
        p.need(item, count, 0);
        return p.merge();
    }

    private void take(String item, int n) { have.merge(item, -n, Integer::sum); }

    private int count(String item) { return have.getOrDefault(item, 0); }

    private int countMatching(String tag) {
        int n = 0;
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (matches(tag, e.getKey())) n += Math.max(0, e.getValue());
        }
        return n;
    }

    private static boolean matches(String tag, String id) {
        return switch (tag) {
            case "#planks" -> Recipes.isPlanks(id);
            case "#logs" -> Recipes.isLog(id);
            default -> tag.equals(id);
        };
    }

    private void takeMatching(String tag, int n) {
        // consume the biggest stacks first
        List<Map.Entry<String, Integer>> list = new ArrayList<>(have.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : list) {
            if (n <= 0) return;
            if (matches(tag, e.getKey()) && e.getValue() > 0) {
                int t = Math.min(n, e.getValue());
                have.put(e.getKey(), e.getValue() - t);
                n -= t;
            }
        }
    }

    private String mostCommonLog() {
        String best = "oak_log";
        int bestN = 0;
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (Recipes.isLog(e.getKey()) && e.getValue() > bestN) { best = e.getKey(); bestN = e.getValue(); }
        }
        return best;
    }

    private void need(String item, int qty, int depth) {
        if (qty <= 0) return;
        if (depth > 12) throw new IllegalStateException("recipe cycle while planning " + item);

        if (item.startsWith("#")) {
            int avail = countMatching(item);
            int use = Math.min(avail, qty);
            takeMatching(item, use);
            int rem = qty - use;
            if (rem == 0) return;
            if (item.equals("#logs")) { steps.add(Step.gather("#logs", rem)); return; }
            // planks: convert whichever log we hold (or will gather)
            int times = (rem + 3) / 4;
            need("#logs", times, depth + 1);
            takeMatching("#logs", times);
            steps.add(Step.craft("#planks", times, false));
            have.merge("planks-surplus", times * 4 - rem, Integer::sum);
            // surplus planks are usable as "oak_planks" stand-ins
            have.merge("oak_planks", times * 4 - rem, Integer::sum);
            return;
        }

        int avail = count(item);
        int use = Math.min(Math.max(avail, 0), qty);
        take(item, use);
        int rem = qty - use;
        if (rem == 0) return;

        Recipes.Craft c = Recipes.craftFor(item);
        Recipes.Smelt s = Recipes.smeltFor(item);
        if (c != null) {
            int times = (rem + c.yield() - 1) / c.yield();
            for (Map.Entry<String, Integer> ing : c.ingredients().entrySet()) {
                need(ing.getKey(), ing.getValue() * times, depth + 1);
            }
            if (c.table()) need("crafting_table", 1, depth + 1); // must hold one to place; reused afterwards
            steps.add(Step.craft(item, times, c.table()));
            int surplus = times * c.yield() - rem;
            if (surplus > 0) have.merge(item, surplus, Integer::sum);
            if (c.table()) have.merge("crafting_table", 1, Integer::sum); // placed table can be picked up again
        } else if (s != null) {
            need(s.input(), rem, depth + 1);
            if (!furnaceKnown && count("furnace") == 0) {
                need("furnace", 1, depth + 1);
                furnaceKnown = true;
            }
            // fuel: coal smelts 8 items, planks/logs 1.5
            if (count("coal") * 8 >= rem) take("coal", (rem + 7) / 8);
            else if (count("charcoal") * 8 >= rem) take("charcoal", (rem + 7) / 8);
            else {
                int coal = (rem + 7) / 8;
                steps.add(Step.gather("coal", coal));
            }
            steps.add(Step.smelt(item, rem));
        } else {
            steps.add(Step.gather(item, rem));
        }
    }

    /** Drop markers and merge duplicate consecutive gathers of the same item. */
    private List<Step> merge() {
        List<Step> out = new ArrayList<>();
        for (Step st : steps) {
            out.add(st);
        }
        // A step that must come first (gather) can be hoisted over unrelated crafts to batch trips: merge gathers.
        Map<String, Integer> gathers = new LinkedHashMap<>();
        List<Step> rest = new ArrayList<>();
        for (Step st : out) {
            if (st.kind() == Step.Kind.GATHER) gathers.merge(st.item(), st.count(), Integer::sum);
            else rest.add(st);
        }
        List<Step> result = new ArrayList<>();
        // Gathers first is always valid: gathering needs only tools, which the executor makes on demand.
        gathers.forEach((k, v) -> result.add(Step.gather(k, v)));
        result.addAll(rest);
        return result;
    }
}
