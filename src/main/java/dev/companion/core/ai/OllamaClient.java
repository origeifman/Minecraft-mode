package dev.companion.core.ai;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Minimal async client for a local Ollama server (/api/chat, /api/tags). Never blocks the caller. */
public final class OllamaClient implements Llm {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final Duration timeout;

    public OllamaClient(String baseUrl, String model, double temperature, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public CompletableFuture<String> chat(List<ChatMessage> messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.addProperty("format", "json");
        JsonArray arr = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            arr.add(o);
        }
        body.add("messages", arr);
        JsonObject opts = new JsonObject();
        opts.addProperty("temperature", temperature);
        body.add("options", opts);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Ollama HTTP " + resp.statusCode() + ": " + abbreviate(resp.body()));
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("error")) throw new IllegalStateException("Ollama: " + root.get("error").getAsString());
            return root.getAsJsonObject("message").get("content").getAsString();
        });
    }

    /** Names of the models installed on the server; fails if the server is unreachable. */
    public CompletableFuture<List<String>> listModels() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags")).timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("Ollama HTTP " + resp.statusCode());
            List<String> names = new ArrayList<>();
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("models")) {
                for (JsonElement e : root.getAsJsonArray("models")) names.add(e.getAsJsonObject().get("name").getAsString());
            }
            return names;
        });
    }

    /** Download a model (blocks the future until finished; models are several GB, so the timeout is long). */
    public CompletableFuture<String> pull(String modelName) {
        JsonObject body = new JsonObject();
        body.addProperty("name", modelName);
        body.addProperty("stream", false);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/pull"))
                .timeout(Duration.ofMinutes(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("pull failed, HTTP " + resp.statusCode() + ": " + abbreviate(resp.body()));
            JsonObject root = JsonParser.parseString(resp.body().lines().reduce((a, b) -> b).orElse("{}")).getAsJsonObject();
            if (root.has("error")) throw new IllegalStateException(root.get("error").getAsString());
            return root.has("status") ? root.get("status").getAsString() : "ok";
        });
    }

    public String baseUrl() { return baseUrl; }

    private static String abbreviate(String s) { return s.length() > 200 ? s.substring(0, 200) + "..." : s; }
}
