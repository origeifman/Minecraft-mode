package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Bring items to another player and drop them at their feet. */
public final class DeliverTask extends Task {
    private final String item, playerName;
    private final int count;
    private int phase, dropped;
    private boolean fetched;
    private AbstractClientPlayerEntity target;

    public DeliverTask(String item, int count, String playerName) {
        this.item = item;
        this.count = count;
        this.playerName = playerName;
    }

    @Override public String describe() { return "deliver " + count + " " + item + " to " + (playerName == null ? "nearest player" : playerName); }

    @Override protected void onStart(Agent a) { if (phase == 2) phase = 1; }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        takeFinished();
        switch (phase) {
            case 0 -> {
                int have = G.count(item);
                if (have < count && !fetched && a.state.chests.total(item) > 0) {
                    fetched = true;
                    startChild(a, new FetchTask(item, count - have));
                    return;
                }
                if (have == 0) { fail("I have no " + item + " to deliver and none in known chests"); return; }
                phase = 1;
            }
            case 1 -> {
                target = FollowTask.find(playerName);
                if (target == null) { fail("player " + (playerName == null ? "" : playerName + " ") + "is not loaded nearby; tell me coordinates to go to"); return; }
                if (G.p().squaredDistanceTo(target) > 3.5 * 3.5) {
                    if (ticks % 20 == 1 || !Baritone.busy()) Baritone.gotoNear(target.getBlockPos(), 2);
                    if (ticks > 2400) fail("could not reach the player");
                    return;
                }
                Baritone.cancel();
                phase = 2;
            }
            case 2 -> {
                target = FollowTask.find(playerName);
                if (target == null) { fail("player left"); return; }
                if (G.p().squaredDistanceTo(target) > 5 * 5) { phase = 1; return; }
                int left = Math.min(count, Math.max(count, 0)) - dropped;
                int idx = G.findSlot(item);
                if (left <= 0 || idx < 0) { done("delivered " + dropped + " " + item); return; }
                G.lookAt(target.getPos().add(0, 0.5, 0));
                ItemStack s = G.p().getInventory().getStack(idx);
                boolean whole = s.getCount() <= left;
                G.mc().interactionManager.clickSlot(G.p().playerScreenHandler.syncId, G.screenSlot(idx), whole ? 1 : 0, SlotActionType.THROW, G.p());
                dropped += whole ? s.getCount() : 1;
            }
            default -> { }
        }
    }
}
