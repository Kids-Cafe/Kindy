package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.config.GoogleTokenProvider;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.mapper.IChatMapper;
import org.kidscafe.kindy.mapper.IChatMessageMapper;
import org.kidscafe.kindy.service.IChatService;
import org.kidscafe.kindy.service.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
class ChatService implements IChatService {
    @Value("${kindy.stt.url}")
    private String STT_URL;
    /**
     * Which dialect {@link #STT_URL} speaks — "openai" or "google".
     *
     * <p>A setting rather than something read off the hostname. The URL does carry the answer, but a
     * rule that infers it is a guess: a proxy, or any gateway not on a Google hostname, silently
     * selects the wrong builder and fails as a 400 from the far end rather than as a value somebody
     * can read. Unrecognised values fall back to openai — see {@link #isGoogle}.
     */
    @Value("${kindy.stt.dialect:openai}")
    private String STT_DIALECT;
    /** As {@link #LLM_KEY}: blank sends no Authorization header, which is what a local server wants. */
    @Value("${kindy.stt.key:}")
    private String STT_KEY;
    @Value("${kindy.stt.language:ko-KR}")
    private String STT_LANGUAGE;
    /** Blank leaves the member off the request; see ChatDTO.STTQueryDTO.RecognitionConfig. */
    @Value("${kindy.stt.model:}")
    private String STT_MODEL;
    @Value("${kindy.llm.url}")
    private String LLM_URL;
    @Value("${kindy.llm.model}")
    private String LLM_MODEL;
    /**
     * The bearer token for the model endpoint — Gemini's or OpenAI's key, blank for a local Ollama.
     *
     * <p>Blank does not mean "not configured" the way a blank URL does: whether a key is wanted is
     * the endpoint's business, not ours, so there is no NOT_AVAILABLE for the absence of one. What
     * blank means here is that no Authorization header is sent at all.
     *
     * <p>A {@code @Value} field rather than a constructor argument on purpose: {@link ChatService}
     * is built with null collaborators in its test to ask questions that never reach the network,
     * and one more constructor parameter is one more null every such test has to carry.
     */
    @Value("${kindy.llm.key:}")
    private String LLM_KEY;
    @Value("${kindy.llm.prompt:}")
    private String LLM_PROMPT;
    @Value("${kindy.tts.url}")
    private String TTS_URL;
    /** As {@link #STT_DIALECT}, for synthesis. */
    @Value("${kindy.tts.dialect:openai}")
    private String TTS_DIALECT;
    @Value("${kindy.tts.key:}")
    private String TTS_KEY;
    /** The OpenAI dialect names a model; Google's does not. Blank leaves it off. */
    @Value("${kindy.tts.model:}")
    private String TTS_MODEL;
    /** Google's dialect requires this; the OpenAI one has no language member and infers it. */
    @Value("${kindy.tts.language:ko-KR}")
    private String TTS_LANGUAGE;
    @Value("${kindy.tts.voice:}")
    private String TTS_VOICE;
    /**
     * Per-partner voices, exactly as {@link #STS_MODEL_KIO} works for voice models: blank means "use
     * the shared voice above", and the two characters then differ by speaking rate alone at this
     * stage.
     *
     * <p>These are the lever that matters for a deployment with no STS_URL, where nothing runs after
     * synthesis at all — two different voices are then the only thing telling Kio and Kina apart by
     * ear. A character's pitch is deliberately NOT applied here; it belongs to conversion, and
     * applying it in both places would transpose the same voice twice.
     */
    @Value("${kindy.tts.voice.kio:}")
    private String TTS_VOICE_KIO;
    @Value("${kindy.tts.voice.kina:}")
    private String TTS_VOICE_KINA;
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
     * The Google access token for the two speech endpoints, when either is set to that dialect.
     *
     * <p>A collaborator rather than a {@code @Value} like {@link #LLM_KEY} above, because unlike a key
     * this is a credential that expires: something has to hold the current one and re-mint it. It
     * costs the tests their fourth null and earns it — reaching this field at all in a service built
     * with nulls would be a NullPointerException, so the ServiceUnavailableException they assert also
     * proves no credential was read for a deployment that has no speech configured.
     */
    private final GoogleTokenProvider googleToken;

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

    /**
     * Whether a speech setting names Google's dialect rather than the OpenAI one.
     *
     * <p>Anything unrecognised — a typo, a blank, a value from a future release — is read as openai,
     * which is the portable dialect and the default. Falling back rather than refusing is on purpose:
     * a misspelt dialect should degrade to the shape most servers speak, not take speech down.
     */
    static boolean isGoogle(String dialect) {
        if (dialect == null || dialect.isBlank()) return false;

        String value = dialect.trim();
        if (value.equalsIgnoreCase("google")) return true;
        if (!value.equalsIgnoreCase("openai")) {
            log.warn("unrecognised speech dialect {}, reading it as openai", value);
        }

        return false;
    }

    /**
     * The recording format the browser and this endpoint have agreed on, stated as constants rather
     * than settings.
     *
     * <p>The browser does not record into a container and hand it over — it takes raw samples and
     * assembles the WAV itself, 16 kHz mono 16-bit LINEAR16, because that is the only thing this end
     * has ever accepted. Making these properties would let one repository be reconfigured away from
     * the other, and the symptom of disagreeing is not an error: Google decodes the bytes at whatever
     * rate it was told and returns a confident transcript of noise.
     *
     * <p>They are sent even though the WAV header carries both and the API treats them as optional
     * for a headered format. Sent, a mismatch with the header is an error rather than a guess — which
     * is the failure worth having.
     */
    private static final String STT_ENCODING = "LINEAR16";
    private static final int STT_SAMPLE_RATE = 16000;

    /**
     * What we ask for back from synthesis, in each dialect's spelling of the same thing.
     *
     * <p>A WAV with a header on it, and three separate things believe that: {@code produces =
     * "audio/wav"} on both controller endpoints, the .wav name on the part handed to voice
     * conversion, and the converter itself. Moving this means moving all four together.
     */
    private static final String TTS_ENCODING = "LINEAR16";
    private static final String SPEECH_FORMAT = "wav";

    /**
     * How much audio goes into one request, in bytes before base64.
     *
     * <p>{@code speech:recognize} is the synchronous endpoint: 60 seconds and 10 MB, inline. Base64
     * makes four bytes out of three, so 7 MB of recording is already 9.3 MB on the wire.
     *
     * <p>Nothing the app itself sends comes near it — the recorder stops at 20 seconds, which is
     * 640 KB at 16 kHz mono 16-bit. This is for everything else that can POST to the endpoint:
     * spring.servlet.multipart.max-file-size is 10 MB, and without this a 10 MB upload becomes a
     * 13 MB request, a 400 naming a limit rather than a file, and a bill for the attempt.
     *
     * <p>It is explicitly NOT a duration check and cannot be one — bytes say nothing about seconds
     * for a compressed format. For the format actually sent, 60 seconds is about 1.9 MB, so Google's
     * duration limit bites long before this does.
     */
    private static final int MAX_INLINE_AUDIO_BYTES = 7_000_000;

    /**
     * One authorized POST to a Google speech API, with its refusals mapped the way {@link #complete}
     * maps the model's.
     *
     * <p>The token is minted into a local before the request is touched, so "this deployment has no
     * Google credentials" is answered before anything is sent and reads as NOT_AVAILABLE rather than
     * as a call that failed.
     */
    private <T> T google(String url, String variable, Object body, Class<T> type) {
        // On the request, never on the RestClient bean. OAuthService is handed the same bean, so a
        // default Authorization header would send a cloud-platform token — which can spend money —
        // to Kakao, Naver and Google's own login endpoint. The rule LLM_KEY follows above, and it
        // matters more here: a leaked API key is a key, a leaked bearer is an hour of an account.
        String authorization = "Bearer " + googleToken.token();

        try {
            return restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization)
                    .body(body).retrieve().body(type);
        } catch (HttpClientErrorException e) {
            // 401 and 403 are the account, not the request — and there are three of them here where
            // the model had two:
            //   401                    the token was refused: a revoked key, a deleted account, clock skew.
            //   403 PERMISSION_DENIED  the service account may not call this API.
            //   403 SERVICE_DISABLED   the API was never enabled in the project.
            // All three are one person's job to fix, no retry helps, and no caller could have asked
            // differently — the same shape of problem as an unset STT_URL, so the same answer, and
            // DiaryService.generateAll stops on it rather than spending the week on a refusal.
            //
            // The body is logged deliberately: SERVICE_DISABLED carries the console URL that enables
            // the API, and that link is the entire fix. Nothing sensitive comes back on a refusal —
            // the recording is in the request, not the response.
            //
            // Everything else stays a failure worth retrying. 429 above all, which is a quota saying
            // "later" and would be a lie as NOT_AVAILABLE; and 400, which is a bug in what we sent —
            // a bad encoding, audio over a minute — and must not read as a feature turned off.
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.warn("Google refused our credentials for {}: {} {}", variable, status, e.getResponseBodyAsString());
                throw new ServiceUnavailableException(variable + " was refused by Google (" + status + ")");
            }

            throw e;
        }
    }

    /** The first of these that says anything, trimmed — or null, which leaves a member off. */
    static String voice(String own, String shared) {
        if (own != null && !own.isBlank()) return own.trim();
        if (shared != null && !shared.isBlank()) return shared.trim();

        return null;
    }

    /**
     * Synthesized speech as a file <b>with a name</b>.
     *
     * <p>The name is load-bearing and invisible. Google answers with base64 in a JSON member, so what
     * comes out of it is bytes and nothing else, and a plain ByteArrayResource returns null from
     * getFilename(). Spring's FormHttpMessageConverter asks a part exactly that question: a null name
     * means the multipart part is written with no {@code filename=} at all and, because the part's
     * content type is derived from the extension, as application/octet-stream. A voice converter that
     * opens uploads by their name gets a nameless octet-stream and refuses it.
     *
     * <p>This did not have to be thought about while the bytes arrived as an HTTP response, because
     * Spring names such a resource after the upstream's own Content-Disposition. There is no upstream
     * response to take a name from on the Google path.
     */
    static final class WavResource extends ByteArrayResource {
        WavResource(byte[] audio) {
            super(audio);
        }

        @Override
        public String getFilename() {
            return "speech.wav";
        }
    }

    @Override
    public List<ChatDTO> getList(long kindergartenId) throws Exception {
        log.debug("Calling getList for {}", kindergartenId);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);

        return chatMapper.selectList(pDTO);
    }

    @Override
    public List<ChatDTO> getList(String userId) throws Exception {
        log.debug("Calling getList for {}", userId);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setClient(userId);
        pDTO.setHost(userId);

        return chatMapper.selectListByUser(pDTO);
    }

    @Override
    public ChatDTO getInfo(long id) throws Exception {
        log.debug("Calling getInfo");

        ChatDTO pDTO = new ChatDTO();
        pDTO.setId(id);

        return chatMapper.select(pDTO);
    }

    @Override
    public ChatDTO getInfo(long kindergartenId, String host, String client) throws Exception {
        log.debug("Calling getInfo for {} between {} and {}", kindergartenId, host, client);

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setHost(host);
        pDTO.setClient(client);

        return chatMapper.selectByParticipants(pDTO);
    }

    @Override
    public int create(ChatDTO pDTO) throws Exception {
        log.debug("Calling create");

        return chatMapper.insert(pDTO);
    }

    @Override
    public ChatDTO ensure(long kindergartenId, String host, String client) throws Exception {
        log.debug("Calling ensure");

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
            log.debug("ensure lost a race, reusing the existing chat");
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
        log.debug("Calling delete");

        ChatDTO pDTO = new ChatDTO();
        pDTO.setId(id);

        return chatMapper.delete(pDTO);
    }

    private List<ChatDTO.MessageDTO> getMessages(ChatDTO.QueryDTO pDTO) throws Exception {
        log.debug("Calling getMessages");

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
        log.debug("Calling addMessage");

        return chatMessageMapper.insert(pDTO);
    }

    @Override
    public int appendMessage(ChatDTO.MessageDTO pDTO) throws Exception {
        log.debug("Calling appendMessage");

        return chatMessageMapper.insertNext(pDTO);
    }

    @Override
    public ChatDTO.MessageDTO appendMessageAndRead(ChatDTO.MessageDTO pDTO) throws Exception {
        log.debug("Calling appendMessageAndRead");

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
        log.debug("Calling transcribe");

        // Before anything is read, encoded or minted: a deployment with no speech spends no time and
        // touches no credential.
        String url = configured(STT_URL, "STT_URL");

        return isGoogle(STT_DIALECT) ? this.transcribeGoogle(url, resource)
                                     : this.transcribeOpenAi(url, resource);
    }

    /**
     * Transcription in the OpenAI dialect — {@code /v1/audio/transcriptions}, multipart.
     *
     * <p>Unchanged from when it was the only one there was, and deliberately so: this is the shape
     * whisper.cpp, faster-whisper, OpenAI itself, Groq and LM Studio all read, and it is what keeps
     * this application runnable with no cloud account at all.
     */
    private String transcribeOpenAi(String url, Resource resource) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("temperature", "0.0");
        parts.add("response_format", "text");
        parts.add("task", "transcribe");
        // `auto` is this dialect's own, and has no equivalent on the Google side, which requires a
        // language and cannot guess one. Left here rather than made to follow kindy.stt.language, so
        // that turning the dialect back does not also change what the recogniser is told.
        parts.add("language", "auto");

        RestClient.RequestBodySpec request = restClient.post().uri(url);
        if (STT_KEY != null && !STT_KEY.isBlank()) {
            request = request.header("Authorization", "Bearer " + STT_KEY.trim());
        }

        return request.body(parts).retrieve().body(String.class);
    }

    /** Transcription in Google's dialect — {@code speech:recognize}, base64 inside JSON. */
    private String transcribeGoogle(String url, Resource resource) throws Exception {
        byte[] audio = resource.getContentAsByteArray();
        if (audio.length > MAX_INLINE_AUDIO_BYTES) {
            // Not a ServiceUnavailableException: speech is configured and working, this caller sent
            // too much of it. The controller turns this into TRANSCRIPTION_FAILED, which is the true
            // answer, and Google is neither asked nor billed for a request it would refuse.
            throw new IllegalArgumentException("recording is " + audio.length
                    + " bytes, over the " + MAX_INLINE_AUDIO_BYTES + " this endpoint sends inline");
        }

        ChatDTO.STTQueryDTO qDTO = new ChatDTO.STTQueryDTO(
                new ChatDTO.STTQueryDTO.RecognitionConfig(
                        STT_ENCODING, STT_SAMPLE_RATE, STT_LANGUAGE,
                        (STT_MODEL == null || STT_MODEL.isBlank()) ? null : STT_MODEL.trim(),
                        true),
                new ChatDTO.STTQueryDTO.RecognitionAudio(Base64.getEncoder().encodeToString(audio)));

        // The recording is not in this line: RecognitionAudio.toString redacts it.
        log.debug(qDTO.toString());

        ChatDTO.STTResponseDTO result = this.google(url, "STT_URL", qDTO, ChatDTO.STTResponseDTO.class);

        // Silence answers "" — not null, and certainly not an exception. A recording with nothing in
        // it comes back as an empty object, and a child who pressed the button and said nothing has
        // made no mistake.
        //
        // And every segment, joined: Google splits at pauses, so the first result is the first
        // sentence of what was said rather than what was said.
        return result == null ? "" : result.transcript();
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
        log.debug("Calling getRecentMessages");

        return chatMessageMapper.selectRecent(new ChatDTO.QueryDTO(id, null, null, null, null, limit));
    }

    @Override
    public List<ChatDTO.DayDTO> getPartnerDays(String userId) throws Exception {
        log.debug("Calling getPartnerDays for {}", userId);

        return chatMessageMapper.selectPartnerDays(userId);
    }

    @Override
    public List<ChatDTO.MessageDTO> getPartnerDay(String userId, String date) throws Exception {
        log.debug("Calling getPartnerDay for {} on {}", userId, date);

        return chatMessageMapper.selectPartnerDay(userId, date);
    }

    /**
     * The `response_format` a caller's {@code format} setting asks for, or null for ordinary prose.
     *
     * <p>{@code kindy.llm.diary.format} and {@code kindy.llm.report.format} hold Ollama's word for
     * this, "json". They stay as they are and {@link #complete} keeps its signature — this is where
     * that word becomes the member Gemini, OpenAI and Ollama's /v1 endpoint all understand. Blank
     * still means "leave the member off entirely", which is how a deployment turns structured
     * output off for a server that does not implement it.
     *
     * <p>Anything else non-blank is read as "yes, JSON" rather than forwarded into {@code type}:
     * `json_object` is the only mode all three implement, and passing a stray value through would
     * be a 400 — a worse answer to a typo than doing the one thing it could have meant. Nothing is
     * lost by being generous, because the answer is pulled out with extractJson either way.
     */
    static ChatDTO.LLMQueryDTO.ResponseFormat responseFormat(String format) {
        if (format == null || format.isBlank()) return null;

        String value = format.trim();
        if (!value.equalsIgnoreCase("json") && !value.equalsIgnoreCase("json_object")) {
            log.warn("unrecognised LLM format {}, asking for json_object", value);
        }

        return ChatDTO.LLMQueryDTO.ResponseFormat.JSON_OBJECT;
    }

    @Override
    public String complete(List<ChatDTO.LLMMessageDTO> messages, String format) throws Exception {
        ChatDTO.LLMQueryDTO qDTO = new ChatDTO.LLMQueryDTO(LLM_MODEL, messages, responseFormat(format));

        // Metadata only. The messages are a child talking to their partner; the log is not
        // where that conversation belongs, and at 30 turns it would be dumped on every reply.
        log.debug("LLM request: model={} turns={} format={}", LLM_MODEL,
                messages == null ? 0 : messages.size(), format == null ? "none" : format);

        String url = configured(LLM_URL, "LLM_URL");

        RestClient.RequestBodySpec request = restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        // On the request, never on the RestClient bean. OAuthService is handed the same bean, so a
        // default Authorization header would send the model key to Kakao, Naver and Google as well
        // — and the key is deliberately not a member of LLMQueryDTO either, which is logged above.
        //
        // Blank means no header rather than an empty `Bearer `: a local Ollama has no notion of a
        // key, and a malformed credential is refused where an absent one is served. The trim is not
        // cosmetic — a key pasted out of a console carries a trailing newline, and `Bearer …\n` is
        // an invalid header value that fails looking exactly like a wrong key.
        if (LLM_KEY != null && !LLM_KEY.isBlank()) {
            request = request.header("Authorization", "Bearer " + LLM_KEY.trim());
        }

        ChatDTO.LLMResponseDTO result;
        try {
            result = request.body(qDTO).retrieve().body(ChatDTO.LLMResponseDTO.class);
        } catch (HttpClientErrorException e) {
            // 401 and 403 are the key, not the request: no retry fixes them, no caller could have
            // asked differently, and the person who has to act is whoever holds the deployment —
            // which is the same shape of problem as LLM_URL being unset. So it gets the same
            // answer, and DiaryService.generateAll stops on it rather than spending the rest of the
            // week's days on an identical refusal and returning an empty success.
            //
            // Everything else stays a GENERATION_FAILED worth retrying. 429 above all, which is a
            // quota saying "later"; and 400, which is a bug in what we sent rather than something a
            // deployment configured wrongly, and should not read as a feature that was turned off.
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.warn("LLM refused our credentials: {} (LLM_API_KEY is {})",
                        status, (LLM_KEY == null || LLM_KEY.isBlank()) ? "unset" : "set");
                throw new ServiceUnavailableException("LLM_API_KEY was rejected (" + status + ")");
            }

            throw e;
        }

        String content = result == null ? null : result.firstContent();
        if (content == null || content.isBlank()) {
            log.warn("LLM returned no usable content: {}", result);
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
        log.debug("Calling requestMessage as {}", partner);

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
        log.debug("Calling synthesize as {}", partner);

        String url = configured(TTS_URL, "TTS_URL");

        // Null is "nobody in particular" — the schedule announcer and anything else that speaks
        // without a character. It keeps the neutral pace speech has always had here. Both dialects
        // spell it differently (speakingRate, speed) on the same scale: 1.0 is the voice's own pace,
        // so the characters' 1.05 and 0.95 mean what they always meant.
        double speed = partner == null ? 1.0 : partner.getSpeed();

        return isGoogle(TTS_DIALECT) ? this.synthesizeGoogle(url, text, partner, speed)
                                     : this.synthesizeOpenAi(url, text, partner, speed);
    }

    /**
     * Synthesis in the OpenAI dialect — {@code /v1/audio/speech}, and the audio itself comes back.
     *
     * <p>The portable half, and the reason this dialect is named after a shape rather than a server:
     * OpenAI, openedai-speech, Kokoro-FastAPI and Speaches all read this body. MeloTTS, which this
     * endpoint used to speak to directly, is reached by putting a small adapter in front of it —
     * the cost of no longer carrying a body only one project understands.
     */
    private Resource synthesizeOpenAi(String url, String text, ChatDTO.Partner partner, double speed) {
        ChatDTO.SpeechQueryDTO qDTO = new ChatDTO.SpeechQueryDTO(
                (TTS_MODEL == null || TTS_MODEL.isBlank()) ? null : TTS_MODEL.trim(),
                text, this.voiceName(partner), SPEECH_FORMAT, speed);

        // The length, not the words: this is what is about to be read aloud to a child.
        log.debug("Speech request: model={} voice={} speed={} ({} chars)", TTS_MODEL,
                this.voiceName(partner), speed, text == null ? 0 : text.length());

        RestClient.RequestBodySpec request = restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (TTS_KEY != null && !TTS_KEY.isBlank()) {
            request = request.header("Authorization", "Bearer " + TTS_KEY.trim());
        }

        return request.body(qDTO).retrieve().body(Resource.class);
    }

    /** Synthesis in Google's dialect — {@code text:synthesize}, base64 inside JSON. */
    private Resource synthesizeGoogle(String url, String text, ChatDTO.Partner partner, double speed) {
        ChatDTO.TTSQueryDTO qDTO = new ChatDTO.TTSQueryDTO(
                new ChatDTO.TTSQueryDTO.Input(text),
                new ChatDTO.TTSQueryDTO.Voice(TTS_LANGUAGE, this.voiceName(partner)),
                new ChatDTO.TTSQueryDTO.AudioConfig(TTS_ENCODING, speed));

        // As above — the text being spoken stays out of the log.
        log.debug("Speech request: language={} voice={} rate={} ({} chars)", TTS_LANGUAGE,
                this.voiceName(partner), speed, text == null ? 0 : text.length());

        ChatDTO.TTSResponseDTO result = this.google(url, "TTS_URL", qDTO, ChatDTO.TTSResponseDTO.class);

        byte[] audio = result == null ? null : result.audio();
        if (audio == null || audio.length == 0) {
            // A 200 with nothing in it. Returning an empty Resource would be worse than failing: the
            // browser gets a zero-byte audio/wav, plays nothing, and reports nothing.
            log.warn("Cloud Text-to-Speech answered with no audio: {}", result);
            throw new IllegalStateException("Cloud Text-to-Speech returned no audio");
        }

        // LINEAR16 arrives with a WAV header already on it, so these bytes are a playable file and
        // not raw samples needing one written. Named, because the next hop reads the name.
        return new WavResource(audio);
    }

    /**
     * The voice configured for this partner, or the shared one, or none at all.
     *
     * <p>Deliberately shaped like {@link #voiceModel} and deliberately different in its last line:
     * voiceModel falls back to a possibly-blank STS_MODEL because a multipart field can be empty, but
     * a blank voice name in JSON is not "no preference" — it is a name that matches no voice. Null is
     * how a member is left off, so null is what "none configured" has to become.
     */
    private String voiceName(ChatDTO.Partner partner) {
        String configured = partner == ChatDTO.Partner.kina ? TTS_VOICE_KINA
                : partner == ChatDTO.Partner.kio ? TTS_VOICE_KIO
                : null;

        return voice(configured, TTS_VOICE);
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
        log.debug("Calling convert as {}", partner);

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
