package dev.companion.game.task;

import dev.companion.core.craft.Recipes;
import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/** Smelt items in a furnace (placing or first crafting one if necessary). */
public final class SmeltTask extends BlockScreenTask {
    private final String output;
    private final int need;
    private String input;
    private int startOut = -1;
    private int wphase, wait, reloads;
    private BlockPos placeSpot;
    private int placeWait, placeTries;
    private boolean triedCraftFurnace;

    public SmeltTask(String output, int count) {
        this.output = output;
        this.need = count;
    }

    @Override public String describe() { return "smelt " + need + " " + output; }

    @Override
    protected void onStart(Agent a) {
        super.onStart(a);
        wphase = 0;
        wait = 0;
        if (startOut < 0) startOut = G.count(output);
        Recipes.Smelt s = Recipes.smeltFor(output);
        if (s == null) { fail("don't know how to smelt " + output); return; }
        input = s.input();
        if (input.equals("#logs")) {
            input = "oak_log";
            for (var e : G.inventory().entrySet()) if (Recipes.isLog(e.getKey())) { input = e.getKey(); break; }
        }
    }

    @Override protected boolean validBlock(BlockPos pos) { return G.blockId(pos).equals("furnace"); }

    @Override protected boolean accepts(ScreenHandler h) { return h instanceof AbstractFurnaceScreenHandler; }

    @Override
    protected boolean acquire(Agent a) {
        if (childRunning(a)) return false;
        Task f = takeFinished();
        if (f != null && f.status() == Status.FAILED) { fail("cannot obtain a furnace: " + f.message()); return false; }
        if (target != null && validBlock(target)) return true;
        BlockPos near = Placer.findBlock(Set.of("furnace"), 10);
        if (near != null) { target = near; return true; }
        if (G.count("furnace") == 0) {
            if (triedCraftFurnace) { fail("no furnace available"); return false; }
            triedCraftFurnace = true;
            startChild(a, new CraftTask("furnace", 1));
            return false;
        }
        if (placeSpot == null) {
            placeSpot = Placer.freeSpotNear(G.pos(), 2, null);
            if (placeSpot == null) { fail("no free space to put down a furnace"); return false; }
            placeWait = 0;
        }
        if (placeWait == 0) {
            if (++placeTries > 4) { fail("placing the furnace failed"); return false; }
            if (!Placer.place(placeSpot, "furnace", a.cfg.reach)) { placeSpot = null; return false; }
        }
        if (++placeWait >= 4) {
            placeWait = 0;
            if (validBlock(placeSpot)) { target = placeSpot; return true; }
            placeSpot = null;
        }
        return false;
    }

    private static int fuelScore(String id) {
        if (id.equals("coal")) return 0;
        if (id.equals("charcoal")) return 1;
        if (id.endsWith("_planks")) return 2;
        if (id.endsWith("_log")) return 3;
        return -1;
    }

    private void quickMove(ScreenHandler h, int slot) {
        G.mc().interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.QUICK_MOVE, G.p());
    }

    /** Slot index (3..38) of the best fuel stack, or -1. */
    private int bestFuelSlot(ScreenHandler h) {
        int best = -1, bestScore = 99;
        for (int i = 3; i < h.slots.size(); i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            String id = G.id(s);
            int sc = fuelScore(id);
            if (sc >= 0 && !id.equals(input) && sc < bestScore) { best = i; bestScore = sc; }
        }
        return best;
    }

    @Override
    protected boolean work(Agent a, ScreenHandler h) {
        AbstractFurnaceScreenHandler f = (AbstractFurnaceScreenHandler) h;
        switch (wphase) {
            case 0 -> {
                if (G.count(output) - startOut >= need) { wphase = 2; return false; }
                if (f.getSlot(0).getStack().isEmpty()) {
                    int src = -1;
                    for (int i = 3; i < h.slots.size(); i++) {
                        ItemStack s = h.getSlot(i).getStack();
                        if (!s.isEmpty() && G.id(s).equals(input)) { src = i; break; }
                    }
                    if (src < 0 && f.getSlot(2).getStack().isEmpty()) { fail("I have no " + input + " to smelt"); return false; }
                    if (src >= 0) quickMove(h, src);
                }
                if (f.getSlot(1).getStack().isEmpty()) {
                    int fuel = bestFuelSlot(h);
                    if (fuel < 0 && !f.isBurning()) { fail("no fuel (coal, charcoal, planks or logs) to run the furnace"); return false; }
                    if (fuel >= 0) quickMove(h, fuel);
                }
                wphase = 1;
                wait = 0;
            }
            case 1 -> {
                wait++;
                if (!f.getSlot(2).getStack().isEmpty() && wait % 5 == 0) quickMove(h, 2);
                if (G.count(output) - startOut >= need) { wphase = 2; return false; }
                if (wait % 40 == 0 && f.getSlot(1).getStack().isEmpty()) {
                    int fuel = bestFuelSlot(h);
                    if (fuel >= 0) quickMove(h, fuel);
                }
                if (wait > need * 220 + 600) { fail("smelting timed out (" + (G.count(output) - startOut) + "/" + need + " " + output + ")"); return false; }
                if (wait > 80 && f.getSlot(0).getStack().isEmpty() && f.getSlot(2).getStack().isEmpty() && !f.isBurning()) {
                    if (++reloads > 3) { fail("furnace ran dry before " + need + " " + output + " were smelted"); return false; }
                    wphase = 0;
                }
            }
            case 2 -> {
                // give back unused input and fuel, then leave
                if (!f.getSlot(0).getStack().isEmpty()) { quickMove(h, 0); return false; }
                if (!f.getSlot(1).getStack().isEmpty()) { quickMove(h, 1); return false; }
                if (!f.getSlot(2).getStack().isEmpty()) { quickMove(h, 2); return false; }
                return true;
            }
            default -> { }
        }
        return false;
    }

    @Override protected String doneMessage() { return "smelted " + need + " " + output; }
}
