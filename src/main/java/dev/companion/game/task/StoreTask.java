package dev.companion.game.task;

import dev.companion.core.storage.ChestIndex;
import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Walk to the storage zone and put items away, placing new chests when the known ones are full. */
public final class StoreTask extends Task {
    private final String item;
    private final Set<String> keep = new HashSet<>();
    private int guard, totalMoved;
    private ChestIndex.Entry lastTried;

    /** @param item null = everything that is not essential or in keep */
    public StoreTask(String item, Set<String> keep) {
        this.item = item;
        if (keep != null) this.keep.addAll(keep);
    }

    @Override public String describe() { return "store " + (item == null ? "items" : item) + " in chests"; }

    @Override protected void onStart(Agent a) {}

    private boolean hasWanted() {
        var inv = G.p().getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (item != null ? G.id(s).equals(item) : !G.isEssential(s, keep)) return true;
        }
        return false;
    }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        Task f = takeFinished();
        if (f instanceof ChestTask ct) {
            totalMoved += ct.moved;
            if (ct.status() == Status.FAILED && lastTried != null) lastTried.usedSlots = lastTried.slots; // skip unreachable chest
        } else if (f instanceof PlaceChestTask && f.status() == Status.FAILED) {
            fail("cannot store: no chest available (" + f.message() + ")");
            return;
        }
        if (!hasWanted()) { done("stored " + totalMoved + " items"); return; }
        if (++guard > 8) { fail("could not store everything after several chests (stored " + totalMoved + ")"); return; }

        BlockPos anchor = a.state.storageZone != null
                ? new BlockPos(a.state.storageZone[0], a.state.storageZone[1], a.state.storageZone[2]) : G.pos();
        ChestIndex.Entry e = a.state.chests.withSpace(G.dim(), anchor.getX(), anchor.getY(), anchor.getZ());
        if (e == null) {
            lastTried = null;
            startChild(a, new PlaceChestTask());
        } else {
            lastTried = e;
            startChild(a, new ChestTask(new BlockPos(e.x, e.y, e.z), ChestTask.Mode.STORE, item, 0, keep));
        }
    }
}
