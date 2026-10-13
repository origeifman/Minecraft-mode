package dev.companion.game.task;

import dev.companion.core.CompanionState;
import dev.companion.core.storage.ChestIndex;
import dev.companion.game.Agent;
import dev.companion.game.G;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

/** Open one specific chest, either storing items into it or taking items out, then update the index. */
public final class ChestTask extends BlockScreenTask {
    public enum Mode { STORE, FETCH }

    private final Mode mode;
    private final String item;       // STORE: null = every non-essential item; FETCH: the wanted item
    private final Set<String> keep;
    private int need;                // FETCH: how many more to take
    public boolean chestFull;        // STORE result: the chest could not take everything
    public int moved;                // items moved (counted by stack size)
    private int rows;

    public ChestTask(BlockPos chest, Mode mode, String item, int count, Set<String> keep) {
        this.target = chest;
        this.mode = mode;
        this.item = item;
        this.need = count;
        this.keep = keep;
    }

    @Override public String describe() { return mode + " chest at " + target.toShortString(); }

    @Override
    protected boolean validBlock(BlockPos pos) {
        var b = G.w().getBlockState(pos).getBlock();
        boolean ok = b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL;
        if (!ok && agent != null) {
            agent.state.chests.remove(G.dim(), pos.getX(), pos.getY(), pos.getZ());
            agent.saveState();
        }
        return ok;
    }

    @Override
    protected boolean accepts(ScreenHandler h) { return h instanceof GenericContainerScreenHandler; }

    @Override
    protected boolean work(Agent a, ScreenHandler h) {
        GenericContainerScreenHandler g = (GenericContainerScreenHandler) h;
        rows = g.getRows();
        int chestSlots = rows * 9;
        int total = g.slots.size();
        int ops = 0;
        if (mode == Mode.STORE) {
            for (int i = chestSlots; i < total && ops < 3; i++) {
                ItemStack s = g.getSlot(i).getStack();
                if (s.isEmpty()) continue;
                if (item != null ? !G.id(s).equals(item) : G.isEssential(s, keep)) continue;
                int before = s.getCount();
                G.mc().interactionManager.clickSlot(g.syncId, i, 0, SlotActionType.QUICK_MOVE, G.p());
                ops++;
                int left = g.getSlot(i).getStack().getCount();
                moved += before - left;
                if (left > 0) { chestFull = true; return true; }
            }
            return ops == 0;
        }
        // FETCH
        if (need <= 0) return true;
        for (int i = 0; i < chestSlots && ops < 3 && need > 0; i++) {
            ItemStack s = g.getSlot(i).getStack();
            if (s.isEmpty() || !G.id(s).equals(item)) continue;
            int before = s.getCount();
            G.mc().interactionManager.clickSlot(g.syncId, i, 0, SlotActionType.QUICK_MOVE, G.p());
            ops++;
            int left = g.getSlot(i).getStack().getCount();
            int took = before - left;
            moved += took;
            need -= took;
            if (left > 0) return true; // inventory full
        }
        return ops == 0 || need <= 0;
    }

    @Override
    protected void beforeClose(Agent a, ScreenHandler h) {
        GenericContainerScreenHandler g = (GenericContainerScreenHandler) h;
        Map<String, Integer> contents = new TreeMap<>();
        int used = 0;
        for (int i = 0; i < g.getRows() * 9; i++) {
            ItemStack s = g.getSlot(i).getStack();
            if (!s.isEmpty()) { contents.merge(G.id(s), s.getCount(), Integer::sum); used++; }
        }
        CompanionState st = a.state;
        if (g.getRows() == 6) { // double chest: drop an adjacent single entry that is really the other half
            for (BlockPos n : new BlockPos[]{target.north(), target.south(), target.east(), target.west()}) {
                st.chests.remove(G.dim(), n.getX(), n.getY(), n.getZ());
            }
        }
        st.chests.update(G.dim(), target.getX(), target.getY(), target.getZ(), g.getRows() * 9, used, contents);
        if (mode == Mode.STORE && chestFull) {
            ChestIndex.Entry e = st.chests.get(G.dim(), target.getX(), target.getY(), target.getZ());
            if (e != null) e.usedSlots = e.slots;
        }
        a.saveState();
    }

    @Override
    protected String doneMessage() {
        return mode == Mode.STORE ? "stored " + moved + " items" + (chestFull ? " (chest full)" : "")
                : "took " + moved + " " + item;
    }
}
