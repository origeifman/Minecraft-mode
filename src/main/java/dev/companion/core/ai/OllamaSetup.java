package dev.companion.core.ai;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Makes sure Ollama is running and has the configured model: starts `ollama serve`, pulls the model. */
public final class OllamaSetup {
    private OllamaSetup() {}

    /** Names match with or without the ":latest" tag. */
    static boolean hasModel(List<String> installed, String model) {
        String want = model.contains(":") ? model : model + ":latest";
        for (String n : installed) if (n.equals(want) || n.equals(model)) return true;
        return false;
    }

    static boolean isLocal(String baseUrl) {
        try {
            String h = URI.create(baseUrl).getHost();
            return h == null || h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1") || h.equals("[::1]");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Blocking; run it on a background thread. Reports progress via `say`.
     * @return null on success, otherwise a human-readable reason it could not be completed
     */
    public static String run(OllamaClient client, String model, Consumer<String> say) {
        List<String> models = list(client);
        if (models == null) {
            if (!isLocal(client.baseUrl())) return "Ollama at " + client.baseUrl() + " is not reachable and it is not a local address, so I will not start it.";
            say.accept("Ollama is not running, trying to start it (`ollama serve`)...");
            try {
                new ProcessBuilder("ollama", "serve").redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (Exception e) {
                return "Could not start Ollama (" + e.getMessage() + "). Install it from https://ollama.com/download and run it, then try again.";
            }
            for (int i = 0; i < 30 && models == null; i++) {
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return "interrupted"; }
                models = list(client);
            }
            if (models == null) return "Started Ollama but it did not answer within 15 seconds.";
            say.accept("Ollama is up.");
        }
        if (hasModel(models, model)) return null;
        say.accept("Model '" + model + "' is not installed, downloading it now (this can take several minutes)...");
        try {
            client.pull(model).get();
        } catch (Exception e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return "Could not download model '" + model + "': " + c.getMessage();
        }
        say.accept("Model '" + model + "' is ready.");
        return null;
    }

    private static List<String> list(OllamaClient c) {
        try {
            return c.listModels().get();
        } catch (Exception e) {
            return null;
        }
    }
}
