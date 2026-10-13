package dev.companion.core;

import com.sun.net.httpserver.HttpServer;
import dev.companion.core.ai.*;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AiTest {

    @Test
    void parsesFencedAndProseWrappedJson() throws Exception {
        AgentAction a = ActionParser.parse("Sure!\n```json\n{\"thought\":\"need wood {x}\",\"action\":\"gather\",\"args\":{\"item\":\"oak_log\",\"count\":16}}\n```");
        assertEquals("gather", a.action());
        assertEquals("oak_log", a.str("item", ""));
        assertEquals(16, a.integer("count", 0));
    }

    @Test
    void parsesAliasKeysAndTopLevelArgs() throws Exception {
        AgentAction a = ActionParser.parse("{\"tool\":\"Go-To\",\"x\":1,\"y\":64,\"z\":-5}");
        assertEquals("go_to", a.action());
        assertEquals(-5, a.integer("z", 0));
        AgentAction b = ActionParser.parse("{\"action\":\"goto\",\"arguments\":{\"x\":\"3\",\"y\":4,\"z\":5}}");
        assertEquals(3, b.integer("x", 0));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ActionParser.ParseException.class, () -> ActionParser.parse("I will mine now"));
        assertThrows(ActionParser.ParseException.class, () -> ActionParser.parse("{\"thought\":\"x\"}"));
    }

    @Test
    void validatesTools() throws Exception {
        assertNull(Tools.validate(AgentAction.of("gather", "item", "coal", "count", 3)));
        assertNotNull(Tools.validate(AgentAction.of("gather", "item", "coal")));
        assertNotNull(Tools.validate(AgentAction.of("teleport")));
        assertNull(Tools.validate(AgentAction.of("stop")));
    }

    @Test
    void reactRepairsInvalidReplyThenReplansAfterFailure() throws Exception {
        List<String> replies = new ArrayList<>(List.of(
                "I think I should mine",                                                           // unparsable -> repair
                "{\"thought\":\"t\",\"action\":\"mine_everything\",\"args\":{}}",                   // unknown tool -> repair
                "{\"thought\":\"get iron\",\"action\":\"gather\",\"args\":{\"item\":\"raw_iron\",\"count\":4}}",
                "{\"thought\":\"lava, try elsewhere\",\"action\":\"goto\",\"args\":{\"x\":0,\"y\":60,\"z\":0}}"));
        List<List<ChatMessage>> seen = new ArrayList<>();
        Llm fake = msgs -> { seen.add(new ArrayList<>(msgs)); return CompletableFuture.completedFuture(replies.remove(0)); };
        ReActAgent agent = new ReActAgent(fake, 20);
        agent.begin("get iron");
        AgentAction first = agent.next("pos 0 64 0").get(1, TimeUnit.SECONDS);
        assertEquals("gather", first.action());
        assertEquals(3, seen.size());
        assertTrue(seen.get(2).stream().anyMatch(m -> m.content().startsWith("INVALID")));
        AgentAction second = agent.next("FAILURE: path blocked by lava").get(1, TimeUnit.SECONDS);
        assertEquals("goto", second.action());
        assertTrue(seen.get(3).stream().anyMatch(m -> m.content().contains("path blocked by lava")));
        assertEquals("system", seen.get(3).get(0).role());
    }

    @Test
    void reactGivesUpAfterRepeatedGarbage() {
        Llm fake = msgs -> CompletableFuture.completedFuture("nope");
        ReActAgent agent = new ReActAgent(fake, 20, 1);
        agent.begin("x");
        var ex = assertThrows(Exception.class, () -> agent.next("s").get(1, TimeUnit.SECONDS));
        assertTrue(ex.getMessage().contains("invalid"));
    }

    @Test
    void ollamaClientTalksToServer() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final String[] body = new String[1];
        srv.createContext("/api/chat", ex -> {
            body[0] = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] r = "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"action\\\":\\\"stop\\\"}\"}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, r.length);
            ex.getResponseBody().write(r);
            ex.close();
        });
        srv.createContext("/api/tags", ex -> {
            byte[] r = "{\"models\":[{\"name\":\"llama3.1:8b\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, r.length);
            ex.getResponseBody().write(r);
            ex.close();
        });
        srv.start();
        try {
            OllamaClient c = new OllamaClient("http://127.0.0.1:" + srv.getAddress().getPort() + "/", "m", 0.1, 5);
            String reply = c.chat(List.of(ChatMessage.system("s"), ChatMessage.user("u"))).get(5, TimeUnit.SECONDS);
            assertEquals("{\"action\":\"stop\"}", reply);
            assertTrue(body[0].contains("\"format\":\"json\"") && body[0].contains("\"stream\":false") && body[0].contains("\"model\":\"m\""));
            assertEquals(List.of("llama3.1:8b"), c.listModels().get(5, TimeUnit.SECONDS));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void ollamaDownFailsAsync() {
        OllamaClient c = new OllamaClient("http://127.0.0.1:1", "m", 0.1, 2);
        assertThrows(Exception.class, () -> c.chat(List.of(ChatMessage.user("x"))).get(5, TimeUnit.SECONDS));
    }

    @Test
    void ruleBasedPlanner() {
        assertEquals("gather", RuleBasedPlanner.plan("Go gather 64 iron ore").get(0).action());
        AgentAction g = RuleBasedPlanner.plan("Go gather 64 iron ore").get(0);
        assertEquals("iron_ore", g.str("item", ""));
        assertEquals(64, g.integer("count", 0));
        AgentAction oak = RuleBasedPlanner.plan("Fetch 32 oak logs").get(0);
        assertEquals("oak_log", oak.str("item", ""));
        assertEquals("follow", RuleBasedPlanner.plan("Follow me to base").get(0).action());
        assertEquals("secure_area", RuleBasedPlanner.plan("Secure the area").get(0).action());
        assertEquals("schematic_info", RuleBasedPlanner.plan("build").get(0).action());
        assertEquals("build", RuleBasedPlanner.plan("build").get(1).action());
        AgentAction go = RuleBasedPlanner.plan("go to 100 64 -200").get(0);
        assertEquals("goto", go.action());
        assertEquals(-200, go.integer("z", 0));
        assertEquals("craft", RuleBasedPlanner.plan("make an iron pickaxe").get(0).action());
        assertEquals("stop", RuleBasedPlanner.plan("stop").get(0).action());
        assertTrue(RuleBasedPlanner.plan("what is the meaning of life").isEmpty());
        List<AgentAction> bring = RuleBasedPlanner.plan("bring me 10 coal");
        assertEquals("deliver", bring.get(1).action());
    }

    @Test
    void itemNames() {
        assertEquals("iron_ore", ItemNames.normalize("iron ores"));
        assertEquals("oak_log", ItemNames.normalize("oak logs"));
        assertEquals("oak_planks", ItemNames.normalize("planks"));
        assertEquals("diamond", ItemNames.normalize("diamonds"));
        assertEquals("cobblestone", ItemNames.normalize("stone"));
        assertEquals("iron_pickaxe", ItemNames.normalize("minecraft:iron_pickaxe"));
        assertEquals("oak_stairs", ItemNames.normalize("oak stairs"));
    }
}
