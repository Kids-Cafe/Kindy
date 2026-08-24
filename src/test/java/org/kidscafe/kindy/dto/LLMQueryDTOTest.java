package org.kidscafe.kindy.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bytes we put on the wire, which are the whole of what lets one code path serve three servers.
 *
 * <p>Nobody reads this request in review — it is assembled by Jackson out of annotations, and the
 * only place it is ever seen is a provider's 400. {@code format} was Ollama's word and means
 * nothing to Gemini or OpenAI; {@code response_format} is theirs and means nothing to Ollama's
 * {@code /api/chat}. Sending either one to the wrong server is one member's difference, and the
 * half that breaks is the diary and the report rather than the chat turns, because only they ask.
 *
 * <p>Nothing here talks to a model or to Spring: a DTO and a mapper, the way ReportServiceTest
 * reads the strings its own service produces.
 */
class LLMQueryDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final List<ChatDTO.LLMMessageDTO> ONE_TURN =
            List.of(new ChatDTO.LLMMessageDTO("user", "안녕"));

    private static JsonNode wire(ChatDTO.LLMQueryDTO.ResponseFormat responseFormat) {
        ChatDTO.LLMQueryDTO query = new ChatDTO.LLMQueryDTO("gemini-2.5-flash", ONE_TURN, responseFormat);

        return MAPPER.readTree(MAPPER.writeValueAsString(query));
    }

    @Test
    void asksForJsonTheWayOpenAiSpellsIt() {
        JsonNode body = wire(ChatDTO.LLMQueryDTO.ResponseFormat.JSON_OBJECT);

        assertEquals("json_object", body.path("response_format").path("type").asString());

        // The member it replaced. Left behind, an OpenAI-compatible server rejects the whole
        // request on the unknown field and the diary silently stops being written.
        assertFalse(body.has("format"), body.toString());
    }

    @Test
    void leavesTheMemberOffForAnOrdinaryTurn() {
        JsonNode body = wire(null);

        // A chat turn wants prose. Sent `response_format` anyway, a child would have a JSON object
        // read aloud to them — and @JsonInclude(NON_NULL) is the only thing preventing it.
        assertFalse(body.has("response_format"), body.toString());
    }

    @Test
    void alwaysSaysItIsNotStreaming() {
        JsonNode body = wire(null);

        // `stream` is a primitive, so it is written even though NON_NULL is on the class. That is
        // wanted: an omitted `stream` is a server's default, and a default that ever became true
        // would give us a chunked body LLMResponseDTO cannot read.
        assertTrue(body.has("stream"), body.toString());
        assertFalse(body.path("stream").asBoolean());
    }

    @Test
    void sendsNothingAboutATurnButRoleAndContent() {
        JsonNode message = wire(null).path("messages").get(0);

        // CHAT_ID, NUM, TYPE and CREATED_AT are ours, not the model's: a server that does not
        // reject them outright still charges for them.
        assertEquals(2, message.size(), message.toString());
        assertEquals("user", message.path("role").asString());
        assertEquals("안녕", message.path("content").asString());
    }
}
