package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/** Craft at a crafting table: finds one nearby, otherwise places (or first crafts) one. */
public final class TableCraftTask extends BlockScreenTask {
    private final String item;
    private final int times;
    private CraftLoop loop;
    private BlockPos placeSpot;
    private int placeWait, placeTries;
    private boolean triedCraftTable;

    public TableCraftTask(String item, int times) { this.item = item; this.times = times; }

    @Override public String describe() { return "craft " + item + " x" + times + " at a crafting table"; }

    @Override protected boolean validBlock(BlockPos pos) { return G.blockId(pos).equals("crafting_table"); }

    @Override protected boolean accepts(ScreenHandler h) { return h instanceof CraftingScreenHandler; }

    @Override
    protected boolean acquire(Agent a) {
        if (childRunning(a)) return false;
        Task f = takeFinished();
        if (f != null && f.status() == Status.FAILED) { fail("cannot obtain a crafting table: " + f.message()); return false; }
        if (target != null && validBlock(target)) return true;
        BlockPos near = Placer.findBlock(Set.of("crafting_table"), 10);
        if (near != null) { target = near; return true; }
        if (G.count("crafting_table") == 0) {
            if (triedCraftTable) { fail("no crafting table available"); return false; }
            triedCraftTable = true;
            startChild(a, new CraftTask("crafting_table", 1));
            return false;
        }
        if (placeSpot == null) {
            placeSpot = Placer.freeSpotNear(G.pos(), 2, null);
            if (placeSpot == null) { fail("no free space to put down a crafting table"); return false; }
            placeWait = 0;
        }
        if (placeWait == 0) {
            if (++placeTries > 4) { fail("placing the crafting table failed"); return false; }
            if (!Placer.place(placeSpot, "crafting_table", a.cfg.reach)) { placeSpot = null; return false; }
        }
        if (++placeWait >= 4) {
            placeWait = 0;
            if (validBlock(placeSpot)) { target = placeSpot; return true; }
            placeSpot = null;
        }
        return false;
    }

    @Override
    protected boolean work(Agent a, ScreenHandler h) {
        if (loop == null) {
            Recipe<?> r = CraftLoop.resolve(item, true);
            if (r == null) { fail("the game has no crafting recipe for " + item); return false; }
            loop = new CraftLoop(r, item, times);
        }
        String res = loop.tick(h);
        if (res == null) return false;
        if (!res.isEmpty()) { fail(res); return false; }
        return true;
    }

    @Override protected String doneMessage() { return "crafted " + item; }
}
