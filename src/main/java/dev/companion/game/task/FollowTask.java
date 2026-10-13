package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.bridge.Baritone;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/** Follow another player until stopped. Runs in the background. */
public final class FollowTask extends Task {
    private String name;

    public FollowTask(String name) { this.name = name; }

    @Override public String describe() { return "follow " + name; }

    @Override public boolean background() { return true; }

    public static AbstractClientPlayerEntity find(String name) {
        AbstractClientPlayerEntity best = null;
        for (AbstractClientPlayerEntity p : G.w().getPlayers()) {
            if (p == G.p()) continue;
            if (name != null && !name.isBlank()) {
                if (p.getName().getString().equalsIgnoreCase(name)) return p;
            } else if (best == null || p.squaredDistanceTo(G.p()) < best.squaredDistanceTo(G.p())) best = p;
        }
        return name != null && !name.isBlank() ? null : best;
    }

    @Override
    protected void onStart(Agent a) {
        if (!Baritone.available()) { fail("Baritone is not installed"); return; }
        AbstractClientPlayerEntity t = find(name);
        if (t == null) { fail("no player" + (name == null || name.isBlank() ? "" : " named " + name) + " is loaded nearby to follow"); return; }
        name = t.getName().getString();
        Baritone.follow(name);
    }

    @Override
    protected void tick(Agent a) {
        if (ticks % 40 == 0 && !Baritone.busy() && find(name) != null) Baritone.follow(name);
    }
}
