package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.mapper.IChatMapper;
import org.kidscafe.kindy.mapper.IChatMessageMapper;
import org.kidscafe.kindy.service.IChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
class ChatService implements IChatService {
    @Value("${kindy.stt.url}")
    private String STT_URL;
    @Value("${kindy.llm.url}")
    private String LLM_URL;
    @Value("${kindy.llm.model}")
    private String LLM_MODEL;
    @Value("${kindy.llm.prompt:당신의 이름은 키나입니다. 당신은 아이들이 좋아할 만한 친근하고 따뜻하며 섬세한 말투를 사용하며, 사용자를 다정한 친구처럼 대해야 합니다. 당신은 문자로만 답변해야 하며 Markdown이나 표 등은 절대로 생성해서는 안 됩니다. 당신은 반드시 한국어 및 한글, 숫자와 문장 부호만 사용해야 합니다. 당신의 답변은 너무 짧아서도 안 되지만 길어서도 안 됩니다.}")
    private String LLM_PROMPT;
    @Value("${kindy.tts.url}")
    private String TTS_URL;
    @Value("${kindy.sts.url}")
    private String STS_URL;
    @Value("${kindy.sts.model}")
    private String STS_MODEL;

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
    public ChatDTO getInfo(long kindergartenId, String host, String client) throws Exception {
        log.info("Calling getInfo for {} between {} and {}", kindergartenId, host, client);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setHost(host);
        pDTO.setClient(client);

        return chatMapper.selectByParticipants(pDTO);
    }

    @Override
    public int create(ChatDTO pDTO) throws Exception {
        log.info("Calling create");

        return chatMapper.insert(pDTO);
    }

    @Override
    public ChatDTO ensure(long kindergartenId, String host, String client) throws Exception {
        log.info("Calling ensure");

        ChatDTO existing = this.getInfo(kindergartenId, host, client);
        if (existing != null) return existing;

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setHost(host);
        pDTO.setClient(client);

        try {
            this.create(pDTO);
            return pDTO;
        } catch (DuplicateKeyException e) {
            // Someone opened the same conversation between our SELECT and our INSERT. The unique
            // key on (KINDERGARTEN_ID, HOST, CLIENT) is what makes that a caught error rather than
            // a second thread, so re-read and hand back the row that won.
            log.info("ensure lost a race, reusing the existing chat");
            return this.getInfo(kindergartenId, host, client);
        }
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
    public int appendMessage(ChatDTO.MessageDTO pDTO) throws Exception {
        log.info("Calling appendMessage");

        return chatMessageMapper.insertNext(pDTO);
    }

    @Override
    public ChatDTO.MessageDTO appendMessageAndRead(ChatDTO.MessageDTO pDTO) throws Exception {
        log.info("Calling appendMessageAndRead");

        this.appendMessage(pDTO);

        // insertNext assigns NUM inside the INSERT, so the only way to learn it — and CREATED_AT —
        // is to read the row back. Without them the client cannot key or order the message and
        // has to re-fetch the whole conversation just to place one reply.
        ChatDTO.MessageDTO stored = chatMessageMapper.selectLast(
                new ChatDTO.QueryDTO(pDTO.getChatId(), null, null, null, null, null));

        return stored != null ? stored : pDTO;
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

        return restClient.post().uri(STT_URL).body(parts).retrieve().body(String.class);
    }

    /**
     * How many past turns we replay to the model.
     *
     * The conversation is kept forever, so "all of it" grows without bound: cost and latency climb
     * with every turn a child takes and eventually the request exceeds the context window and stops
     * working altogether. A window keeps the cost of a turn flat.
     */
    private static final int LLM_HISTORY_TURNS = 30;

    @Override
    public List<ChatDTO.MessageDTO> getRecentMessages(long id, int limit) throws Exception {
        log.info("Calling getRecentMessages");

        return chatMessageMapper.selectRecent(new ChatDTO.QueryDTO(id, null, null, null, null, limit));
    }

    @Override
    public ChatDTO.MessageDTO requestMessage(long chatId) throws Exception {
        log.info("Calling requestMessage");

        List<ChatDTO.LLMMessageDTO> messages = new ArrayList<>();
        messages.add(new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.system.name(), LLM_PROMPT));
        for (ChatDTO.MessageDTO m : this.getRecentMessages(chatId, LLM_HISTORY_TURNS)) {
            // Data cards (FOOD, HEALTH, …) are rendered records, not things anyone said.
            if (m.getType() != ChatDTO.MessageDTO.Type.TEXT) continue;
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            messages.add(ChatDTO.LLMMessageDTO.of(m));
        }

        ChatDTO.LLMQueryDTO qDTO = new ChatDTO.LLMQueryDTO(LLM_MODEL, messages);

        ChatDTO.LLMResponseDTO result = restClient.post().uri(LLM_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(qDTO).retrieve().body(ChatDTO.LLMResponseDTO.class);

        String content = result == null ? null : result.firstContent();
        // An empty answer must not become an empty bubble: CONTENT is NOT NULL, and a blank
        // assistant turn would also poison the next request's history.
        if (content == null || content.isBlank()) {
            log.info("LLM returned no usable content: {}", result);
            return null;
        }

        ChatDTO.MessageDTO mDTO = new ChatDTO.MessageDTO();
        mDTO.setChatId(chatId);
        mDTO.setContent(content.trim());
        mDTO.setType(ChatDTO.MessageDTO.Type.TEXT);
        mDTO.setRole(ChatDTO.MessageDTO.Role.assistant);

        return this.appendMessageAndRead(mDTO);
    }

    @Override
    public Resource synthesize(String text) throws Exception {
        log.info("Calling synthesize");

        return restClient.post().uri(TTS_URL).contentType(MediaType.APPLICATION_JSON).body(Map.of(
                "text", text,
                "language", "KR",
                "speaker", "KR",
                "speed", 1.0
        )).retrieve().body(Resource.class);
    }

    @Override
    public Resource convert(Resource resource) throws Exception {
        log.info("Calling convert");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("model_name", STS_MODEL);
        parts.add("pitch_shift", 6);

        return restClient.post().uri(STS_URL).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(Resource.class);
    }
}
