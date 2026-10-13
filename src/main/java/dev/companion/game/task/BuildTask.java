package dev.companion.game.task;

import dev.companion.core.craft.Sources;
import dev.companion.core.schem.LitematicParser;
import dev.companion.core.schem.Schematic;
import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import dev.companion.game.bridge.Litematica;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Builds a Litematica schematic layer by layer: supply (chests -> gather/craft), then place, then next layer.
 * Placement mode "native" clicks blocks itself; "printer" only walks and supplies while Litematica-Printer places.
 */
public final class BuildTask extends Task {
    private static final class Placement {
        final BlockPos pos; final Schematic.State state; final String item; final int qty;
        int deferrals; boolean clicked;
        Placement(BlockPos pos, Schematic.State state, String item, int qty) { this.pos = pos; this.state = state; this.item = item; this.qty = qty; }
    }

    private final BlockPos originOverride;
    private Schematic sch;
    private BlockPos origin;
    private String rotation = "NONE";
    private boolean printer;
    private int layer;
    private int phase;                 // 0 load, 1 layer setup, 2 supply, 3 place, 4 next layer
    private final List<Placement> pending = new ArrayList<>();
    private Placement cur;
    private int cooldown, curWait, placed, skipped, obstructed;
    private final Map<String, String> unobtainable = new TreeMap<>();
    private final Set<String> supplyTried = new HashSet<>();
    private boolean storedThisLayer;
    private final Set<String> myKeep = new HashSet<>();

    public BuildTask(BlockPos originOverride) { this.originOverride = originOverride; }

    @Override public String describe() {
        return sch == null ? "build (loading)" : "build " + sch.name + " layer " + (layer - sch.minY + 1) + "/" + sch.layerCount() + ", placed " + placed;
    }

    @Override
    protected void onStart(Agent a) {
        if (sch != null) { if (phase == 3) phase = 1; return; }
        if (!Baritone.available()) { fail("Baritone is required to move around while building"); return; }
        Optional<Litematica.Active> act = Litematica.selectedPlacement();
        if (act.isEmpty()) act = Litematica.newestFile(originOverride != null ? originOverride : G.pos());
        if (act.isEmpty()) { fail("no schematic: select a placement in Litematica or put a .litematic file into the schematics folder"); return; }
        try {
            sch = LitematicParser.parse(act.get().file());
        } catch (Exception e) {
            fail("could not read schematic " + act.get().file().getFileName() + ": " + e.getMessage());
            return;
        }
        if (sch.blocks().isEmpty()) { fail("the schematic is empty"); return; }
        origin = originOverride != null ? originOverride : act.get().origin();
        rotation = act.get().rotation();
        printer = a.cfg.placementMode.equalsIgnoreCase("printer");
        layer = sch.minY;
        myKeep.addAll(sch.materials().keySet());
        a.keep.addAll(myKeep);
        if (!a.cfg.buildAllowScaffold) { Baritone.exec("set allowPlace false"); Baritone.exec("set allowBreak false"); }
        String mir = act.get().mirror();
        G.say("Building '" + sch.name + "': " + sch.blocks().size() + " blocks, " + sch.layerCount() + " layers at " + origin.toShortString()
                + " (" + (printer ? "printer mode" : "native placement") + (mir != null && !mir.equals("NONE") && !mir.equals("null") ? ", mirror ignored" : "") + ")");
        if (printer && !a.cfg.printerEnableCommand.isBlank() && G.p().networkHandler != null) {
            String c = a.cfg.printerEnableCommand.trim();
            if (c.startsWith("/")) G.p().networkHandler.sendChatCommand(c.substring(1)); else G.p().networkHandler.sendChatMessage(c);
        }
        phase = 1;
    }

    @Override
    protected void onEnd(Agent a) {
        a.keep.removeAll(myKeep);
        if (!a.cfg.buildAllowScaffold) { Baritone.exec("set allowPlace true"); Baritone.exec("set allowBreak true"); }
    }

    private BlockPos worldPos(Schematic.Block b) {
        int[] r = Schematic.rotate(b.x(), b.z(), rotation);
        return origin.add(r[0], b.y(), r[1]);
    }

    private boolean done(Placement p) { return G.blockId(p.pos).equals(p.state.id()); }

    @Override
    protected void tick(Agent a) {
        if (sch == null) return;
        if (childRunning(a)) return;
        Task f = takeFinished();

        switch (phase) {
            case 1 -> setupLayer();
            case 2 -> supply(a, f);
            case 3 -> place(a);
            case 4 -> nextLayer();
            default -> { }
        }
    }

    private void setupLayer() {
        pending.clear();
        cur = null;
        for (Schematic.Block b : sch.layer(layer)) {
            String item = Schematic.itemFor(b.state());
            if (item == null) continue;
            Placement p = new Placement(origin.add(Schematic.rotate(b.x(), b.z(), rotation)[0], b.y(), Schematic.rotate(b.x(), b.z(), rotation)[1]),
                    b.state(), item, Schematic.quantityFor(b.state()));
            if (!G.w().isChunkLoaded(p.pos)) continue;
            if (done(p)) { placed++; continue; }
            if (unobtainable.containsKey(item)) { skipped++; continue; }
            pending.add(p);
        }
        storedThisLayer = false;
        supplyTried.clear();
        phase = pending.isEmpty() ? 4 : 2;
    }

