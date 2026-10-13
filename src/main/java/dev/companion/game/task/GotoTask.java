package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import net.minecraft.util.math.BlockPos;

public final class GotoTask extends Task {
    private final BlockPos target;
    private final int range;
    private int idle, retries, stuck;
    private BlockPos lastPos;

    public GotoTask(BlockPos target, int range) { this.target = target; this.range = range; }

    @Override public String describe() { return "go to " + target.toShortString(); }

    @Override
    protected void onStart(Agent a) {
        idle = 0;
        if (!Baritone.available()) { fail("Baritone is not installed, so I cannot navigate"); return; }
        Baritone.gotoNear(target, range);
        lastPos = G.pos();
    }

    @Override
    protected void tick(Agent a) {
        double d = G.dist(G.pos(), target);
        if (d <= range + 1.5) { Baritone.cancel(); done("arrived at " + target.toShortString()); return; }
        if (!Baritone.busy()) {
            if (++idle > 20) {
                idle = 0;
                if (++retries > 3) { fail("no path to " + target.toShortString() + " (" + (int) d + " blocks away); blocked, unloaded or unsafe"); return; }
                Baritone.gotoNear(target, range);
            }
        } else idle = 0;
        if (ticks % 100 == 0) {
            if (lastPos != null && G.dist(lastPos, G.pos()) < 1.5 && Baritone.busy()) {
                if (++stuck >= 4) { Baritone.cancel(); fail("stuck while walking to " + target.toShortString()); return; }
            } else stuck = 0;
            lastPos = G.pos();
        }
    }
}
