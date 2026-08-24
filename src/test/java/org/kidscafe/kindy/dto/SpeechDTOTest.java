package org.kidscafe.kindy.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bytes we put on the wire to a speech service, and the ones we read back.
 *
 * <p>Nobody reads either in review. The requests are assembled by Jackson out of annotations and the
 * only place they are ever seen is a 400; the recognition response is a shape nothing else in this
 * application has, and the two mistakes it invites — reading results[0] as the transcript, and
 * reading an absent results as a failure — both produce something that looks like it worked.
 *
 * <p>Nothing here talks to a service or to Spring: a DTO and a mapper, the way ReportServiceTest
 * reads the strings its own service produces.
 */
class SpeechDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode wire(Object dto) {
        return MAPPER.readTree(MAPPER.writeValueAsString(dto));
    }

    private static ChatDTO.STTQueryDTO recognize(String model) {
        return new ChatDTO.STTQueryDTO(
                new ChatDTO.STTQueryDTO.RecognitionConfig("LINEAR16", 16000, "ko-KR", model, true),
                new ChatDTO.STTQueryDTO.RecognitionAudio("YWJj"));
    }

    private static ChatDTO.STTResponseDTO heard(String... segments) {
        ChatDTO.STTResponseDTO response = new ChatDTO.STTResponseDTO();
        response.setResults(java.util.Arrays.stream(segments).map(text -> {
            ChatDTO.STTResponseDTO.Alternative alternative = new ChatDTO.STTResponseDTO.Alternative();
            alternative.setTranscript(text);

            ChatDTO.STTResponseDTO.Result result = new ChatDTO.STTResponseDTO.Result();
            result.setAlternatives(List.of(alternative));
            return result;
        }).toList());

        return response;
    }

    // ── Speech-to-text, outbound ────────────────────────────────────────────────────────────────

    @Test
    void sendsTheFormatTheBrowserActuallyRecords() {
        // The other half of this contract lives in the frontend, which hand-assembles a 16 kHz mono
        // 16-bit WAV rather than using MediaRecorder, precisely because this end accepts nothing
        // else. Disagreeing does not error — it transcribes noise confidently.
        JsonNode config = wire(recognize(null)).path("config");

        assertEquals("LINEAR16", config.path("encoding").asString());
        assertEquals(16000, config.path("sampleRateHertz").asInt());
        assertEquals("ko-KR", config.path("languageCode").asString());
    }

    @Test
    void alwaysAsksForPunctuation() {
        // A primitive precisely so NON_NULL cannot eat it. The transcript is read by a person and
        // then replayed to the model as history; an unpunctuated Korean run-on degrades both.
        JsonNode config = wire(recognize(null)).path("config");

        assertTrue(config.has("enableAutomaticPunctuation"), config.toString());
        assertTrue(config.path("enableAutomaticPunctuation").asBoolean());
    }

    @Test
    void leavesTheModelOffWhenNoneIsConfigured() {
        // Which models exist differs by language, so a blank member is not "no preference" — it is a
        // model name matching nothing, and a 400 on every single recording.
        assertFalse(wire(recognize(null)).path("config").has("model"));
        assertEquals("latest_short", wire(recognize("latest_short")).path("config").path("model").asString());
    }

    @Test
    void carriesTheRecordingAsBase64() {
        byte[] recording = "not really a wav".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.getEncoder().encodeToString(recording);

        ChatDTO.STTQueryDTO query = new ChatDTO.STTQueryDTO(
                new ChatDTO.STTQueryDTO.RecognitionConfig("LINEAR16", 16000, "ko-KR", null, true),
                new ChatDTO.STTQueryDTO.RecognitionAudio(encoded));

        assertArrayEquals(recording,
                Base64.getDecoder().decode(wire(query).path("audio").path("content").asString()));
    }

    @Test
    void doesNotPutTheRecordingInTheLog() {
        // transcribe logs its request the way complete logs its own. Twenty seconds of a child's
        // voice is some 850 KB of base64, once per turn, in a log file somebody keeps.
        String base64 = "QUJDREVGRw";
        String printed = new ChatDTO.STTQueryDTO.RecognitionAudio(base64).toString();

        assertFalse(printed.contains(base64), printed);
        assertTrue(printed.contains(String.valueOf(base64.length())), printed);
    }

    // ── Speech-to-text, inbound ─────────────────────────────────────────────────────────────────

    @Test
    void joinsEverySegmentGoogleHeard() {
        // The regression this file exists for. Google splits a recording where it hears a pause, so
        // results[0] is the first sentence of what a child said, not what they said.
        assertEquals("오늘 유치원에서 친구랑 놀았어",
                heard("오늘 유치원에서", " 친구랑", " 놀았어").transcript());
    }

    @Test
    void readsSilenceAsNothingRatherThanAsAFailure() {
        // A recording with no speech in it comes back as {}. The child pressed the button and said
        // nothing, which is not a mistake and must not become an error.
        assertEquals("", MAPPER.readValue("{}", ChatDTO.STTResponseDTO.class).transcript());
        assertEquals("", MAPPER.readValue("{\"results\":[]}", ChatDTO.STTResponseDTO.class).transcript());
        assertEquals("", MAPPER.readValue("{\"results\":[{\"alternatives\":[]}]}",
                ChatDTO.STTResponseDTO.class).transcript());
        assertEquals("", heard("", "   ").transcript());
    }

    @Test
    void takesTheBestAlternativeAndIgnoresTheRest() {
        // Best first, and there is nowhere in this application to show a second.
        ChatDTO.STTResponseDTO response = MAPPER.readValue(
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"안녕\"},{\"transcript\":\"안녕히\"}]}]}",
                ChatDTO.STTResponseDTO.class);

        assertEquals("안녕", response.transcript());
    }

    @Test
    void ignoresTheFieldsGoogleAddsForItself() {
        ChatDTO.STTResponseDTO response = MAPPER.readValue(
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"안녕\",\"confidence\":0.98}],"
                        + "\"resultEndTime\":\"1.2s\",\"languageCode\":\"ko-kr\"}],"
                        + "\"totalBilledTime\":\"15s\",\"requestId\":\"7\"}",
                ChatDTO.STTResponseDTO.class);

        assertEquals("안녕", response.transcript());
    }

    // ── Text-to-speech, Google ──────────────────────────────────────────────────────────────────

    private static ChatDTO.TTSQueryDTO synthesize(String voice) {
        return new ChatDTO.TTSQueryDTO(
                new ChatDTO.TTSQueryDTO.Input("안녕"),
                new ChatDTO.TTSQueryDTO.Voice("ko-KR", voice),
                new ChatDTO.TTSQueryDTO.AudioConfig("LINEAR16", 1.05));
    }

    @Test
    void namesTheVoiceAndThePace() {
        JsonNode body = wire(synthesize("ko-KR-Standard-A"));

        assertEquals("안녕", body.path("input").path("text").asString());
        assertEquals("ko-KR", body.path("voice").path("languageCode").asString());
        assertEquals("ko-KR-Standard-A", body.path("voice").path("name").asString());
        assertEquals("LINEAR16", body.path("audioConfig").path("audioEncoding").asString());
        assertEquals(1.05, body.path("audioConfig").path("speakingRate").asDouble(), 0.0001);
    }

    @Test
    void leavesTheVoiceNameOffWhenNoneIsConfigured() {
        assertFalse(wire(synthesize(null)).path("voice").has("name"));
    }

    @Test
    void neverSendsAPitch() {
        // Pins a decision somebody will be tempted to undo. A character's pitch is applied during
        // voice conversion; sending it here as well transposes the same voice twice on the path
        // children actually hear, and the answer is a chipmunk.
        JsonNode body = wire(synthesize("ko-KR-Standard-A"));

        assertFalse(body.path("audioConfig").has("pitch"), body.toString());
        // These two are absent for their own reasons: a gender contradicting a named voice is a 400,
        // and an unset sample rate lets the voice render at the rate it sounds best at.
        assertFalse(body.path("voice").has("ssmlGender"), body.toString());
        assertFalse(body.path("audioConfig").has("sampleRateHertz"), body.toString());
    }

    @Test
    void decodesTheAudioGoogleSends() {
        byte[] audio = {(byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F'};

        ChatDTO.TTSResponseDTO response = new ChatDTO.TTSResponseDTO();
        response.setAudioContent(Base64.getEncoder().encodeToString(audio));
        assertArrayEquals(audio, response.audio());

        // A 200 carrying no audio is a failure for the caller to notice, not a zero-length array to
        // hand to a browser that would play nothing and report nothing.
        response.setAudioContent(null);
        assertNull(response.audio());
        response.setAudioContent("");
        assertNull(response.audio());
    }

    @Test
    void doesNotPutTheSynthesizedAudioInTheLog() {
        ChatDTO.TTSResponseDTO response = new ChatDTO.TTSResponseDTO();
        response.setAudioContent("QUJDREVG");

        assertFalse(response.toString().contains("QUJDREVG"), response.toString());
    }

    // ── Text-to-speech, OpenAI dialect ──────────────────────────────────────────────────────────

    @Test
    void asksAnOpenAiServerForWav() {
        // The default is mp3, which would make `produces = "audio/wav"` a lie and hand voice
        // conversion something it cannot open by name.
        JsonNode body = wire(new ChatDTO.SpeechQueryDTO("tts-1", "안녕", "alloy", "wav", 0.95));

        assertEquals("wav", body.path("response_format").asString());
        assertEquals("tts-1", body.path("model").asString());
        assertEquals("안녕", body.path("input").asString());
        assertEquals("alloy", body.path("voice").asString());
        assertEquals(0.95, body.path("speed").asDouble(), 0.0001);
    }

    @Test
    void leavesTheModelAndVoiceOffForAServerThatNamesNeither() {
        JsonNode body = wire(new ChatDTO.SpeechQueryDTO(null, "안녕", null, "wav", 1.0));

        assertFalse(body.has("model"), body.toString());
        assertFalse(body.has("voice"), body.toString());
        // speed is a primitive, so it is always written — 1.0 said out loud is what makes the
        // characters' 1.05 and 0.95 legible beside it in a log.
        assertTrue(body.has("speed"), body.toString());
    }
}