    private Map<String, Integer> layerNeeds() {
        Map<String, Integer> need = new TreeMap<>();
        for (Placement p : pending) if (!done(p)) need.merge(p.item, p.qty, Integer::sum);
        return need;
    }

    private String lastFail = "";

    private void supply(Agent a, Task finished) {
        if (finished != null && finished.status() == Status.FAILED) lastFail = finished.message();
        Map<String, Integer> need = layerNeeds();
        Map<String, Integer> have = G.inventory();
        if (!storedThisLayer && G.emptySlots() < 3 && !need.isEmpty()) {
            storedThisLayer = true;
            startChild(a, new StoreTask(null, a.keepSet()));
            return;
        }
        for (var e : need.entrySet()) {
            String item = e.getKey();
            int missing = e.getValue() - have.getOrDefault(item, 0);
            if (missing <= 0 || unobtainable.containsKey(item)) continue;
            // 1) chests
            if (a.state.chests.total(item) > 0 && supplyTried.add("fetch:" + item)) {
                startChild(a, new FetchTask(item, missing));
                return;
            }
            // 2) gather / craft / smelt
            if (supplyTried.add("make:" + item)) {
                if (!Sources.obtainable(item)) { unobtainable.put(item, "cannot be mined or crafted automatically (needs " + missing + " more)"); continue; }
                startChild(a, new CraftTask(item, e.getValue()));
                return;
            }
            // already tried both ways and still short
            unobtainable.put(item, "still missing " + missing + " after fetching/crafting" + (lastFail.isEmpty() ? "" : " (" + lastFail + ")"));
        }
        // drop blocks whose material could not be obtained
        Iterator<Placement> it = pending.iterator();
        while (it.hasNext()) if (unobtainable.containsKey(it.next().item)) { it.remove(); skipped++; }
        phase = pending.isEmpty() ? 4 : 3;
    }

    private void defer() {
        if (cur == null) return;
        pending.remove(cur);
        if (++cur.deferrals > 3) { skipped++; } else pending.add(cur);
        cur = null;
        curWait = 0;
    }

    private void place(Agent a) {
        if (pending.isEmpty()) { phase = 4; return; }
        if (cur == null) { cur = pending.get(0); curWait = 0; cooldown = 0; }
        Placement p = cur;

        if (done(p) || (p.clicked && !Placer.replaceable(p.pos))) {
            placed++;
            pending.remove(p);
            cur = null;
            return;
        }
        if (!Placer.replaceable(p.pos)) { obstructed++; skipped++; pending.remove(p); cur = null; return; }
        if (G.count(p.item) == 0 && !printer) { phase = 2; supplyTried.remove("fetch:" + p.item); supplyTried.remove("make:" + p.item); return; }

        curWait++;
        if (printer) {
            if (G.distToCenter(p.pos) > 3.5 && (!Baritone.busy() || curWait % 20 == 1)) Baritone.gotoNear(p.pos, 3);
            if (curWait > 300) defer();
            return;
        }
        if (G.distToCenter(p.pos) > a.cfg.reach - 0.4) {
            if (!Baritone.busy() || curWait % 20 == 1) Baritone.gotoNear(p.pos, 3);
            if (curWait > 400) defer();
            return;
        }
        if (Baritone.busy()) Baritone.cancel();
        if (cooldown > 0) { cooldown--; return; }
        if (Placer.playerInside(p.pos)) { // standing in the way: walk elsewhere and come back to it later
            Baritone.gotoNear(p.pos.add(4, 0, 0), 1);
            defer();
            return;
        }
        if (Placer.place(p.pos, p.item, a.cfg.reach)) { p.clicked = true; cooldown = 3; }
        else defer(); // no supporting neighbour yet
        if (p.clicked && curWait > 40) defer();
    }

    private void nextLayer() {
        int idx = layer - sch.minY + 1;
        G.say("Layer " + idx + "/" + sch.layerCount() + " done (" + placed + " blocks placed so far)");
        layer++;
        if (layer > sch.maxY) {
            finishBuild();
            return;
        }
        phase = 1;
    }

    private void finishBuild() {
        int missingNow = 0;
        for (Schematic.Block b : sch.blocks()) {
            if (Schematic.itemFor(b.state()) == null) continue;
            BlockPos wp = worldPos(b);
            if (G.w().isChunkLoaded(wp) && !G.blockId(wp).equals(b.state().id())) missingNow++;
        }
        Baritone.cancel();
        if (missingNow == 0 && unobtainable.isEmpty()) {
            done("build complete: " + sch.name + " (" + placed + " blocks placed)");
        } else {
            StringBuilder sb = new StringBuilder("build of " + sch.name + " incomplete: " + missingNow + " block(s) differ from the schematic");
            if (!unobtainable.isEmpty()) sb.append("; unobtainable materials: ").append(unobtainable);
            if (obstructed > 0) sb.append("; ").append(obstructed).append(" position(s) obstructed by other blocks");
            fail(sb.toString());
        }
    }
}
