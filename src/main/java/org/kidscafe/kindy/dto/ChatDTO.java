package org.kidscafe.kindy.dto;

import java.util.List;

public record ChatDTO(String model, List<ChatMessageDTO> messages, boolean stream) {
    public record ChatMessageDTO(Role role, String content) {}
    public record ChatResultDTO(ChatMessageDTO message) {}
    public enum Role {
        user,
        assistant
    }
}
