package dev.companion.game.task;

import dev.companion.core.craft.MiningRules;
import dev.companion.core.craft.Sources;
import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mine/collect an item with Baritone. Makes the required tool on demand (also if it breaks mid-way),
 * and empties the inventory into chests when it fills up.
 */
public final class GatherTask extends Task {
    private final String item;
    private final int target;
    private final List<String> blocks = new ArrayList<>();
    private final List<String> counted;
    private int collected, lastCount = -1, idle, restarts, lastProgressTick;
    private boolean toolAttempted;

    public GatherTask(String item, int count) {
        this.item = item;
        this.target = count;
        for (String b : Sources.blocksFor(item)) {
            if (Registries.BLOCK.containsId(new Identifier("minecraft", b))) blocks.add(b);
        }
        this.counted = Sources.countedAs(item);
    }

    @Override public String describe() { return "gather " + target + " " + item + " (collected " + collected + ")"; }

    private int have() {
        var inv = G.inventory();
        int n = 0;
        for (String c : counted) n += inv.getOrDefault(c, 0);
        return n;
    }

    @Override
    protected void onStart(Agent a) {
        if (blocks.isEmpty()) { fail("'" + item + "' is not something I can mine; try craft or smelt instead"); return; }
        if (!Baritone.available()) { fail("Baritone is not installed, so I cannot mine"); return; }
        if (lastCount < 0) lastCount = have();
        a.keep.add(item);
        idle = 0;
        lastProgressTick = ticks;
        if (!ensureTool(a)) return;
        Baritone.mine(blocks);
    }

    /** @return false if a tool-crafting child was started */
    private boolean ensureTool(Agent a) {
        Optional<String> missing = Optional.empty();
        for (String b : blocks) {
            missing = MiningRules.missingTool(b, G.inventory());
            if (missing.isPresent()) break;
        }
        if (missing.isPresent()) {
            if (toolAttempted) { fail("I could not make the " + missing.get() + " needed to mine " + item); return false; }
            toolAttempted = true;
            Baritone.cancel();
            startChild(a, new CraftTask(missing.get(), 1));
            return false;
        }
        toolAttempted = false;
        return true;
    }

    @Override protected void onEnd(Agent a) { a.keep.remove(item); }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        Task f = takeFinished();
        if (f != null) {
            if (f.status() == Status.FAILED) {
                if (f instanceof CraftTask) { fail("tool needed for " + item + " could not be made: " + f.message()); return; }
                fail("inventory is full and storing items failed: " + f.message());
                return;
            }
            lastProgressTick = ticks;
            if (f instanceof CraftTask) toolAttempted = false;
            if (ensureTool(a)) Baritone.mine(blocks);
            return;
        }

        int now = have();
        if (now > lastCount) { collected += now - lastCount; lastProgressTick = ticks; }
        lastCount = now;
        if (collected >= target) { Baritone.cancel(); done("collected " + collected + " " + item); return; }

        if (G.emptySlots() == 0) {
            Baritone.cancel();
            startChild(a, new StoreTask(null, a.keepSet()));
            return;
        }
        if (ticks % 20 == 0) {
            // tool broke mid-way?
            for (String b : blocks) {
                if (MiningRules.missingTool(b, G.inventory()).isPresent()) { Baritone.cancel(); if (ensureTool(a)) Baritone.mine(blocks); return; }
            }
        }
        if (!Baritone.busy()) {
            if (++idle > 40) {
                idle = 0;
                if (++restarts > 2) {
                    fail("no " + item + " found or reachable (collected " + collected + "/" + target + "); explore somewhere else with goto and retry");
                    return;
                }
                Baritone.mine(blocks);
            }
        } else idle = 0;
        if (ticks - lastProgressTick > 4800) {
            Baritone.cancel();
            fail("no progress for 4 minutes while gathering " + item + " (collected " + collected + "/" + target + ")");
        }
    }
}
