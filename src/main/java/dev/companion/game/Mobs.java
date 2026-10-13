package dev.companion.game;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hostile-mob queries and melee helpers. */
public final class Mobs {
    private Mobs() {}

    public static List<LivingEntity> hostiles(double radius) {
        Box box = G.p().getBoundingBox().expand(radius);
        List<LivingEntity> l = new ArrayList<>(G.w().getEntitiesByClass(LivingEntity.class, box, e ->
                e instanceof Monster && e.isAlive() && !(e instanceof Angerable a && !a.hasAngerTime())
                        && e.squaredDistanceTo(G.p()) <= radius * radius));
        l.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(G.p())));
        return l;
    }

    public static boolean isCreeper(LivingEntity e) { return e instanceof CreeperEntity; }

    private static int weaponScore(String id) {
        int tier = id.startsWith("netherite") ? 5 : id.startsWith("diamond") ? 4 : id.startsWith("iron") ? 3
                : id.startsWith("stone") ? 2 : id.startsWith("wooden") || id.startsWith("golden") ? 1 : 0;
        if (id.endsWith("_sword")) return tier * 10 + 5;
        if (id.endsWith("_axe")) return tier * 10 + 3;
        return -1;
    }

    public static boolean hasWeapon() {
        for (int i = 0; i < 36; i++) if (weaponScore(G.id(G.p().getInventory().getStack(i))) >= 0) return true;
        return false;
    }

    /** Select the best sword/axe. @return false if none */
    public static boolean equipWeapon() {
        int best = -1, bestScore = -1;
        var inv = G.p().getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            int sc = weaponScore(G.id(s));
            if (sc > bestScore) { best = i; bestScore = sc; }
        }
        if (best < 0) return false;
        if (best < 9 && inv.selectedSlot == best) return true;
        return G.selectIndex(best);
    }

    /** Swing at the entity if in reach and the cooldown is ready. */
    public static boolean attack(LivingEntity e) {
        if (G.p().squaredDistanceTo(e) > 3.2 * 3.2) return false;
        if (G.p().getAttackCooldownProgress(0.5f) < 0.95f) return false;
        G.lookAt(new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ()));
        G.mc().interactionManager.attackEntity(G.p(), e);
        G.p().swingHand(Hand.MAIN_HAND);
        return true;
    }
}
