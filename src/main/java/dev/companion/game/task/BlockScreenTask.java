package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Base for "walk to a block, open its GUI, do something, close it": chests, crafting tables, furnaces.
 * Subclasses may place the block first by overriding acquire().
 */
public abstract class BlockScreenTask extends Task {
    protected BlockPos target;
    protected ScreenHandler handler;
    private int phase;           // 0 acquire, 1 approach, 2 open, 3 wait open, 4 work, 5 close
    private int timer, openTries, navIdle, navRetries;
    private boolean navIssued;

    protected abstract boolean validBlock(BlockPos pos);

    protected abstract boolean accepts(ScreenHandler h);

    /** Called every tick while the screen is open. @return true when finished. */
    protected abstract boolean work(Agent a, ScreenHandler h);

    protected String doneMessage() { return "done"; }

    protected void beforeClose(Agent a, ScreenHandler h) {}

    /** Make sure `target` is set (find or place the block). @return true when ready, false to keep waiting; may fail(). */
    protected boolean acquire(Agent a) { return target != null; }

    @Override
    protected void onStart(Agent a) {
        phase = 0;
        timer = openTries = navIdle = navRetries = 0;
        navIssued = false;
        handler = null;
    }

    @Override
    public void pause(Agent a) {
        super.pause(a);
        closeScreen();
    }

    @Override
    protected void onEnd(Agent a) { closeScreen(); }

    private void closeScreen() {
        if (G.ready() && G.p().currentScreenHandler != G.p().playerScreenHandler) G.p().closeHandledScreen();
    }

    @Override
    protected void tick(Agent a) {
        switch (phase) {
            case 0 -> {
                if (acquire(a) && status() == Status.RUNNING) phase = 1;
            }
            case 1 -> {
                if (!validBlock(target)) { fail("no usable block at " + target.toShortString()); return; }
                if (G.distToCenter(target) <= 3.6) { Baritone.cancel(); phase = 2; return; }
                if (!navIssued) { navIssued = Baritone.gotoNear(target, 2); if (!navIssued) { fail("Baritone is required to walk to " + target.toShortString()); } return; }
                if (!Baritone.busy()) {
                    if (++navIdle > 20) {
                        navIdle = 0;
                        if (++navRetries > 3) { fail("cannot reach " + target.toShortString()); return; }
                        Baritone.gotoNear(target, 2);
                    }
                } else navIdle = 0;
            }
            case 2 -> {
                G.useBlock(target, Direction.UP);
                timer = 0;
                phase = 3;
            }
            case 3 -> {
                ScreenHandler h = G.p().currentScreenHandler;
                if (h != G.p().playerScreenHandler && accepts(h)) {
                    handler = h;
                    phase = 4;
                } else if (++timer > 30) {
                    if (++openTries > 3) fail("could not open the block at " + target.toShortString() + " (blocked?)");
                    else phase = 2;
                }
            }
            case 4 -> {
                if (G.p().currentScreenHandler != handler) { phase = 1; return; } // something closed it
                if (work(a, handler)) {
                    beforeClose(a, handler);
                    phase = 5;
                }
            }
            case 5 -> {
                G.p().closeHandledScreen();
                done(doneMessage());
            }
            default -> { }
        }
    }
}
