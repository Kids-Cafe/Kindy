package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.mapper.IChatMapper;
import org.kidscafe.kindy.mapper.IChatMessageMapper;
import org.kidscafe.kindy.service.IChatService;
import org.kidscafe.kindy.service.ServiceUnavailableException;
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
    @Value("${kindy.llm.prompt:}")
    private String LLM_PROMPT;
    @Value("${kindy.tts.url}")
    private String TTS_URL;
    @Value("${kindy.sts.url}")
    private String STS_URL;
    @Value("${kindy.sts.model}")
    private String STS_MODEL;
    /**
     * Per-partner voice models, when a deployment has trained one for each character.
     *
     * Blank — which is the default, and what `${STS_MODEL_KIO:}` leaves behind when the variable is
     * unset — means "use the shared model": the two partners then still differ by speed and pitch
     * (see {@link ChatDTO.Partner}) rather than sounding identical.
     */
    @Value("${kindy.sts.model.kio:}")
    private String STS_MODEL_KIO;
    @Value("${kindy.sts.model.kina:}")
    private String STS_MODEL_KINA;

    private final RestClient restClient;
    private final IChatMapper chatMapper;
    private final IChatMessageMapper chatMessageMapper;

    /**
     * The URL of one of the speech/language services, or a refusal if this deployment has none.
     *
     * <p>Every one of them is declared {@code ${VAR:}} in application.properties, so an unset
     * variable leaves the property blank rather than absent and the application starts regardless.
     * The cost of that is this check: {@code .uri("")} has no base URL to resolve against and
     * throws about a URI not being absolute, several frames from anything that names the setting.
     *
     * <p>Checked here, at the one point every outbound call passes through, rather than at startup:
     * a missing model server is not a reason to refuse to boot when the rest of the application —
     * signing in, classes, photos, notices — works without it.
     */
    static String configured(String url, String variable) {
        if (url == null || url.isBlank())
            throw new ServiceUnavailableException(variable + " is not configured");

        return url;
    }

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

        String url = configured(STT_URL, "STT_URL");

        return restClient.post().uri(url).body(parts).retrieve().body(String.class);
    }

    /**
     * How many past turns we replay to the model.
     *
     * The conversation is kept forever, so "all of it" grows without bound: cost and latency climb
     * with every turn a child takes and eventually the request exceeds the context window and stops
     * working altogether. A window keeps the cost of a turn flat.
     */
    private static final int LLM_HISTORY_TURNS = 30;

    /**
     * The system prompt, with the partner's own two lines left as placeholders.
     *
     * `kindy.llm.prompt` overrides it, and an override may use the same placeholders — but it is
     * wired as `${LLM_PROMPT:}` in application.properties, so an unset environment variable makes
     * the property blank rather than absent, and a `@Value` default would never be reached. Hence
     * the blank check in {@link #systemPrompt} instead.
     */
    private static final String DEFAULT_LLM_PROMPT = "당신의 이름은 {name}입니다. {persona} 당신은 아이들이 좋아할 만한 친근하고 따뜻하며 섬세한 말투를 사용하며, 사용자를 다정한 친구처럼 대해야 합니다. 당신은 문자로만 답변해야 하며 Markdown이나 표 등은 절대로 생성해서는 안 됩니다. 당신은 반드시 한국어 및 한글, 숫자와 문장 부호만 사용해야 합니다. 당신의 답변은 너무 짧아서도 안 되지만 길어서도 안 됩니다.";

    /**
     * The system prompt for one partner.
     *
     * Only the prompt changes — the model, the history and the voice are shared — so the character
     * a child picked shows up as how the assistant talks about and behaves as itself. Placeholders
     * are always substituted, including `{username}`, which older prompts carry: an unresolved
     * `{…}` reaching the model reads as an instruction we did not mean to give.
     */
    private String systemPrompt(ChatDTO.Partner partner) {
        String template = (LLM_PROMPT == null || LLM_PROMPT.isBlank()) ? DEFAULT_LLM_PROMPT : LLM_PROMPT;

        return template
                .replace("{name}", partner.getLabel())
                .replace("{persona}", partner.getPersona())
                .replace("{username}", "친구");
    }

    @Override
    public List<ChatDTO.MessageDTO> getRecentMessages(long id, int limit) throws Exception {
        log.info("Calling getRecentMessages");

        return chatMessageMapper.selectRecent(new ChatDTO.QueryDTO(id, null, null, null, null, limit));
    }

    @Override
    public List<ChatDTO.DayDTO> getPartnerDays(String userId) throws Exception {
        log.info("Calling getPartnerDays for {}", userId);

        return chatMessageMapper.selectPartnerDays(userId);
    }

    @Override
    public List<ChatDTO.MessageDTO> getPartnerDay(String userId, String date) throws Exception {
        log.info("Calling getPartnerDay for {} on {}", userId, date);

        return chatMessageMapper.selectPartnerDay(userId, date);
    }

    @Override
    public String complete(List<ChatDTO.LLMMessageDTO> messages, String format) throws Exception {
        ChatDTO.LLMQueryDTO qDTO = new ChatDTO.LLMQueryDTO(LLM_MODEL, messages, format);

        log.info(qDTO.toString());

        String url = configured(LLM_URL, "LLM_URL");

        ChatDTO.LLMResponseDTO result = restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(qDTO).retrieve().body(ChatDTO.LLMResponseDTO.class);

        String content = result == null ? null : result.firstContent();
        if (content == null || content.isBlank()) {
            log.info("LLM returned no usable content: {}", result);
            return null;
        }

        return content.trim();
    }

    @Override
    public ChatDTO.MessageDTO requestMessage(long chatId) throws Exception {
        return this.requestMessage(chatId, (String) null);
    }

    @Override
    public ChatDTO.MessageDTO requestMessage(long chatId, String partner) throws Exception {
        return this.requestMessage(chatId, ChatDTO.Partner.of(partner));
    }

    @Override
    public ChatDTO.MessageDTO requestMessage(long chatId, ChatDTO.Partner partner) throws Exception {
        log.info("Calling requestMessage as {}", partner);

        List<ChatDTO.LLMMessageDTO> messages = new ArrayList<>();
        messages.add(new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.system.name(), this.systemPrompt(partner)));
        for (ChatDTO.MessageDTO m : this.getRecentMessages(chatId, LLM_HISTORY_TURNS)) {
            // Data cards (FOOD, HEALTH, …) are rendered records, not things anyone said.
            if (m.getType() != ChatDTO.MessageDTO.Type.TEXT) continue;
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            messages.add(ChatDTO.LLMMessageDTO.of(m));
        }

        // An empty answer must not become an empty bubble: CONTENT is NOT NULL, and a blank
        // assistant turn would also poison the next request's history.
        String content = this.complete(messages, null);
        if (content == null) return null;

        ChatDTO.MessageDTO mDTO = new ChatDTO.MessageDTO();
        mDTO.setChatId(chatId);
        mDTO.setContent(content);
        mDTO.setType(ChatDTO.MessageDTO.Type.TEXT);
        mDTO.setRole(ChatDTO.MessageDTO.Role.assistant);
        // AUTHOR stays null: nobody wrote this. Naming either side of the chat as its author would
        // credit a person with the model's words.

        return this.appendMessageAndRead(mDTO);
    }

    @Override
    public Resource synthesize(String text) throws Exception {
        return this.synthesize(text, (ChatDTO.Partner) null);
    }

    @Override
    public Resource synthesize(String text, String partner) throws Exception {
        return this.synthesize(text, voicePartner(partner));
    }

    /**
     * Unlike the prompt, speech has a neutral setting, so an absent name stays absent instead of
     * becoming the default character: a caller that never had a character (the schedule announcer)
     * should not start sounding like Kio because it left the parameter off.
     */
    private static ChatDTO.Partner voicePartner(String value) {
        return value == null || value.isBlank() ? null : ChatDTO.Partner.of(value);
    }

    @Override
    public Resource synthesize(String text, ChatDTO.Partner partner) throws Exception {
        log.info("Calling synthesize as {}", partner);

        // Null is "nobody in particular" — the schedule announcer and anything else that speaks
        // without a character. It keeps the neutral speed the endpoint has always used.
        double speed = partner == null ? 1.0 : partner.getSpeed();

        String url = configured(TTS_URL, "TTS_URL");

        return restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(Map.of(
                "text", text,
                "language", "KR",
                "speaker", "KR",
                "speed", speed
        )).retrieve().body(Resource.class);
    }

    /** The voice model trained for this partner, or the shared one when none is configured. */
    private String voiceModel(ChatDTO.Partner partner) {
        String configured = partner == ChatDTO.Partner.kina ? STS_MODEL_KINA
                : partner == ChatDTO.Partner.kio ? STS_MODEL_KIO
                : null;

        return configured == null || configured.isBlank() ? STS_MODEL : configured.trim();
    }

    @Override
    public Resource convert(Resource resource) throws Exception {
        return this.convert(resource, (ChatDTO.Partner) null);
    }

    @Override
    public Resource convert(Resource resource, String partner) throws Exception {
        return this.convert(resource, voicePartner(partner));
    }

    @Override
    public Resource convert(Resource resource, ChatDTO.Partner partner) throws Exception {
        log.info("Calling convert as {}", partner);

        // 6 is the shift this endpoint used before the characters had voices of their own; it stays
        // the answer for callers that don't name one.
        int pitchShift = partner == null ? 6 : partner.getPitchShift();

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("model_name", this.voiceModel(partner));
        parts.add("pitch_shift", pitchShift);

        String url = configured(STS_URL, "STS_URL");

        return restClient.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(Resource.class);
    }
}
