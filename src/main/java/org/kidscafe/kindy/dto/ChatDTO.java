package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Base64;
import java.util.List;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatDTO {
    private Long id;
    private Long kindergartenId;
    private String client;
    private String host;
    private Long createdAt;

    /**
     * The AI partner a child talks to.
     *
     * The choice is not a column anywhere — it lives on the client and rides along with each
     * request — so it may only decide what we tell the model about itself. Unknown or missing
     * values fall back to {@link #kio} rather than failing the turn: a child mid-conversation
     * losing an answer is worse than talking to the other character for one turn.
     */
    @Getter
    public enum Partner {
        kio("키오", "당신은 씩씩하고 에너지가 넘칩니다. 호기심이 많아 아이의 이야기에 신나게 반응하고, 아이가 힘들어할 때는 용기를 북돋아 줍니다. 말투는 활기차고 밝습니다.", 1.05, 4),
        kina("키나", "당신은 따뜻하고 섬세합니다. 아이의 마음을 먼저 헤아려 기쁠 때 함께 기뻐하고 속상할 때는 조용히 곁에 있어 줍니다. 말투는 부드럽고 다정합니다.", 0.95, 8);

        private final String label;
        private final String persona;
        /**
         * How the character sounds, on top of whichever voice model is configured for it.
         *
         * These two run through the same pipeline everything else does — synthesis speed, then a
         * pitch shift during conversion — so the two still sound apart even where both partners
         * share one voice model, which is what we ship. Kio is the brisker, lower of the two;
         * Kina the softer, higher one.
         */
        private final double speed;
        private final int pitchShift;

        Partner(String label, String persona, double speed, int pitchShift) {
            this.label = label;
            this.persona = persona;
            this.speed = speed;
            this.pitchShift = pitchShift;
        }

        public static Partner of(String value) {
            if (value == null || value.isBlank()) return kio;
            for (Partner p : values()) {
                if (p.name().equalsIgnoreCase(value.trim())) return p;
            }
            return kio;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class QueryDTO {
        private Long id;
        private Integer minNum;
        private Integer maxNum;
        private Long minTimestamp;
        private Long maxTimestamp;
        private Integer maxAmount;
    }

    /**
     * One calendar day of a child's conversations with their AI partner, and how much was said.
     *
     * The diary is written per day, so the counts are what decides whether a day has enough to
     * write about at all — a day with two "안녕" turns is not a day worth a diary.
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class DayDTO {
        /** YYYY-MM-DD, in the database's timezone — the same calendar the diary is keyed on. */
        private String date;
        /** TEXT turns the child themself took. Data cards are not things anyone said. */
        private int userMessages;
        private int totalMessages;
        /**
         * When the last thing that day was said, in epoch milliseconds.
         *
         * This is what tells a finished day from one still being lived in: compared against the
         * diary's SOURCE_AT it says whether the child has kept talking since the entry was
         * written, which is the normal state of today's diary.
         */
        private long lastMessageAt;
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageDTO {
        /**
         * What kind of message this is: something someone said, or a report card someone pulled in.
         *
         * The names do not match {@link ReportDTO.Category} — FRIEND/STUDY here, FRIENDSHIP/LEARNING
         * there — which is exactly why {@link #category()} exists rather than a
         * {@code valueOf(type.name())} at each call site.
         */
        public enum Type {
            TEXT,
            FOOD,
            HEALTH,
            FRIEND,
            PERSONALITY,
            STUDY

            ;

            /** The report category this card shows, or null for TEXT, which shows no report. */
            public ReportDTO.Category category() {
                return switch (this) {
                    case TEXT -> null;
                    case FOOD -> ReportDTO.Category.FOOD;
                    case HEALTH -> ReportDTO.Category.HEALTH;
                    case FRIEND -> ReportDTO.Category.FRIENDSHIP;
                    case PERSONALITY -> ReportDTO.Category.PERSONALITY;
                    case STUDY -> ReportDTO.Category.LEARNING;
                };
            }
        }

        public enum Role {
            user,
            assistant,
            system,
            tool
        }

        private Long chatId;
        private Integer num;
        private Type type;
        private String content;
        private Role role;
        /**
         * Who wrote it — the user id of the person, or null when nobody did.
         *
         * ROLE alone cannot answer this. It separates the child from the model in a self-chat,
         * where the two sides are the same person, but a conversation between two people is all
         * `user` rows and ROLE says nothing about which of them spoke. Without this the client had
         * to guess, and the only guess available — "the host said everything" — showed each
         * participant their own messages under the other person's name.
         *
         * Null means no person: an `assistant` turn, or a row written before the column existed
         * (see docs/migration-chat-author.sql — old two-person threads are not attributable and are
         * left unattributed rather than guessed at).
         *
         * The server stamps it from the session, and it is never a request parameter — the same
         * rule ROLE follows, and for the same reason: a caller that can name its own author can put
         * words in the other participant's mouth.
         */
        private String author;
        /** The author's per-kindergarten nickname, falling back to their real name. Read-only. */
        private String authorName;
        /**
         * The report this card shows — the exact version, not the category.
         *
         * Null for TEXT, which shows no report, and null for cards written before the column existed
         * whose child could not be established (docs/migration-report-identity.sql PHASE 4).
         * <p>
         * This is what makes a card mean the same thing tomorrow. {@link #type} says which of the
         * five reports it is, but a child's "food report" is a moving target — every regeneration
         * writes a new one — so a card that stored only the category re-read itself against today's
         * numbers every time it was rendered, and a conversation from last March came back saying
         * something nobody had said. An id does not move.
         * <p>
         * Like {@code role} and {@code author} it is never a request parameter: {@code chat/send}
         * takes the child and resolves their current report itself. A caller that could name the id
         * could pin another child's report into a thread.
         */
        private Long reportId;
        /**
         * {@link #reportId}'s JSON blob, carried inline so a card paints from the message alone.
         *
         * The alternative — the client fetching each id it sees — is a round trip per card and a
         * second access-gated endpoint to get right, to deliver data the thread's participants can
         * already read. Read-only, and absent unless this is a card.
         */
        private String reportData;
        private Long createdAt;
    }

    /** One exchange: what the person said, and what the assistant answered (null if it couldn't). */
    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TurnDTO {
        private MessageDTO sent;
        private MessageDTO reply;
    }

    /**
     * One turn as the LLM wants to see it.
     *
     * {@link MessageDTO} cannot be sent as-is: it also carries CHAT_ID, NUM, TYPE and CREATED_AT,
     * which are ours, not the model's. OpenAI-compatible servers reject unknown members of
     * `messages[]`, and the ones that don't still pay tokens for them.
     */
    @Getter
    @ToString
    @AllArgsConstructor
    public static class LLMMessageDTO {
        private String role;
        private String content;

        public static LLMMessageDTO of(MessageDTO m) {
            return new LLMMessageDTO(m.getRole().name(), m.getContent());
        }
    }

    /**
     * One request, in the OpenAI chat-completions dialect.
     *
     * That dialect is the point. Gemini's `/v1beta/openai/chat/completions`, OpenAI itself and
     * Ollama's own `/v1/chat/completions` all read this exact body, so choosing a provider is three
     * environment variables rather than a second code path — and {@link LLMResponseDTO} already
     * reads back whichever shape they answer in.
     *
     * It cost the Ollama-only `format` member, which sat at the top level and means nothing
     * anywhere else; {@link ResponseFormat} is where that request lives now. A deployment still
     * running Ollama must therefore point LLM_URL at `/v1/chat/completions` — `/api/chat` speaks
     * Ollama's own dialect and does not know `response_format`.
     */
    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LLMQueryDTO {
        private String model;
        private List<LLMMessageDTO> messages;
        /**
         * Always sent, and always false.
         *
         * A primitive rather than a Boolean, so the {@code NON_NULL} above does not suppress it. An
         * omitted `stream` is only ever a server's own default, and a default that turned true
         * would hand us a chunked body {@link LLMResponseDTO} cannot read.
         */
        private boolean stream;
        /**
         * The structured-output switch, for the callers that have to parse the answer.
         *
         * Left null it is not serialized at all: a chat turn wants prose, and a server that does
         * not implement the member answers 400 rather than ignoring it. Only the diary and the
         * report ask for it — see {@code ChatService.responseFormat}, which is where a deployment's
         * `format` setting becomes one of these.
         */
        @JsonProperty("response_format")
        private ResponseFormat responseFormat;

        public LLMQueryDTO(String model, List<LLMMessageDTO> messages, ResponseFormat responseFormat) {
            this(model, messages, false, responseFormat);
        }

        /**
         * `response_format`. Serializes as {@code {"type": "json_object"}} and nothing else.
         *
         * One shared constant, because that is the only value anything here has ever needed.
         * `json_schema` would mean carrying the diary's and the five reports' schemas in a shape
         * each provider spells differently, for a guarantee that is checked after the fact anyway:
         * {@code DiaryService.parse} and {@code ReportService.parse} exist because a small model
         * gets the shape wrong even when it was told not to.
         */
        @Getter
        @ToString
        @AllArgsConstructor
        public static class ResponseFormat {
            public static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");

            private String type;
        }
    }

    /**
     * The reply, in whichever shape the model server speaks.
     *
     * We used to deserialize straight into {@link MessageDTO}, which assumes a bare
     * `{role, content}` at the top level. No common server answers that way, so `content` came back
     * null and the turn produced silence — with no error to explain it.
     *
     * Two shapes are accepted, because the two servers we run against disagree:
     * <ul>
     *   <li>Ollama `/api/chat` → {@code {"message": {"role", "content"}}}</li>
     *   <li>OpenAI-compatible `/v1/chat/completions` → {@code {"choices": [{"message": {…}}]}}</li>
     * </ul>
     * Unknown members are ignored, so a server that adds fields (timings, token counts) still parses.
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LLMResponseDTO {
        @Getter
        @Setter
        @ToString
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private LLMMessageValue message;
            /** Some servers put the text here instead of under `message` (completions-style). */
            private String text;
        }

        @Getter
        @Setter
        @ToString
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class LLMMessageValue {
            private String role;
            private String content;
        }

        /** Ollama-style: the reply sits at the top level. */
        private LLMMessageValue message;
        /** OpenAI-style: the reply sits inside the first choice. */
        private List<Choice> choices;
        /** Last resort — a server that returns only the text. */
        private String response;

        /** The assistant's text, or null if the upstream answered in a shape we don't know. */
        public String firstContent() {
            if (message != null && message.getContent() != null) return message.getContent();

            if (choices != null && !choices.isEmpty()) {
                Choice first = choices.get(0);
                if (first != null) {
                    if (first.getMessage() != null && first.getMessage().getContent() != null) {
                        return first.getMessage().getContent();
                    }
                    if (first.getText() != null) return first.getText();
                }
            }

            return response;
        }
    }

    /**
     * One request to Cloud Speech-to-Text's synchronous {@code v1/speech:recognize}.
     *
     * <p>The recording travels inside the JSON as base64 rather than as a multipart file, which is
     * the whole of why this class exists: the dialect it sits beside is a whisper.cpp upload —
     * `file`, `temperature`, `response_format=text`, `language=auto` — and nothing about that shape
     * survives the move. Note in particular that `auto` does not: {@link RecognitionConfig#languageCode}
     * is required here and there is no value meaning "work it out".
     *
     * <p>60 seconds and 10 MB is the limit of this endpoint. Longer needs
     * {@code speech:longrunningrecognize} and an object in a bucket, which is a different endpoint, a
     * poll loop and a piece of infrastructure this application does not have.
     */
    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class STTQueryDTO {
        private RecognitionConfig config;
        private RecognitionAudio audio;

        @Getter
        @ToString
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class RecognitionConfig {
            /** LINEAR16, a constant in ChatService — it is our half of a two-repo contract. */
            private String encoding;
            /** 16000, from the same contract. */
            private Integer sampleRateHertz;
            /** Required. There is no "auto" the way the other dialect has one. */
            private String languageCode;
            /**
             * Blank leaves this off and Google uses its default, which is the right default: which
             * models exist differs by language, so a name ko-KR does not have is a 400 on every
             * single recording rather than a slightly worse transcript.
             */
            private String model;
            /**
             * A primitive, so the {@code NON_NULL} above does not suppress it, and always true.
             *
             * <p>The transcript is not read by a machine: it becomes a chat bubble and then a turn of
             * history replayed to the model. An unpunctuated Korean run-on degrades both, and the
             * flag costs nothing.
             */
            private boolean enableAutomaticPunctuation;
        }

        /**
         * The recording itself, base64.
         *
         * <p>{@code toString} is hand-written and redacts it, which is not tidiness: transcribe logs
         * its request the way complete does, and twenty seconds of speech is some 850 KB of base64 —
         * one log line per child per turn, holding the audio of what a child said. The same care
         * that keeps the model key off {@link LLMQueryDTO}, pointed the other way.
         */
        @Getter
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class RecognitionAudio {
            private String content;

            @Override
            public String toString() {
                return "RecognitionAudio(content="
                        + (content == null ? "null" : content.length() + " base64 chars") + ")";
            }
        }
    }

    /**
     * What Cloud Speech-to-Text answers with.
     *
     * <p>{@code results} is a <b>list of segments</b>, not one transcript. Google splits a recording
     * where it hears a pause, and each segment carries its own alternatives — so reading
     * {@code results[0]}, which is the obvious thing to write, silently returns the first sentence of
     * what a child said and drops the rest. {@link #transcript()} exists so that mistake cannot be
     * made twice.
     *
     * <p>And it is <b>absent</b> for silence: a recording of nothing comes back as <code>{}</code>.
     * That is not an error and must not become one — the child pressed the button and said nothing,
     * and the answer to that is to ask again, not to fail.
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class STTResponseDTO {
        @Getter
        @Setter
        @ToString
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Alternative {
            private String transcript;
            private Double confidence;
        }

        @Getter
        @Setter
        @ToString
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Result {
            /** Best first. We take one — there is nowhere in this application to show a second. */
            private List<Alternative> alternatives;
        }

        private List<Result> results;

        /**
         * Everything that was heard, in order, as one line — or "" when nothing was.
         *
         * <p>Each segment is trimmed before joining and the segments are rejoined with a single
         * space: Google leads continuation segments with a space of its own, so pasting the raw
         * strings together yields double spaces, and dropping the space entirely runs the last word
         * of one sentence into the first of the next.
         */
        public String transcript() {
            if (results == null || results.isEmpty()) return "";

            StringBuilder joined = new StringBuilder();
            for (Result result : results) {
                if (result == null || result.getAlternatives() == null || result.getAlternatives().isEmpty()) continue;

                Alternative best = result.getAlternatives().get(0);
                if (best == null || best.getTranscript() == null) continue;

                String text = best.getTranscript().trim();
                if (text.isEmpty()) continue;

                if (!joined.isEmpty()) joined.append(' ');
                joined.append(text);
            }

            return joined.toString();
        }
    }

    /**
     * One request to Cloud Text-to-Speech's {@code v1/text:synthesize}.
     *
     * <p>Three members are conspicuously absent, and each absence is a decision:
     * <ul>
     *   <li>{@code audioConfig.pitch} — a character's pitch belongs to voice conversion and only
     *       there. Sent here as well it would be applied twice on the path children actually hear
     *       (Google's semitones, then the converter's), and the answer would be a chipmunk.</li>
     *   <li>{@code voice.ssmlGender} — meaningless once {@code name} is given, and a contradiction
     *       between the two is a 400.</li>
     *   <li>{@code audioConfig.sampleRateHertz} — left off, the voice is rendered at its own rate,
     *       which is the best it sounds. LINEAR16 carries a WAV header, so everything downstream
     *       reads the rate out of the file rather than being told it.</li>
     * </ul>
     */
    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TTSQueryDTO {
        private Input input;
        private Voice voice;
        private AudioConfig audioConfig;

        @Getter
        @ToString
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Input {
            /** Plain text, never `ssml`: this reads back a model's prose, which is not markup. */
            private String text;
        }

        @Getter
        @ToString
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Voice {
            private String languageCode;
            /**
             * Null leaves the member off and Google picks the default voice for the language. That
             * works, and is not promised to keep picking the same one — a child's partner quietly
             * changing voice after a Google release is the kind of thing nobody files a bug about
             * and everybody notices. Name one per deployment.
             */
            private String name;
        }

        @Getter
        @ToString
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class AudioConfig {
            /** LINEAR16 — a WAV with a header on it, which is what everything downstream expects. */
            private String audioEncoding;
            /**
             * A primitive, like {@code stream} on {@link LLMQueryDTO}, so it is always written: 1.0
             * is Google's default anyway, and saying it out loud is what makes the two characters'
             * 1.05 and 0.95 legible in a log beside it.
             */
            private double speakingRate;
        }
    }

    /**
     * What Cloud Text-to-Speech answers with: the whole audio file, base64, in a JSON member.
     *
     * <p>{@code toString} redacts it for the same reason {@link STTQueryDTO.RecognitionAudio} does.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TTSResponseDTO {
        private String audioContent;

        /** The bytes, or null if the server answered with no audio in it. */
        public byte[] audio() {
            if (audioContent == null || audioContent.isBlank()) return null;

            // The MIME decoder rather than the basic one: it skips whitespace instead of throwing on
            // it, and a decoder that threw here would surface as a voice that failed rather than as
            // the formatting detail it is.
            return Base64.getMimeDecoder().decode(audioContent);
        }

        @Override
        public String toString() {
            return "TTSResponseDTO(audioContent="
                    + (audioContent == null ? "null" : audioContent.length() + " base64 chars") + ")";
        }
    }

    /**
     * One request to an OpenAI-dialect {@code /v1/audio/speech}.
     *
     * <p>The portable half of synthesis, and the reason the TTS dialect is named `openai` rather than
     * after any one server: OpenAI itself, openedai-speech, Kokoro-FastAPI and Speaches all read
     * this body. What it replaced — MeloTTS's own {@code {text, language, speaker, speed}} — was a
     * shape exactly one project spoke, so a deployment wanting MeloTTS now puts a small adapter in
     * front of it and keeps every other server as an option.
     *
     * <p>Unlike Google's, the answer to this is the audio itself rather than base64 inside JSON,
     * which is why there is no response type beside this one.
     */
    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpeechQueryDTO {
        /** Blank leaves it off, for a local server that serves one model and names none. */
        private String model;
        /** The text. OpenAI calls this `input`; Google calls the same thing `input.text`. */
        private String input;
        private String voice;
        /**
         * Always "wav". Pinned to three other things — `produces = "audio/wav"` on both controller
         * endpoints, the .wav name on the part handed to voice conversion, and the converter itself
         * — so moving it means moving all four together. The default is mp3, which would make the
         * declared content type a lie.
         */
        @JsonProperty("response_format")
        private String responseFormat;
        /** OpenAI's word for what Google calls speakingRate, on the same scale. */
        private double speed;
    }
}
