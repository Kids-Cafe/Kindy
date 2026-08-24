package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.service.IChatService;
import org.kidscafe.kindy.service.ServiceUnavailableException;
import org.kidscafe.kindy.service.IDiaryService;
import org.kidscafe.kindy.service.IUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
class DiaryService implements IDiaryService {
    private final IChatService chatService;
    private final IUserService userService;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * What a day needs before it is worth a diary.
     *
     * Without a floor every "안녕" becomes an entry, and the model — given three words — fills the
     * rest in from nowhere, which is exactly the failure a child's parent would read as a lie.
     * Both bounds matter: the total keeps one-line days out, and the child's own count keeps out a
     * day where the partner did all the talking.
     */
    private static final int MIN_MESSAGES = 4;
    private static final int MIN_USER_MESSAGES = 2;

    /**
     * How much of one day we hand the model.
     *
     * A day is bounded in a way a whole conversation isn't, but a talkative afternoon can still
     * outgrow a small model's context. The tail is kept rather than the head: the end of the day
     * is what a diary written that evening would be about.
     */
    private static final int MAX_DAY_MESSAGES = 80;

    /** Column width of T_DIARY.TITLE. SUMMARY and CONTENT are TEXT and need no bound. */
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_TAGS = 5;

    @Value("${kindy.llm.diary.prompt:}")
    private String LLM_DIARY_PROMPT;
    @Value("${kindy.llm.diary.format:json}")
    private String LLM_DIARY_FORMAT;

    /**
     * The system prompt for writing one day.
     *
     * `kindy.llm.diary.prompt` overrides it. As with the chat prompt it is wired as
     * `${LLM_DIARY_PROMPT:}`, so an unset environment variable leaves the property blank rather
     * than absent and a `@Value` default would never be reached — hence the blank check below.
     *
     * Three things it has to get right, in order of how badly they fail:
     * 1. Only the child's own turns are evidence. The partner's replies are there for context —
     *    they are what the child was answering — but a diary that reports what the AI said as
     *    something that happened is a fabricated record of a real child's day.
     * 2. JSON and nothing else. A 3B model will otherwise wrap it in a code fence or preface it
     *    with "물론이죠!"; {@link #extractJson} cleans up after that, but asking plainly costs
     *    nothing and fails less often.
     * 3. First person, in a small child's Korean. The entry is presented as the child's own diary.
     */
    private static final String DEFAULT_DIARY_PROMPT = """
            당신은 유치원에 다니는 아이의 하루 일기를 대신 써 주는 작가입니다.
            아래에 {date}에 아이가 AI 친구와 나눈 대화가 순서대로 주어집니다. '아이:'로 시작하는 줄은 아이가 한 말이고, '친구:'로 시작하는 줄은 AI 친구가 한 말입니다.
            일기는 오직 아이가 한 말에서 드러난 사실과 감정만으로 써야 합니다. AI 친구가 한 말은 아이가 무엇에 답한 것인지 알기 위한 참고일 뿐이며, 친구가 한 말이나 대화에 없는 일을 지어내서 쓰면 절대 안 됩니다.
            아이가 직접 쓴 것처럼 '나는'으로 시작하는 1인칭으로, 유치원 아이가 쓸 법한 짧고 쉬운 한국어 문장으로 쓰세요.
            당신은 반드시 아래 형태의 JSON 객체 하나만 출력해야 합니다. 설명, 인사말, Markdown, 코드 블록을 앞뒤에 붙여서는 절대 안 됩니다.
            {"mood": "happy", "title": "제목", "summary": "요약", "text": "일기 본문", "tags": ["태그1", "태그2"]}
            각 항목의 규칙입니다.
            - mood: 그날 아이의 기분. 반드시 happy(기쁨), excited(신남), calm(차분함), sad(속상함), upset(힘듦) 중 하나여야 합니다.
            - title: 그날을 한마디로 나타내는 20자 이내의 제목.
            - summary: 하루를 한두 문장으로 줄인 요약.
            - text: 세 문장에서 다섯 문장 사이의 일기 본문.
            - tags: 그날의 일을 나타내는 한 단어짜리 태그 3개 이내의 배열. '#'은 붙이지 마세요.
            모든 문장은 한국어로 쓰고, 이모지와 표는 쓰지 마세요.""";

    private String systemPrompt(String date) {
        String template = (LLM_DIARY_PROMPT == null || LLM_DIARY_PROMPT.isBlank())
                ? DEFAULT_DIARY_PROMPT : LLM_DIARY_PROMPT;

        return template.replace("{date}", date);
    }

    /** Null when the deployment turned structured output off — a server that has no `format`. */
    private String format() {
        return LLM_DIARY_FORMAT == null || LLM_DIARY_FORMAT.isBlank() ? null : LLM_DIARY_FORMAT.trim();
    }

