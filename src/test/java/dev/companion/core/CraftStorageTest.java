package dev.companion.core;

import dev.companion.core.craft.*;
import dev.companion.core.storage.ChestIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CraftStorageTest {

    private static Map<String, Integer> inv(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static int total(List<Step> steps, Step.Kind k, String item) {
        return steps.stream().filter(s -> s.kind() == k && s.item().equals(item)).mapToInt(Step::count).sum();
    }

    @Test
    void toolTiers() {
        assertEquals(ToolTier.IRON, MiningRules.requirement("diamond_ore").tier());
        assertEquals(ToolTier.STONE, MiningRules.requirement("deepslate_iron_ore").tier());
        assertEquals(ToolTier.WOODEN, MiningRules.requirement("stone").tier());
        assertEquals("none", MiningRules.requirement("oak_log").tool());
        assertEquals(Optional.of("iron_pickaxe"), MiningRules.missingTool("diamond_ore", inv("stone_pickaxe", 1)));
        assertEquals(Optional.empty(), MiningRules.missingTool("diamond_ore", inv("diamond_pickaxe", 1)));
        assertEquals(Optional.of("wooden_pickaxe"), MiningRules.missingTool("stone", inv()));
        assertEquals(Optional.empty(), MiningRules.missingTool("iron_ore", inv("iron_pickaxe", 1)));
    }

    @Test
    void woodenPickaxeFromNothing() {
        List<Step> s = CraftPlanner.plan("wooden_pickaxe", 1, inv());
        // 3 planks + 2 sticks(=1 craft of 2 planks) + table(4 planks) => 9 planks => 3 plank crafts => 3 logs
        assertEquals(3, total(s, Step.Kind.GATHER, "#logs"));
        assertEquals(1, total(s, Step.Kind.CRAFT, "wooden_pickaxe"));
        assertEquals(1, total(s, Step.Kind.CRAFT, "stick"));
        assertEquals(1, total(s, Step.Kind.CRAFT, "crafting_table"));
        int pick = indexOf(s, "wooden_pickaxe"), table = indexOf(s, "crafting_table"), stick = indexOf(s, "stick");
        assertTrue(table < pick && stick < pick);
        assertTrue(s.get(0).kind() == Step.Kind.GATHER);
    }

    @Test
    void usesInventoryAndSkipsFinishedWork() {
        List<Step> s = CraftPlanner.plan("stone_pickaxe", 1, inv("cobblestone", 3, "stick", 2, "crafting_table", 1));
        assertEquals(List.of(Step.Kind.CRAFT), s.stream().map(Step::kind).distinct().toList());
        assertEquals(1, s.size());
        assertTrue(s.get(0).table());
    }

    @Test
    void ironPickaxeNeedsFurnaceAndSmelting() {
        List<Step> s = CraftPlanner.plan("iron_pickaxe", 1, inv("stone_pickaxe", 1));
        assertEquals(3, total(s, Step.Kind.GATHER, "raw_iron"));
        assertEquals(3, total(s, Step.Kind.SMELT, "iron_ingot"));
        assertEquals(1, total(s, Step.Kind.CRAFT, "furnace"));
        assertTrue(total(s, Step.Kind.GATHER, "cobblestone") >= 8);
        assertTrue(total(s, Step.Kind.GATHER, "coal") >= 1);
        assertTrue(indexOf(s, "iron_ingot") < indexOf(s, "iron_pickaxe"));
        assertTrue(indexOf(s, "furnace") < indexOf(s, "iron_ingot"));
    }

    @Test
    void chestUsesExistingPlanksOfAnyWood() {
        List<Step> s = CraftPlanner.plan("chest", 1, inv("birch_planks", 8, "crafting_table", 1));
        assertEquals(1, s.size());
        assertEquals("chest", s.get(0).item());
    }

    @Test
    void plainGatherIsASingleStep() {
        List<Step> s = CraftPlanner.plan("coal", 5, inv("coal", 2));
        assertEquals(List.of(Step.gather("coal", 3)), s);
    }

    private static int indexOf(List<Step> s, String item) {
        for (int i = 0; i < s.size(); i++) if (s.get(i).item().equals(item)) return i;
        return -1;
    }

    @Test
    void chestIndexTracksQueriesAndPersists() throws Exception {
        CompanionState st = new CompanionState();
        st.chests.update("minecraft:overworld", 10, 64, 10, 27, 3, Map.of("cobblestone", 128, "oak_log", 64));
        st.chests.update("minecraft:overworld", 20, 64, 10, 27, 27, Map.of("dirt", 999));
        st.chests.update("minecraft:the_nether", 0, 64, 0, 27, 1, Map.of("cobblestone", 5));
        st.waypoints.put("home", new int[]{1, 2, 3});
        st.storageZone = new int[]{10, 64, 10};

        assertEquals(133, st.chests.total("cobblestone"));
        assertEquals(1, st.chests.withItem("minecraft:overworld", "cobblestone", 0, 64, 0).size());
        assertEquals(10, st.chests.withSpace("minecraft:overworld", 20, 64, 10).x); // the full one is skipped
        st.chests.adjust("minecraft:overworld", 10, 64, 10, "cobblestone", -100, 0);
        assertEquals(28, st.chests.get("minecraft:overworld", 10, 64, 10).items.get("cobblestone"));

        Path f = Files.createTempFile("state", ".json");
        st.save(f);
        CompanionState back = CompanionState.load(f);
        assertEquals(3, back.chests.chests.size());
        assertEquals(28 + 5, back.chests.total("cobblestone"));
        assertArrayEquals(new int[]{1, 2, 3}, back.waypoints.get("home"));
        assertArrayEquals(new int[]{10, 64, 10}, back.storageZone);
        st.chests.remove("minecraft:overworld", 20, 64, 10);
        assertEquals(2, st.chests.chests.size());
    }
}
