package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.mapper.IChatMapper;
import org.kidscafe.kindy.mapper.IChatMessageMapper;
import org.kidscafe.kindy.service.IChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
class ChatService implements IChatService {
    @Value("${kindy.transcription.url}")
    private String API_URL;

    private final RestClient restClient;
    private final IChatMapper chatMapper;
    private final IChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatDTO> getList(long kindergartenId) throws Exception {
        log.info("Calling getList for {}", kindergartenId);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);

        return chatMapper.selectList(pDTO);
    }

    @Override
    public List<ChatDTO> getList(String userId) throws Exception {
        log.info("Calling getList for {}", userId);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setClient(userId);
        pDTO.setHost(userId);

        return chatMapper.selectListByUser(pDTO);
    }

    @Override
    public ChatDTO getInfo(long id) throws Exception {
        log.info("Calling getInfo");

        ChatDTO pDTO = new ChatDTO();
        pDTO.setId(id);

        return chatMapper.select(pDTO);
    }

    @Override
    public int create(ChatDTO pDTO) throws Exception {
        log.info("Calling create");

        return chatMapper.insert(pDTO);
    }

    @Override
    public int create(long kindergartenId, String host, String client) throws Exception {
        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setHost(host);
        pDTO.setClient(client);

        return this.create(pDTO);
    }

    @Override
    public int delete(long id) throws Exception {
        log.info("Calling delete");

        ChatDTO pDTO = new ChatDTO();
        pDTO.setId(id);

        return chatMapper.delete(pDTO);
    }

    private List<ChatDTO.MessageDTO> getMessages(ChatDTO.QueryDTO pDTO) throws Exception {
        log.info("Calling getMessages");

        return chatMessageMapper.selectList(pDTO);
    }

    @Override
    public List<ChatDTO.MessageDTO> getMessages(long id) throws Exception {
        return this.getMessages(new ChatDTO.QueryDTO(id, null, null, null, null, null));
    }

    @Override
    public List<ChatDTO.MessageDTO> getMessages(long id, int start, int end) throws Exception {
        return this.getMessages(new ChatDTO.QueryDTO(id, start, end, null, null, null));
    }

    @Override
    public List<ChatDTO.MessageDTO> getMessages(long id, int num) throws Exception {
        return this.getMessages(id, num, false);
    }

    @Override
    public List<ChatDTO.MessageDTO> getMessages(long id, int num, boolean reversed) throws Exception {
        return this.getMessages(new ChatDTO.QueryDTO(id, reversed ? null : num, reversed ? num : null, null, null, 50));
    }

    @Override
    public List<ChatDTO.MessageDTO> getMessages(long id, long timestamp, int num) throws Exception {
        return this.getMessages(new ChatDTO.QueryDTO(id, null, null, null, timestamp, num));
    }

    @Override
    public int addMessage(ChatDTO.MessageDTO pDTO) throws Exception {
        log.info("Calling addMessage");

        return chatMessageMapper.insert(pDTO);
    }

    @Override
    public String transcribe(Resource resource) throws Exception {
        log.info("Calling transcribe");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("temperature", "0.0");
        parts.add("response_format", "text");
        parts.add("task", "transcribe");
        parts.add("language", "auto");
        return restClient.post().uri(API_URL).body(parts).retrieve().body(String.class);
    }

    @Override
    public Resource synthesize(String text) throws Exception {
        log.info("Calling synthesize");

        // TODO

        return null;
    }

    @Override
    public Resource convert(Resource resource) throws Exception {
        log.info("Calling convert");

        // TODO

        return null;
    }
}
