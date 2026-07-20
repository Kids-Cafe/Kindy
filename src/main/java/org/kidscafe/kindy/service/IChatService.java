package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ChatDTO;

public interface IChatService {
    ChatDTO createChat();
    ChatDTO.ChatMessageDTO requestNextMessage(ChatDTO pDTO);
}
