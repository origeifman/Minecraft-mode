package dev.companion.game.task;

import dev.companion.game.Agent;
import dev.companion.game.G;
import dev.companion.game.Mobs;
import dev.companion.game.bridge.Baritone;
import net.minecraft.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kill every hostile mob in range, then stand guard until the area has been quiet for a while. */
public final class SecureTask extends Task {
    private final double radius;
    private int quiet, kills;
    private boolean toolTried;
    private final Set<Integer> seen = new HashSet<>();

    public SecureTask(int radius) { this.radius = Math.max(6, Math.min(48, radius)); }

    @Override public String describe() { return "secure the area (radius " + (int) radius + ")"; }

    @Override protected void onStart(Agent a) { a.guarding = true; }

    @Override protected void onEnd(Agent a) { a.guarding = false; }

    @Override
    protected void tick(Agent a) {
        if (childRunning(a)) return;
        takeFinished();
        if (!Mobs.hasWeapon() && !toolTried) {
            toolTried = true;
            startChild(a, new CraftTask(G.count("cobblestone") >= 2 ? "stone_sword" : "wooden_sword", 1));
            return;
        }
        List<LivingEntity> hs = Mobs.hostiles(radius);
        for (LivingEntity e : hs) seen.add(e.getId());
        if (hs.isEmpty()) {
            if (++quiet >= 100) {
                Baritone.cancel();
                done("area secured: saw " + seen.size() + " hostile(s), quiet for 5 seconds");
            }
            return;
        }
        quiet = 0;
        kills = seen.size() - hs.size();
        Mobs.equipWeapon();
        LivingEntity t = hs.get(0);
        double d = G.p().distanceTo(t);
        if (d > 2.8) {
            if (Mobs.isCreeper(t) && d < 6) { Baritone.cancel(); return; } // let it come; never rush a creeper
            if (ticks % 10 == 0 || !Baritone.busy()) Baritone.gotoNear(t.getBlockPos(), 1);
        } else {
            Baritone.cancel();
            Mobs.attack(t);
        }
        if (ticks > 6000) { Baritone.cancel(); done("guarded for 5 minutes, " + hs.size() + " hostile(s) still around"); }
    }
}