    @Override
    public List<DiaryDTO> generateAll(String userId) throws Exception {
        log.info("Calling generateAll for {}", userId);

        List<DiaryDTO> written = new ArrayList<>();
        // Counted as attempts, not successes: the cap is there to bound how many model calls one
        // request makes, and a day that failed cost us that call just the same.
        int attempted = 0;
        int skippedForCap = 0;

        for (ChatDTO.DayDTO day : chatService.getPartnerDays(userId)) {
            if (!this.hasEnough(day)) continue;

            // A day already written is skipped unless the child has said more since — which is
            // the normal state of today, whose conversation is not over when the diary screen
            // first opens. isStale() is what keeps a hand-edited entry out of that.
            DiaryDTO existing = userService.getDiaryInfo(userId, day.getDate());
            if (existing != null && !isStale(existing, day.getLastMessageAt())) continue;

            if (attempted >= MAX_DAY_ATTEMPTS) {
                skippedForCap++;
                continue;
            }
            attempted++;

            // One bad day must not cost the caller the other six — the model is the fragile part
            // here and it fails per-request.
            try {
                DiaryDTO entry = this.generate(userId, day.getDate(), false);
                if (entry != null) written.add(entry);
            } catch (ServiceUnavailableException e) {
                // Not a bad day — a deployment with no model. Every remaining day would fail the
                // same way, so there is nothing to salvage by carrying on, and swallowing it would
                // report GENERATE_COMPLETE with an empty list: indistinguishable from a week with
                // nothing worth writing about.
                throw e;
            } catch (Exception e) {
                log.info("diary generation failed for {} on {}: {}", userId, day.getDate(), e.toString());
            }
        }

        if (skippedForCap > 0) {
            log.info("{} more day(s) left for the next run for {}", skippedForCap, userId);
        }

        return written;
    }

    private boolean hasEnough(ChatDTO.DayDTO day) {
        return day.getTotalMessages() >= MIN_MESSAGES && day.getUserMessages() >= MIN_USER_MESSAGES;
    }

    /**
     * How much new conversation it takes before a written day is worth writing again.
     *
     * Today's diary goes stale as the child keeps talking, and rewriting it is the point. But
     * every rewrite is a model call, and one more "응" is not a different day — without a floor,
     * every visit to the diary screen after every single turn would spend one. Two is the smallest
     * real exchange: the child said something and their partner answered.
     */
    private static final int MIN_NEW_MESSAGES = 2;

    /**
     * Whether an entry has fallen behind the conversation, cheaply — from the day's totals alone.
     *
     * A null SOURCE_AT means a person wrote or edited this day ({@code user/diary/create} and
     * {@code user/diary/modify} both store null), and those are never stale, never rewritten, no
     * matter how much is said afterwards. Overwriting somebody's own words is the one failure here
     * that cannot be undone.
     *
     * Otherwise: stale when the day's newest message is newer than the newest one the entry saw.
     * This is the pre-filter {@link #generateAll} uses, and it is deliberately looser than
     * {@link #isStale(DiaryDTO, List)} — it cannot count how many turns are new, only that some
     * are. {@link #generate} asks again with the messages in hand before spending a model call.
     * <p>
     * Package-private for {@code DiaryServiceTest}: this pair of rules is what stands between a
     * child's own words and a model that would happily replace them.
     */
    static boolean isStale(DiaryDTO entry, long lastMessageAt) {
        Long sourceAt = entry.getSourceAt();

        return sourceAt != null && sourceAt < lastMessageAt;
    }

    /** As above, but counting: {@link #MIN_NEW_MESSAGES} turns have to have arrived since. */
    static boolean isStale(DiaryDTO entry, List<ChatDTO.MessageDTO> messages) {
        Long sourceAt = entry.getSourceAt();
        if (sourceAt == null) return false;

        long fresh = messages.stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt() > sourceAt)
                .count();

