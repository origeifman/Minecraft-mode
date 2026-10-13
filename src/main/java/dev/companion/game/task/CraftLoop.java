package dev.companion.game.task;

import dev.companion.game.G;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

/** Tick-driven crafting through the vanilla recipe book click (works for 2x2 and 3x3 grids). */
final class CraftLoop {
    private final Recipe<?> recipe;
    private final String outputName;
    private int remaining, phase, wait;
    String error;

    CraftLoop(Recipe<?> recipe, String outputName, int times) {
        this.recipe = recipe;
        this.outputName = outputName;
        this.remaining = times;
    }

    private static boolean satisfiable(Recipe<?> r, List<ItemStack> stacks, int[] counts) {
        int[] c = counts.clone();
        for (Ingredient ing : r.getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < stacks.size(); i++) {
                if (c[i] > 0 && ing.test(stacks.get(i))) { c[i]--; found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Find a crafting recipe producing `out` ("#planks" = whichever plank type the inventory can make). */
    static Recipe<?> resolve(String out, boolean needsGrid3x3) {
        var world = G.w();
        List<ItemStack> stacks = new ArrayList<>();
        var inv = G.p().getInventory();
        for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty()) stacks.add(inv.getStack(i));
        int[] counts = new int[stacks.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = stacks.get(i).getCount();

        Recipe<?> fallback = null;
        for (Recipe<?> r : world.getRecipeManager().values()) {
            if (r.getType() != RecipeType.CRAFTING) continue;
            String oid = G.id(r.getOutput(world.getRegistryManager()));
            boolean match = out.equals("#planks") ? oid.endsWith("_planks") : oid.equals(out);
            if (!match) continue;
            if (!needsGrid3x3 && !r.fits(2, 2)) continue;
            if (fallback == null) fallback = r;
            if (satisfiable(r, stacks, counts)) return r;
        }
        return fallback;
    }

    /** @return null while running, "" when finished, otherwise an error message. */
    String tick(ScreenHandler h) {
        switch (phase) {
            case 0 -> {
                G.mc().interactionManager.clickRecipe(h.syncId, recipe, false);
                wait = 0;
                phase = 1;
            }
            case 1 -> {
                wait++;
                if (!h.getSlot(0).getStack().isEmpty()) {
                    G.mc().interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, G.p());
                    phase = 2;
                    wait = 0;
                } else if (wait > 20) {
                    return "missing ingredients (or full inventory) while crafting " + outputName;
                }
            }
            case 2 -> {
                if (++wait >= 2) {
                    if (--remaining <= 0) return "";
                    phase = 0;
                }
            }
            default -> { }
        }
        return null;
    }
}
