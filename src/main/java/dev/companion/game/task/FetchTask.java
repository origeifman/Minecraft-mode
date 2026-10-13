package dev.companion.game.task;

import dev.companion.core.storage.ChestIndex;
import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Take items out of the remembered chests. May overshoot by up to a stack. */
public final class FetchTask extends Task {
    private final String item;
    private final int count;
    private int start = -1;
    private final Set<String> tried = new HashSet<>();

    public FetchTask(String item, int count) { this.item = item; this.count = count; }

    @Override public String describe() { return "fetch " + count + " " + item + " from chests"; }

    @Override protected void onStart(Agent a) { if (start < 0) start = G.count(item); }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        takeFinished();
        int got = G.count(item) - start;
        if (got >= count) { done("fetched " + got + " " + item); return; }
        if (G.emptySlots() == 0) { fail("inventory full while fetching " + item + " (have " + got + "/" + count + ")"); return; }
        BlockPos p = G.pos();
        for (ChestIndex.Entry e : a.state.chests.withItem(G.dim(), item, p.getX(), p.getY(), p.getZ())) {
            String key = e.x + "," + e.y + "," + e.z;
            if (tried.add(key)) {
                startChild(a, new ChestTask(new BlockPos(e.x, e.y, e.z), ChestTask.Mode.FETCH, item, count - got, Collections.emptySet()));
                return;
            }
        }
        if (got > 0) done("fetched only " + got + "/" + count + " " + item + " (no more in known chests)");
        else fail("no known chest contains " + item + " (" + a.state.chests.summary(12) + ")");
    }
}
