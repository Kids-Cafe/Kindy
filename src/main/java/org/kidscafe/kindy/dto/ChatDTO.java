package org.kidscafe.kindy.dto;

import java.util.List;

public record ChatDTO(String model, List<ChatMessageDTO> messages, boolean stream) {
    public static record ChatMessageDTO(Role role, String content) {}
    public static record ChatResultDTO(ChatMessageDTO message) {}
    public static enum Role {
        user,
        assistant
    }
}
