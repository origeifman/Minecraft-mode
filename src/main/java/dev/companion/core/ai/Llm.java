package dev.companion.core.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Anything that can answer a chat; Ollama in production, a fake in tests. */
public interface Llm {
    CompletableFuture<String> chat(List<ChatMessage> messages);
}
