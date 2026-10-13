package dev.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.companion.core.storage.ChestIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything the agent remembers between sessions: chests, waypoints, storage zone. */
public final class CompanionState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ChestIndex chests = new ChestIndex();
    /** name -> {x,y,z} */
    public Map<String, int[]> waypoints = new LinkedHashMap<>();
    /** Centre of the designated storage zone {x,y,z}, or null until set. */
    public int[] storageZone;

    public static CompanionState load(Path file) {
        try {
            if (Files.exists(file)) {
                CompanionState s = GSON.fromJson(Files.readString(file), CompanionState.class);
                if (s != null) {
                    if (s.chests == null) s.chests = new ChestIndex();
                    if (s.waypoints == null) s.waypoints = new LinkedHashMap<>();
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return new CompanionState();
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
