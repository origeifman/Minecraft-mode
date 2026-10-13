package dev.companion.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The ReAct decision loop, minus the game: feed it observations, it returns the next tool call.
 * Malformed or invalid replies are fed back to the model so it can correct itself.
 */
public final class ReActAgent {
    private final Llm llm;
    private final int maxHistory;
    private final int maxRepairs;
    private final List<ChatMessage> history = new ArrayList<>();
    private String goal = "";
    private int steps;

    public ReActAgent(Llm llm, int maxHistory) {
        this(llm, maxHistory, 2);
    }

    public ReActAgent(Llm llm, int maxHistory, int maxRepairs) {
        this.llm = llm;
        this.maxHistory = Math.max(6, maxHistory);
        this.maxRepairs = maxRepairs;
    }

    public void begin(String goal) {
        this.goal = goal;
        this.steps = 0;
        history.clear();
    }

    public int steps() { return steps; }

    public String goal() { return goal; }

    /**
     * @param observation what happened since the last call (first call: the world snapshot)
     */
    public CompletableFuture<AgentAction> next(String observation) {
        String content = history.isEmpty()
                ? "PLAYER REQUEST: " + goal + "\n\nCURRENT STATE:\n" + observation
                : "OBSERVATION:\n" + observation;
        history.add(ChatMessage.user(content));
        steps++;
        return attempt(0);
    }

    private CompletableFuture<AgentAction> attempt(int repairs) {
        return llm.chat(window()).thenCompose(reply -> {
            AgentAction action;
            String error;
            try {
                action = ActionParser.parse(reply);
                error = Tools.validate(action);
            } catch (ActionParser.ParseException e) {
                action = null;
                error = "could not parse your reply (" + e.getMessage() + ")";
            }
            history.add(ChatMessage.assistant(reply));
            if (error == null) return CompletableFuture.completedFuture(action);
            if (repairs >= maxRepairs) {
                return CompletableFuture.failedFuture(new IllegalStateException("model kept producing invalid output: " + error));
            }
            history.add(ChatMessage.user("INVALID: " + error + ". Reply again with ONE valid JSON object."));
            return attempt(repairs + 1);
        });
    }

    private List<ChatMessage> window() {
        List<ChatMessage> out = new ArrayList<>();
        out.add(ChatMessage.system(Prompts.system()));
        int from = Math.max(0, history.size() - maxHistory);
        // always keep the original request visible
        if (from > 0) out.add(ChatMessage.user("(earlier steps trimmed) PLAYER REQUEST: " + goal));
        out.addAll(history.subList(from, history.size()));
        return out;
    }
}
