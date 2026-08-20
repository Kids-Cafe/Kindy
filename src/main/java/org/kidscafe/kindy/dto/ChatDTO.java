package org.kidscafe.kindy.dto;

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

    @Getter
    @ToString
    @AllArgsConstructor
    public static class LLMQueryDTO {
        private String model;
        private List<MessageDTO> messages;
        private boolean stream;
        public LLMQueryDTO(String model, List<MessageDTO> messages) {
            this(model, messages, false);
        }
    }
}
