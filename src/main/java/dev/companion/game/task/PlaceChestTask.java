package dev.companion.game.task;

import dev.companion.core.storage.ChestIndex;
import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.TreeMap;

/** Craft (if needed) and place a chest inside the designated storage zone. */
public final class PlaceChestTask extends Task {
    private int phase;      // 0 ensure chest item, 1 pick spot + walk, 2 place, 3 verify
    private BlockPos spot;
    private int attempts, wait;
    private boolean crafted;

    @Override public String describe() { return "place a chest in the storage zone"; }

    @Override protected void onStart(Agent a) { phase = Math.min(phase, 1); wait = 0; }

    private static boolean chestNear(BlockPos p) {
        for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west(), p.north().east(), p.north().west(), p.south().east(), p.south().west()}) {
            String id = G.blockId(n);
            if (id.equals("chest") || id.equals("trapped_chest")) return true;
        }
        return false;
    }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        Task f = takeFinished();
        if (f != null && f.status() == Status.FAILED) { fail("cannot get a chest: " + f.message()); return; }

        switch (phase) {
            case 0 -> {
                if (G.count("chest") > 0) { phase = 1; return; }
                if (crafted) { fail("crafting a chest did not produce one"); return; }
                crafted = true;
                startChild(a, new CraftTask("chest", 1));
            }
            case 1 -> {
                if (a.state.storageZone == null) {
                    BlockPos p = G.pos();
                    a.state.storageZone = new int[]{p.getX(), p.getY(), p.getZ()};
                    a.saveState();
                    G.say("Storage zone set at " + p.toShortString());
                }
                BlockPos zone = new BlockPos(a.state.storageZone[0], a.state.storageZone[1], a.state.storageZone[2]);
                if (G.dist(G.pos(), zone) > a.cfg.storageRadius + 2) {
                    if (!dev.companion.game.bridge.Baritone.busy() && ++wait % 20 == 1) dev.companion.game.bridge.Baritone.gotoNear(zone, 2);
                    if (wait > 1200) fail("cannot reach the storage zone");
                    return;
                }
                spot = Placer.freeSpotNear(zone, a.cfg.storageRadius, p -> !chestNear(p));
                if (spot == null) { fail("no free spot for a chest in the storage zone (radius " + a.cfg.storageRadius + ")"); return; }
                dev.companion.game.bridge.Baritone.cancel();
                phase = 2;
            }
            case 2 -> {
                if (G.distToCenter(spot) > a.cfg.reach - 0.4) {
                    if (!dev.companion.game.bridge.Baritone.busy()) dev.companion.game.bridge.Baritone.gotoNear(spot, 2);
                    if (++wait > 600) fail("cannot get close enough to place the chest");
                    return;
                }
                if (++attempts > 6) { fail("placing the chest failed repeatedly"); return; }
                if (Placer.place(spot, "chest", a.cfg.reach)) { wait = 0; phase = 3; }
                else phase = 1; // spot changed, re-pick
            }
            case 3 -> {
                if (++wait < 4) return;
                if (G.blockId(spot).equals("chest")) {
                    Map<String, Integer> empty = new TreeMap<>();
                    ChestIndex.Entry e = a.state.chests.update(G.dim(), spot.getX(), spot.getY(), spot.getZ(), 27, 0, empty);
                    a.saveState();
                    done("placed chest at " + spot.toShortString());
                } else {
                    phase = 2;
                    wait = 0;
                }
            }
            default -> { }
        }
    }
}
