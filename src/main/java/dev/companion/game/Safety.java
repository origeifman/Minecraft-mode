package dev.companion.game;

import dev.companion.game.bridge.Baritone;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Reflex layer. It runs before any task each tick and, when it takes over, the running task is paused
 * and later resumed: eating, fleeing from low health / creepers, fighting back, escaping lava, surfacing.
 */
public final class Safety {
    private enum State { NONE, EATING, FLEEING, FIGHTING, LAVA }

    private State state = State.NONE;
    private int timer, calm;
    private BlockPos fleeTarget;
    private boolean drowning;
    public boolean forceEat;
    private String lastEvent = "";

    public boolean active() { return state != State.NONE; }

    /** Human-readable note about the last reflex that fired (cleared when read). */
    public String takeEvent() {
        String e = lastEvent;
        lastEvent = "";
        return e;
    }

    public void reset() {
        if (state == State.EATING) G.mc().options.useKey.setPressed(false);
        if (drowning) G.mc().options.jumpKey.setPressed(false);
        drowning = false;
        state = State.NONE;
        forceEat = false;
    }

    private void enter(State s, String why) {
        state = s;
        timer = 0;
        calm = 0;
        lastEvent = why;
    }

    private static boolean badFood(String id) {
        return switch (id) {
            case "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chorus_fruit", "golden_apple",
                 "enchanted_golden_apple", "suspicious_stew", "chicken", "tropical_fish" -> true;
            default -> false;
        };
    }

