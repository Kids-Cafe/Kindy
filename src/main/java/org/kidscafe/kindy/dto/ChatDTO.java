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
