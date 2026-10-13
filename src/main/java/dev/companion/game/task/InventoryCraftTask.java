package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.recipe.Recipe;

/** Craft something that fits the 2x2 inventory grid. */
public final class InventoryCraftTask extends Task {
    private final String item;
    private final int times;
    private CraftLoop loop;

    public InventoryCraftTask(String item, int times) { this.item = item; this.times = times; }

    @Override public String describe() { return "craft " + item + " x" + times + " (inventory grid)"; }

    @Override
    protected void onStart(Agent a) {
        Recipe<?> r = CraftLoop.resolve(item, false);
        if (r == null) { fail("no 2x2 recipe for " + item + " (or the game does not know it)"); return; }
        loop = new CraftLoop(r, item, times);
    }

    @Override
    protected void tick(Agent a) {
        if (loop == null) return;
        String res = loop.tick(G.p().playerScreenHandler);
        if (res == null) return;
        if (res.isEmpty()) done("crafted " + item);
        else fail(res);
    }
}