        return fresh >= MIN_NEW_MESSAGES;
    }

    /** When the last thing in this stretch of conversation was said. */
    private static long lastMessageAt(List<ChatDTO.MessageDTO> messages) {
        long last = 0;
        for (ChatDTO.MessageDTO m : messages) {
            if (m.getCreatedAt() != null && m.getCreatedAt() > last) last = m.getCreatedAt();
        }

        return last;
    }

    @Override
    public DiaryDTO generate(String userId, String date, boolean force) throws Exception {
        log.info("Calling generate for {} on {}", userId, date);

        DiaryDTO existing = userService.getDiaryInfo(userId, date);

        List<ChatDTO.MessageDTO> messages = chatService.getPartnerDay(userId, date);
        if (!this.hasEnough(messages)) {
            log.info("not enough conversation on {} for {}", date, userId);
            return null;
        }

        // `force` is the caller saying so explicitly, for one named date. Everything else defers to
        // isStale, which also protects a hand-edited entry — including from generateAll, whose
        // cheaper pre-filter cannot see how many of the day's turns are actually new.
        if (existing != null && !force && !isStale(existing, messages)) return null;

        long sourceAt = lastMessageAt(messages);

        List<ChatDTO.LLMMessageDTO> query = List.of(
                new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.system.name(), this.systemPrompt(date)),
                new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.user.name(), this.transcript(messages)));

        String answer = chatService.complete(query, this.format());
        if (answer == null) return null;

        DiaryDTO pDTO = this.parse(answer);
        if (pDTO == null) {
            log.info("could not read a diary out of: {}", answer);
            return null;
        }

        pDTO.setUserId(userId);
        pDTO.setDate(date);
        // How far this entry was written from. Read back on the next run to decide whether the
        // child has kept talking since — and taken from the messages we actually just used, so a
        // turn that arrived mid-request is left for next time rather than silently counted as read.
        pDTO.setSourceAt(sourceAt);

        if (existing == null) userService.createDiary(pDTO);
        else userService.updateDiary(pDTO);

        return pDTO;
    }

    /** The same floor {@link #generateAll} applies, for the single-day path that has no counts. */
    private boolean hasEnough(List<ChatDTO.MessageDTO> messages) {
        if (messages.size() < MIN_MESSAGES) return false;

        long mine = messages.stream()
                .filter(m -> m.getRole() == ChatDTO.MessageDTO.Role.user)
                .count();

        return mine >= MIN_USER_MESSAGES;
    }

    /**
     * The day as one block of text, labelled by who spoke.
     *
     * It is a single user turn rather than a replayed conversation because the model is not
     * continuing this conversation — it is reading it. Sent as alternating roles, a small model
     * answers the last thing the child said instead of summarizing the day.
     */
    private String transcript(List<ChatDTO.MessageDTO> messages) {
        List<ChatDTO.MessageDTO> window = messages.size() > MAX_DAY_MESSAGES
                ? messages.subList(messages.size() - MAX_DAY_MESSAGES, messages.size())
                : messages;

        StringBuilder sb = new StringBuilder();
        for (ChatDTO.MessageDTO m : window) {
            sb.append(m.getRole() == ChatDTO.MessageDTO.Role.user ? "아이: " : "친구: ")
              .append(m.getContent().trim())
              .append('\n');
        }

        return sb.toString();
    }

    /**
     * The model's answer as a diary, or null if there is no usable one in it.
     *
     * Everything is defended individually: a small model gets a field wrong far more often than it
     * gets the whole object wrong, and one bad `mood` should not throw away a good entry.
     */
    // Package-private, not private: this is the part a small model breaks, and DiaryServiceTest
    // covers the shapes it comes back with.
    DiaryDTO parse(String answer) {
        String json = extractJson(answer);
        if (json == null) return null;

        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            log.info("diary answer was not JSON: {}", e.toString());
            return null;
        }
        if (!node.isObject()) return null;

        String text = text(node, "text");
        String summary = text(node, "summary");
        // CONTENT is NOT NULL, so a body is the one field we cannot do without. A model that gave
        // only a summary still wrote the day down — use it rather than losing the entry.
        if (text == null) text = summary;
        if (text == null) return null;

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setText(text);
        pDTO.setSummary(summary != null ? summary : text);
        pDTO.setTitle(this.title(node, pDTO.getSummary()));
        pDTO.setMood(mood(node));
        pDTO.setTags(tags(node));

        return pDTO;
    }

    private String title(JsonNode node, String fallback) {
        String title = text(node, "title");
        if (title == null) title = fallback;

        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) return null;

        String s = value.asString().trim();

        return s.isEmpty() ? null : s;
    }

    /** An unknown or missing mood is not a reason to lose the entry; `calm` is the neutral one. */
    private static DiaryDTO.Mood mood(JsonNode node) {
        String value = text(node, "mood");
        if (value == null) return DiaryDTO.Mood.calm;

        for (DiaryDTO.Mood m : DiaryDTO.Mood.values()) {
            if (m.name().equalsIgnoreCase(value.trim())) return m;
        }

        log.info("unknown mood from the model: {}", value);

        return DiaryDTO.Mood.calm;
    }

    /**
     * Tags, deduplicated and capped.
     *
     * A model asked for an array sometimes answers with one comma-separated string, so both are
     * read. The leading '#' is stripped because the screen adds its own.
     */
    private static List<String> tags(JsonNode node) {
        JsonNode value = node.get("tags");
        if (value == null) return List.of();

        List<String> raw = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(t -> { if (t.isString()) raw.add(t.asString()); });
        } else if (value.isString()) {
            raw.addAll(List.of(value.asString().split(",")));
        }

        Set<String> tags = new LinkedHashSet<>();
        for (String tag : raw) {
            String cleaned = tag.trim().replaceFirst("^#+", "").trim();
            if (cleaned.isEmpty()) continue;
            tags.add(cleaned);
            if (tags.size() >= MAX_TAGS) break;
        }

        return List.copyOf(tags);
    }

    /**
     * The JSON object inside whatever the model actually sent.
     *
     * With `format: json` the answer is already bare, but that switch is Ollama's and a deployment
     * may have it off — in which case the object arrives wrapped in a code fence or a sentence.
     * Outermost braces, so a nested object doesn't truncate the parse.
     */
    private static String extractJson(String answer) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');

        return start < 0 || end <= start ? null : answer.substring(start, end + 1);
    }
}
