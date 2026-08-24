package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LLMQueryDTO {
        private String model;
        private List<LLMMessageDTO> messages;
        private boolean stream;
        /**
         * Ollama's structured-output switch ("json"), for callers that need to parse the answer.
         *
         * Left null it is not serialized at all, because it is not a member every server knows:
         * an OpenAI-compatible endpoint rejects the request outright on an unknown field, and the
         * chat turns must keep working against both. Only the diary sets it.
         */
        private String format;
        public LLMQueryDTO(String model, List<LLMMessageDTO> messages) {
            this(model, messages, false, null);
        }
        public LLMQueryDTO(String model, List<LLMMessageDTO> messages, String format) {
            this(model, messages, false, format);
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
}