    /** Index of the best food in the main inventory, or -1. */
    private int bestFood() {
        int best = -1;
        double bestScore = -1;
        var inv = G.p().getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.getItem().isFood() || badFood(G.id(s))) continue;
            FoodComponent fc = s.getItem().getFoodComponent();
            if (fc == null) continue;
            double score = fc.getHunger() + fc.getSaturationModifier() * fc.getHunger() * 2;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private static boolean lavaNear() {
        BlockPos p = G.pos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (G.w().getBlockState(p.add(dx, dy, dz)).isOf(Blocks.LAVA)) return true;
                }
            }
        }
        return G.p().isInLava();
    }

    private BlockPos awayFrom(List<LivingEntity> threats, int dist) {
        double ax = 0, az = 0;
        for (LivingEntity e : threats) { ax += e.getX(); az += e.getZ(); }
        ax /= Math.max(1, threats.size());
        az /= Math.max(1, threats.size());
        double dx = G.p().getX() - ax, dz = G.p().getZ() - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) { dx = 1; dz = 0; len = 1; }
        BlockPos p = G.pos();
        return p.add((int) (dx / len * dist), 0, (int) (dz / len * dist));
    }

    private BlockPos safePlace(Agent a, List<LivingEntity> threats) {
        int[] home = a.state.waypoints.get("home");
        if (home == null) home = a.state.storageZone;
        if (home != null) {
            BlockPos h = new BlockPos(home[0], home[1], home[2]);
            if (G.dist(G.pos(), h) < 250 && G.dist(G.pos(), h) > 8) return h;
        }
        return awayFrom(threats, 22);
    }

    public void tick(Agent a) {
        var p = G.p();
        var cfg = a.cfg;

        // surface when running out of air (Baritone normally swims up, this is the backstop)
        if (p.isSubmergedInWater() && p.getAir() < 100) {
            G.mc().options.jumpKey.setPressed(true);
            drowning = true;
        } else if (drowning) {
            G.mc().options.jumpKey.setPressed(false);
            drowning = false;
        }

        List<LivingEntity> hostiles = Mobs.hostiles(cfg.hostileScanRadius);
        double health = p.getHealth();
        int food = p.getHungerManager().getFoodLevel();
        LivingEntity nearest = hostiles.isEmpty() ? null : hostiles.get(0);
        double nearestD = nearest == null ? 99 : p.distanceTo(nearest);

        switch (state) {
            case NONE -> {
                if (lavaNear() && !p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
                    Baritone.cancel();
                    fleeTarget = G.pos().add(G.pos().getX() % 2 == 0 ? 6 : -6, 0, G.pos().getZ() % 2 == 0 ? 6 : -6);
                    Baritone.gotoNear(fleeTarget, 2);
                    enter(State.LAVA, "Lava next to me, backing off.");
                    return;
                }
                if (nearest != null && Mobs.isCreeper(nearest) && nearestD < cfg.creeperSafeDistance && !a.guarding) {
                    fleeTarget = awayFrom(hostiles, 14);
                    Baritone.cancel();
                    Baritone.gotoNear(fleeTarget, 2);
                    enter(State.FLEEING, "Creeper close by, retreating.");
                    return;
                }
                if (nearest != null && health <= cfg.fleeBelowHealth) {
                    fleeTarget = safePlace(a, hostiles);
                    Baritone.cancel();
                    Baritone.gotoNear(fleeTarget, 3);
                    enter(State.FLEEING, "Low health (" + (int) health + ") with hostiles around, retreating.");
                    return;
                }
                if ((food <= cfg.eatBelowFoodLevel || forceEat) && food < 20 && bestFood() >= 0 && (nearest == null || nearestD > 6)) {
                    Baritone.cancel();
                    enter(State.EATING, "Eating (food level " + food + ").");
                    return;
                }
                if (nearest != null && nearestD < 5 && health > cfg.fleeBelowHealth && Mobs.hasWeapon()) {
                    Baritone.cancel();
                    enter(State.FIGHTING, "Fighting " + nearest.getName().getString() + ".");
                }
                forceEat = false;
            }
            case EATING -> {
                timer++;
                int idx = bestFood();
                if (idx < 0 || food >= 20 || timer > 120) { reset(); return; }
                if (!p.isUsingItem()) {
                    G.selectIndex(idx);
                    G.p().setPitch(-70);
                    G.mc().interactionManager.interactItem(p, net.minecraft.util.Hand.MAIN_HAND);
                }
                G.mc().options.useKey.setPressed(true);
                if (nearest != null && nearestD < 4 && timer > 5) reset(); // interrupted by an attacker
            }
            case FLEEING -> {
                timer++;
                if (nearest != null && nearestD < 2.5 && Mobs.hasWeapon() && health > cfg.fleeBelowHealth && !Mobs.isCreeper(nearest)) {
                    enter(State.FIGHTING, "Cornered, fighting back.");
                    return;
                }
                if (!Baritone.busy() && fleeTarget != null && G.dist(G.pos(), fleeTarget) > 4 && timer % 20 == 0) Baritone.gotoNear(fleeTarget, 3);
                boolean safe = nearest == null || nearestD > 16;
                if (safe) calm++; else calm = 0;
                boolean healthy = health > cfg.fleeBelowHealth + 2;
                if ((calm > 40 && (healthy || timer > 300)) || timer > 900) { Baritone.cancel(); reset(); }
            }
            case FIGHTING -> {
                timer++;
                if (health <= cfg.fleeBelowHealth || (nearest != null && Mobs.isCreeper(nearest) && nearestD < cfg.creeperSafeDistance && !a.guarding && nearestD > 3.2)) {
                    reset();
                    return;
                }
                if (nearest == null || nearestD > 10) { if (++calm > 20) { Baritone.cancel(); reset(); } return; }
                calm = 0;
                Mobs.equipWeapon();
                if (nearestD > 2.8) {
                    if (timer % 10 == 0 || !Baritone.busy()) Baritone.gotoNear(nearest.getBlockPos(), 1);
                } else {
                    Baritone.cancel();
                    Mobs.attack(nearest);
                }
                if (timer > 1200) reset();
            }
            case LAVA -> {
                timer++;
                if (!Baritone.busy() && timer % 20 == 0 && fleeTarget != null) Baritone.gotoNear(fleeTarget, 2);
                if (!lavaNear()) calm++; else calm = 0;
                if (calm > 15 || timer > 200) { Baritone.cancel(); reset(); }
            }
        }
    }
}
