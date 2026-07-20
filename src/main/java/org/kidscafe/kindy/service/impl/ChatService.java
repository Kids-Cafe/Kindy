package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.service.IChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService implements IChatService {
    @Value("${kindy.llm.url}")
    private String API_URL;
    @Value("${kindy.llm.model}")
    private String MODEL;

    private final RestClient restClient;

    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @Override
    public ChatDTO createChat() {
        return new ChatDTO(MODEL, new ArrayList<ChatDTO.ChatMessageDTO>(), false);
    }

    @Override
    public ChatDTO.ChatMessageDTO requestNextMessage(ChatDTO pDTO) {
        this.callLog("requestNextMessage");
        ChatDTO.ChatResultDTO result = restClient.post().uri(API_URL).contentType(MediaType.APPLICATION_JSON).body(pDTO).retrieve().body(ChatDTO.ChatResultDTO.class);
        if (result == null) return new ChatDTO.ChatMessageDTO(ChatDTO.Role.assistant, "");
        pDTO.messages().add(result.message());
        return result.message();
    }
}
