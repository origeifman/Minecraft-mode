package dev.companion.game;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Small static helpers over the Minecraft client (Yarn 1.20.1). All must run on the client thread. */
public final class G {
    private G() {}

    public static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public static ClientPlayerEntity p() { return mc().player; }

    public static ClientWorld w() { return mc().world; }

    public static boolean ready() {
        MinecraftClient m = mc();
        return m.player != null && m.world != null && m.interactionManager != null;
    }

    public static void say(String s) {
        if (mc().player != null) mc().player.sendMessage(Text.literal("§b[AI]§r " + s), false);
    }

    public static String dim() { return w().getRegistryKey().getValue().toString(); }

    public static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).getPath(); }

    public static String id(Item i) { return Registries.ITEM.getId(i).getPath(); }

    public static String blockId(BlockPos pos) { return Registries.BLOCK.getId(w().getBlockState(pos).getBlock()).getPath(); }

    public static BlockPos pos() { return p().getBlockPos(); }

    public static Vec3d eye() { return p().getEyePos(); }

    /** item path -> total count over main inventory (hotbar + 27) and offhand. */
    public static Map<String, Integer> inventory() {
        Map<String, Integer> m = new TreeMap<>();
        var inv = p().getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) m.merge(id(s), s.getCount(), Integer::sum);
        }
        ItemStack off = inv.getStack(40);
        if (!off.isEmpty()) m.merge(id(off), off.getCount(), Integer::sum);
        return m;
    }

    public static int count(String itemPath) { return inventory().getOrDefault(itemPath, 0); }

    public static int emptySlots() {
        int n = 0;
        var inv = p().getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isEmpty()) n++;
        return n;
    }

    /** PlayerScreenHandler slot id for main-inventory index (0-8 hotbar, 9-35 main). */
    public static int screenSlot(int invIndex) { return invIndex < 9 ? 36 + invIndex : invIndex; }

    public static int findSlot(String itemPath) {
        var inv = p().getInventory();
        for (int i = 0; i < 36; i++) if (id(inv.getStack(i)).equals(itemPath) && !inv.getStack(i).isEmpty()) return i;
        return -1;
    }

    /** Put the item into the selected hotbar slot. */
    public static boolean select(String itemPath) {
        int idx = findSlot(itemPath);
        return idx >= 0 && selectIndex(idx);
    }

    public static boolean selectIndex(int idx) {
        var inv = p().getInventory();
        if (idx < 9) {
            inv.selectedSlot = idx;
            return true;
        }
        // swap the stack from the main inventory into the current hotbar slot
        mc().interactionManager.clickSlot(p().playerScreenHandler.syncId, screenSlot(idx), inv.selectedSlot, SlotActionType.SWAP, p());
        return true;
    }

    public static void lookAt(Vec3d target) {
        Vec3d e = eye();
        double dx = target.x - e.x, dy = target.y - e.y, dz = target.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p().setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p().setPitch((float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    public static double dist(BlockPos a, BlockPos b) {
        return Math.sqrt(a.getSquaredDistance(b));
    }

    public static double distToCenter(BlockPos b) { return eye().distanceTo(Vec3d.ofCenter(b)); }

    /** Right-click a block on the given face. */
    public static void useBlock(BlockPos target, Direction face) {
        Vec3d hit = Vec3d.ofCenter(target).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        lookAt(hit);
        mc().interactionManager.interactBlock(p(), Hand.MAIN_HAND, new BlockHitResult(hit, face, target, false));
        p().swingHand(Hand.MAIN_HAND);
    }

    public static boolean isSolid(BlockPos pos) {
        BlockState s = w().getBlockState(pos);
        return s.isSolidBlock(w(), pos);
    }

    public static boolean isAir(BlockPos pos) { return w().getBlockState(pos).isAir(); }

    /** Things the agent never throws into a chest. */
    public static boolean isEssential(ItemStack s, Set<String> extraKeep) {
        Item item = s.getItem();
        String id = id(s);
        if (extraKeep.contains(id)) return true;
        if (extraKeep.contains("#logs") && (id.endsWith("_log") || id.endsWith("_stem"))) return true;
        if (extraKeep.contains("#planks") && id.endsWith("_planks")) return true;
        if (item instanceof ToolItem || item instanceof ArmorItem) return true;
        if (item.isFood()) return true;
        return switch (id) {
            case "crafting_table", "furnace", "chest", "torch", "bucket", "water_bucket", "shield", "bow", "arrow",
                 "crossbow", "ender_pearl", "totem_of_undying", "elytra", "shears", "fishing_rod", "flint_and_steel" -> true;
            default -> false;
        };
    }
}
