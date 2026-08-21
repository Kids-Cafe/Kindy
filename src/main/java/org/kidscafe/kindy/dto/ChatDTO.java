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
        kio("키오", "당신은 씩씩하고 에너지가 넘칩니다. 호기심이 많아 아이의 이야기에 신나게 반응하고, 아이가 힘들어할 때는 용기를 북돋아 줍니다. 말투는 활기차고 밝습니다."),
        kina("키나", "당신은 따뜻하고 섬세합니다. 아이의 마음을 먼저 헤아려 기쁠 때 함께 기뻐하고 속상할 때는 조용히 곁에 있어 줍니다. 말투는 부드럽고 다정합니다.");

        private final String label;
        private final String persona;

        Partner(String label, String persona) {
            this.label = label;
            this.persona = persona;
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

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageDTO {
        public enum Type {
            TEXT,
            FOOD,
            HEALTH,
            FRIEND,
            PERSONALITY,
            STUDY
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
    public static class LLMQueryDTO {
        private String model;
        private List<LLMMessageDTO> messages;
        private boolean stream;
        public LLMQueryDTO(String model, List<LLMMessageDTO> messages) {
            this(model, messages, false);
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
