package org.kidscafe.kindy.service.impl;

import org.junit.jupiter.api.Test;
import org.kidscafe.kindy.service.ServiceUnavailableException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a deployment has no speech or language service.
 *
 * <p>The five URLs are declared {@code ${VAR:}} in application.properties so the application starts
 * without them — the same bargain the social providers and the photo bucket already make. This is
 * the other half of that: the point of use has to tell "never configured" apart from "configured
 * and briefly down", because they arrive at a parent as the same blank screen and are fixed in
 * completely different places.
 *
 * <p>Nothing here talks to a model, the database or Spring — the decision under test is a pure
 * one, so the service is built with null collaborators the way DiaryServiceTest builds its own.
 */
class ChatServiceTest {
    @Test
    void refusesAUrlThatWasNeverConfigured() {
        // Blank, not absent: an unset environment variable leaves the property present and empty,
        // which is exactly why this check cannot be a @Value default.
        ServiceUnavailableException thrown = assertThrows(ServiceUnavailableException.class,
                () -> ChatService.configured("", "STT_URL"));

        // The message has to name the variable. It is read by whoever is holding a deployment that
        // half-works, and "not configured" alone does not say which of the five is missing.
        assertTrue(thrown.getMessage().contains("STT_URL"), thrown.getMessage());
    }

    @Test
    void treatsWhitespaceAndNullAsUnconfigured() {
        // A variable set to a stray space is a typo, not a configuration, and resolves against no
        // base URL just as badly as an empty one.
        assertThrows(ServiceUnavailableException.class, () -> ChatService.configured("   ", "LLM_URL"));

        // Null never happens through Spring, but it does when this class is constructed directly,
        // and refusing is the same right answer.
        assertThrows(ServiceUnavailableException.class, () -> ChatService.configured(null, "TTS_URL"));
    }

    @Test
    void passesAConfiguredUrlStraightThrough() {
        // The guard is a gate, not a parser: a deployment's URL reaches the client untouched.
        String url = "http://127.0.0.1:9000/v1/audio/transcriptions";

        assertEquals(url, ChatService.configured(url, "STT_URL"));
    }

    @Test
    void refusesBeforeBuildingARequest() {
        // The guard runs ahead of the outbound call rather than inside it. Proven by the client
        // being null: reaching it at all would be a NullPointerException, so a
        // ServiceUnavailableException means nothing was sent and nothing needed to be.
        ChatService service = new ChatService(null, null, null);

        assertThrows(ServiceUnavailableException.class, () -> service.transcribe(null));
    }

    @Test
    void refusesBeforeSendingACompletion() {
        // As above, for the model. It also pins the ordering the API key depends on: the header is
        // only ever attached to a request the URL guard has already allowed.
        ChatService service = new ChatService(null, null, null);

        assertThrows(ServiceUnavailableException.class, () -> service.complete(List.of(), "json"));
    }

    @Test
    void asksForJsonOnlyWhenAskedTo() {
        // Blank is how a deployment turns structured output off — LLM_DIARY_FORMAT= — and it has to
        // leave the member off the request rather than send an empty one.
        assertNull(ChatService.responseFormat(null));
        assertNull(ChatService.responseFormat(""));
        assertNull(ChatService.responseFormat("   "));

        // "json" is Ollama's word for it, kept in the two format properties so that a deployment's
        // existing configuration goes on meaning what it meant.
        assertEquals("json_object", ChatService.responseFormat("json").getType());
        assertEquals("json_object", ChatService.responseFormat(" JSON ").getType());
        assertEquals("json_object", ChatService.responseFormat("json_object").getType());
    }

    @Test
    void readsAnUnknownFormatAsJsonRatherThanForwardingIt() {
        // Passing a stray value into `type` would be a 400 from the provider, which is a worse
        // answer to a typo than the one thing it could plausibly have meant.
        assertEquals("json_object", ChatService.responseFormat("json_schema").getType());
    }
}
