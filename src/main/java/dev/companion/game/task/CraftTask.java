package dev.companion.game.task;

import dev.companion.core.craft.CraftPlanner;
import dev.companion.core.craft.Recipes;
import dev.companion.core.craft.Step;
import dev.companion.game.Agent;
import dev.companion.game.G;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ensure the inventory holds at least `count` of an item. Plans the whole chain (logs -> planks ->
 * sticks -> table -> tools, cobblestone -> furnace, ore -> smelt ...) and runs it step by step.
 */
public final class CraftTask extends Task {
    private final String item;
    private final int count;
    private List<Step> steps;
    private int idx;
    private final Set<String> myKeep = new HashSet<>();

    public CraftTask(String item, int count) {
        this.item = item;
        this.count = count;
    }

    @Override public String describe() {
        return "craft " + count + " " + item + (steps == null ? "" : " (step " + Math.min(idx + 1, steps.size()) + "/" + steps.size() + ")");
    }

    @Override
    protected void onStart(Agent a) {
        if (steps != null) return; // resume keeps its place
        try {
            steps = CraftPlanner.plan(item, count, G.inventory());
        } catch (IllegalStateException e) {
            fail("cannot plan " + item + ": " + e.getMessage());
            return;
        }
        for (Step s : steps) {
            myKeep.add(s.item());
            var c = Recipes.craftFor(s.item());
            if (c != null) myKeep.addAll(c.ingredients().keySet());
        }
        myKeep.add(item);
        a.keep.addAll(myKeep);
        if (steps.isEmpty()) done("already have " + G.count(item) + " " + item);
        else G.say("Plan for " + item + ": " + steps);
    }

    @Override protected void onEnd(Agent a) { a.keep.removeAll(myKeep); }

    @Override
    protected void tick(Agent a) {
        if (steps == null) return;
        if (childRunning(a)) return;
        Task f = takeFinished();
        if (f != null) {
            if (f.status() == Status.FAILED) {
                fail("step '" + steps.get(idx) + "' failed: " + f.message());
                return;
            }
            idx++;
        }
        if (idx >= steps.size()) {
            if (!item.startsWith("#") && G.count(item) < count) {
                fail("finished the plan but only have " + G.count(item) + "/" + count + " " + item);
            } else {
                done("have " + G.count(item) + " " + item);
            }
            return;
        }
        Step s = steps.get(idx);
        switch (s.kind()) {
            case GATHER -> startChild(a, new GatherTask(s.item(), s.count()));
            case SMELT -> startChild(a, new SmeltTask(s.item(), s.count()));
            case CRAFT -> startChild(a, s.table() ? new TableCraftTask(s.item(), s.count()) : new InventoryCraftTask(s.item(), s.count()));
        }
    }
}
