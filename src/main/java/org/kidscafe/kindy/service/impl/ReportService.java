package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.service.IChatService;
import org.kidscafe.kindy.service.ServiceUnavailableException;
import org.kidscafe.kindy.service.IReportService;
import org.kidscafe.kindy.service.IUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
class ReportService implements IReportService {
    private final IChatService chatService;
    private final IUserService userService;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    // ---- how much of the child's records one report is written from ----

    /**
     * How far back a report looks.
     *
     * A diary is about one day; a report is about a stretch of them, and a fortnight is roughly
     * what a parent means by "요즘". Going further costs context for evidence that has stopped
     * being true — a food a child refused two months ago is not what they eat now.
     */
    private static final int MAX_DAYS = 14;
    /**
     * The most turns handed to the model, kept from the end.
     *
     * A talkative fortnight outgrows a small model's context. The tail is kept because a report is
     * about where the child is now, and the newest turns are the ones that say so.
     */
    private static final int MAX_MESSAGES = 120;
    private static final int MAX_DIARIES = 14;

    // ---- what it takes before there is anything to report on ----

    /**
     * The floor, and the reason it exists.
     *
     * Five categories cannot be filled out of four sentences, so a model given four sentences
     * fills them from nowhere — and unlike a diary, a report is read by the child's parents and
     * teacher as an assessment. Either bound clears it: a child who has been talking, or one whose
     * days have already been written up as diaries.
     */
    private static final int MIN_USER_MESSAGES = 4;
    private static final int MIN_DIARIES = 2;
    /**
     * How much new evidence it takes before a written report is worth writing again.
     *
     * Every regeneration is five model calls. One more "응" is not a new report, and without a
     * floor every visit to the reports screen after every single turn would spend all five.
     */
    private static final int MIN_NEW_ITEMS = 4;

    // ---- bounds the screen imposes on what we store ----

    /** Radar axes below three collapse into a line or a dot rather than a shape. */
    private static final int MIN_TRAITS = 3;
    private static final int MAX_WEEKLY = 7;
    private static final int MAX_TIMELINE = 10;
    private static final int MAX_SUBJECTS = 6;
    /** `favorite`, `caution`, `closest` — chip rows and bar lists that wrap badly past five. */
    private static final int MAX_LIST = 5;
    private static final int MAX_LABEL_LENGTH = 24;
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_NOTE_LENGTH = 600;
    /** The stacked food bars are portions, not percentages. */
    private static final int MAX_PORTION = 5;

    /**
     * The five axes the personality radar draws.
     *
     * They are also the keys {@code ChildFullReport}'s parent tips are filed under, so a trait
     * outside this set has neither an axis nor a tip. Dropped rather than drawn: an invented label
     * silently changes the shape of the chart, and the shape is what a parent reads.
     */
    private static final Set<String> TRAITS = Set.of("사교성", "창의성", "집중력", "활동성", "감수성");

    /** The health timeline's three faces. */
    private static final Set<String> STATUSES = Set.of("good", "mild", "bad");

    @Value("${kindy.llm.report.prompt:}")
    private String LLM_REPORT_PROMPT;
    @Value("${kindy.llm.report.format:json}")
    private String LLM_REPORT_FORMAT;

    /**
     * The system prompt, shared by all five categories.
     *
     * `kindy.llm.report.prompt` overrides it. As with the chat and diary prompts it is wired as
     * `${LLM_REPORT_PROMPT:}`, so an unset environment variable leaves the property blank rather
     * than absent and a `@Value` default would never be reached — hence the blank check below.
     * <p>
     * Three things it has to get right, in order of how badly they fail:
     * <ol>
     *   <li>Only the child's own turns and their own diary are evidence. The partner's replies are
     *       there for context — they are what the child was answering — but a report that treats
     *       what the AI said as something the child did is a fabricated assessment of a real
     *       child, and it is a teacher and a parent who read it.</li>
     *   <li>An unknown field is left empty. This is the difference between this prompt and the
     *       diary's: a diary has one body and a model that pads it is merely wordy, while a report
     *       is mostly numbers, and a padded number is indistinguishable from a measured one.</li>
     *   <li>JSON and nothing else. {@link #extractJson} cleans up after a code fence or a
     *       "물론이죠!", but asking plainly costs nothing and fails less often.</li>
     * </ol>
     */
    private static final String DEFAULT_REPORT_PROMPT = """
            당신은 유치원에 다니는 아이의 성장 리포트를 쓰는 분석가입니다. 이 리포트는 아이의 부모와 담임 선생님이 읽습니다.
            아래에는 아이가 AI 친구와 나눈 최근 대화와, 그 대화를 정리한 아이의 일기가 시간 순서대로 주어집니다. '아이:'로 시작하는 줄은 아이가 한 말이고, '친구:'로 시작하는 줄은 AI 친구가 한 말입니다.
            리포트는 오직 아이가 한 말과 아이의 일기에서 드러난 사실만으로 써야 합니다. AI 친구가 한 말은 아이가 무엇에 답한 것인지 알기 위한 참고일 뿐이며, 친구가 한 말이나 대화에 없는 일을 아이의 것으로 쓰면 절대 안 됩니다.
            근거가 없는 항목은 지어내지 말고 빈 배열이나 빈 문자열로 두세요. 비어 있는 칸이 지어낸 숫자보다 낫습니다.
            지금 쓸 것은 '{category}' 항목입니다.
            당신은 반드시 아래 형태의 JSON 객체 하나만 출력해야 합니다. 설명, 인사말, Markdown, 코드 블록을 앞뒤에 붙여서는 절대 안 됩니다.
            {schema}
            각 항목의 규칙입니다.
            {rules}
            모든 문장은 한국어 존댓말로 쓰고, 이모지와 표는 쓰지 마세요.""";

    /**
     * What one category asks for: its name, the object shape, and the rules for its fields.
     *
     * Kept beside the prompt rather than in the properties file because the schema is not a
     * setting — it is the frontend's type, restated. `types.ts` is the other half of this record,
     * and the two have to move together.
     */
    private record Ask(String category, String schema, String rules) {}

    private static Ask ask(ReportDTO.Category category) {
        return switch (category) {
            case FOOD -> new Ask("음식 섭취 분석",
                    """
                    {"weekly": [{"day": "월", "vegetable": 3, "protein": 2, "carbs": 4, "dairy": 1}], "balanceNote": "설명", "favorite": ["당근"], "caution": ["버섯"]}""",
                    """
                    - weekly: 아이가 무엇을 먹었는지 말한 날만 넣습니다. day는 '월'부터 '일' 중 하나이고, vegetable(채소)·protein(단백질)·carbs(탄수화물)·dairy(유제품)는 각각 0에서 5 사이의 정수입니다. 최대 7개이며 같은 요일을 두 번 쓰지 마세요.
                    - balanceNote: 아이의 식습관을 한두 문장으로 설명합니다.
                    - favorite: 아이가 좋아한다고 말한 음식. 3개 이내의 짧은 단어 배열입니다.
                    - caution: 아이가 싫어하거나 먹기 힘들어한 음식. 3개 이내입니다.""");
            case HEALTH -> new Ask("건강 상태 추적",
                    """
                    {"timeline": [{"date": "8/19", "status": "good", "note": "설명"}], "note": "설명"}""",
                    """
                    - timeline: 아이가 몸 상태를 이야기한 날들입니다. date는 'M/D' 형식, status는 good(좋음)·mild(조금 힘듦)·bad(아픔) 중 하나, note는 한 문장입니다. 5개 이내이며 같은 날짜를 두 번 쓰지 마세요.
                    - note: 최근 건강 상태를 한두 문장으로 요약합니다.
                    - 키와 몸무게는 대화로 알 수 없는 값입니다. 절대 쓰지 마세요.""");
            case FRIENDSHIP -> new Ask("교우관계 맵",
                    """
                    {"sociabilityScore": 70, "closest": [{"name": "민준", "strength": 80, "note": "설명"}], "groupNote": "설명"}""",
                    """
                    - sociabilityScore: 아이가 또래와 어울리는 정도. 0에서 100 사이의 정수입니다.
                    - closest: 아이가 이름을 말한 친구만 3명 이내로 넣습니다. name은 아이가 부른 그 이름 그대로, strength는 0에서 100 사이, note는 한 문장입니다. 아이가 말하지 않은 이름을 지어내면 절대 안 됩니다.
                    - groupNote: 아이가 또래와 지내는 모습을 한두 문장으로 설명합니다.""");
            case PERSONALITY -> new Ask("성격 성향 분석",
                    """
                    {"traits": [{"trait": "사교성", "value": 70}, {"trait": "창의성", "value": 60}, {"trait": "집중력", "value": 50}, {"trait": "활동성", "value": 80}, {"trait": "감수성", "value": 65}], "mbtiLike": "호기심 많은 탐험가", "summary": "설명"}""",
                    """
                    - traits: 반드시 사교성·창의성·집중력·활동성·감수성 다섯 개를 모두 넣고, 다른 이름은 쓰지 마세요. value는 0에서 100 사이의 정수입니다.
                    - mbtiLike: 아이의 성향을 한마디로 나타내는 20자 이내의 표현입니다.
                    - summary: 두세 문장으로 아이의 성향을 설명합니다.""");
            case LEARNING -> new Ask("학습 발달 현황",
                    """
                    {"subjects": [{"subject": "한글", "progress": 60}], "recentTopic": "공룡", "interestNote": "설명"}""",
                    """
                    - subjects: 아이가 이야기한 배움이나 활동 4개 이내입니다. subject는 짧은 단어, progress는 0에서 100 사이의 정수이며 같은 이름을 두 번 쓰지 마세요.
                    - recentTopic: 아이가 요즘 가장 관심을 보인 주제. 한 단어나 짧은 구입니다.
                    - interestNote: 그 관심을 한두 문장으로 설명합니다.""");
        };
    }

    private String systemPrompt(ReportDTO.Category category) {
        String template = (LLM_REPORT_PROMPT == null || LLM_REPORT_PROMPT.isBlank())
                ? DEFAULT_REPORT_PROMPT : LLM_REPORT_PROMPT;
        Ask ask = ask(category);

        return template
                .replace("{category}", ask.category())
                .replace("{schema}", ask.schema())
                .replace("{rules}", ask.rules());
    }

    /** Null when the deployment turned structured output off — a server that has no `format`. */
    private String format() {
        return LLM_REPORT_FORMAT == null || LLM_REPORT_FORMAT.isBlank() ? null : LLM_REPORT_FORMAT.trim();
    }

    // ---- what one report is written from ----

    /**
     * The child's recent records, read once and shared by all five categories.
     *
     * Gathering is the expensive-but-cheap half (a handful of queries); the model calls are the
     * other half. Doing it once per request rather than once per category is the difference
     * between five passes over the same fortnight and one.
     *
     * @param transcript  the whole stretch as one block of text, in the order it happened
     * @param stamps      when each piece of evidence was recorded, epoch ms — how {@link #isStale}
     *                    counts what has arrived since a report was written
     * @param userMessages turns the child themself took, within the window actually sent
     * @param diaries     diary entries within the window actually sent
     */
    private record Evidence(String transcript, List<Long> stamps, int userMessages, int diaries) {
        boolean isEnough() {
            return userMessages >= MIN_USER_MESSAGES || diaries >= MIN_DIARIES;
        }

        /** When the newest thing we read was recorded. 0 for a child with no records at all. */
        long sourceAt() {
            return stamps.stream().mapToLong(Long::longValue).max().orElse(0L);
        }

        int freshAfter(long at) {
            return (int) stamps.stream().filter(stamp -> stamp > at).count();
        }
    }

    /** One turn, tagged with the day it belongs to so the transcript can head each day. */
    private record Said(String date, ChatDTO.MessageDTO message) {}

    @Override
    public List<ReportDTO> generateAll(String childId, boolean force) throws Exception {
        log.info("Calling generateAll for {}", childId);

        Evidence evidence = this.gather(childId);
        if (!evidence.isEnough()) {
            log.info("not enough to report on for {}", childId);
            return List.of();
        }

        List<ReportDTO> written = new ArrayList<>();
        for (ReportDTO.Category category : ReportDTO.Category.values()) {
            // One bad category must not cost the caller the other four — the model is the fragile
            // part here and it fails per-request.
            try {
                ReportDTO report = this.write(childId, category, evidence, force);
                if (report != null) written.add(report);
            } catch (ServiceUnavailableException e) {
                // Not a bad category — a deployment with no model. The other four would fail
                // identically, and an empty list returned as success reads as "nothing to report".
                throw e;
            } catch (Exception e) {
                log.info("report generation failed for {} on {}: {}", childId, category, e.toString());
            }
        }

        return written;
    }

    @Override
    public ReportDTO generate(String childId, ReportDTO.Category category, boolean force) throws Exception {
        log.info("Calling generate for {} on {}", childId, category);

        Evidence evidence = this.gather(childId);
        // `force` does not lower the floor. It means "write this one again even though it is
        // current", not "write one out of nothing" — there is no report to be had either way.
        if (!evidence.isEnough()) {
            log.info("not enough to report on for {}", childId);
            return null;
        }

        return this.write(childId, category, evidence, force);
    }

    private ReportDTO write(String childId, ReportDTO.Category category, Evidence evidence, boolean force)
            throws Exception {
        ReportDTO existing = userService.getReportInfo(childId, category);
        long writtenAt = existing == null || existing.getCreatedAt() == null ? 0L : existing.getCreatedAt();

        if (!force && !isStale(existing, evidence.sourceAt(), evidence.freshAfter(writtenAt))) return null;

        List<ChatDTO.LLMMessageDTO> query = List.of(
                new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.system.name(), this.systemPrompt(category)),
                new ChatDTO.LLMMessageDTO(ChatDTO.MessageDTO.Role.user.name(), evidence.transcript()));

        String answer = chatService.complete(query, this.format());
        if (answer == null) return null;

        String data = this.parse(category, answer, existing);
        if (data == null) {
            log.info("could not read a {} report out of: {}", category, answer);
            return null;
        }

        ReportDTO pDTO = new ReportDTO();
        pDTO.setChildId(childId);
        pDTO.setCategory(category);
        pDTO.setData(data);

        userService.saveReport(pDTO);

        return pDTO;
    }

    /**
     * Whether a report has fallen behind the child's records.
     *
     * Unlike the diary there is still no flag for "a person wrote this" — {@code T_CHILD_REPORT} has
     * no SOURCE_AT — so this cannot tell a hand-saved report from a generated one, and it does not
     * pretend to. What has changed is the consequence: reports are append-only now, so writing over
     * a hand-saved report leaves it in the table rather than destroying it. That makes the gap
     * recoverable instead of fatal, but a screen that lets a teacher edit a report still wants the
     * column, so that the write does not happen in the first place.
     * <p>
     * CREATED_AT is stamped when the version is written, which is after every piece of evidence it
     * was written from, so the comparison has none of the round-trip trouble the diary's stored
     * SOURCE_AT had. It is also the only stamp the row has: an immutable row has no update time
     * (see docs/migration-report-identity.sql PHASE 2).
     * <p>
     * Package-private for {@code ReportServiceTest}: this is what stands between a correct report
     * and five model calls per screen open.
     */
    static boolean isStale(ReportDTO existing, long sourceAt, int freshItems) {
        if (existing == null) return true;

        Long writtenAt = existing.getCreatedAt();
        if (writtenAt == null) return true;
        if (sourceAt <= writtenAt) return false;

        return freshItems >= MIN_NEW_ITEMS;
    }

    /**
     * The child's recent conversation and diary, as one block of text.
     *
     * It goes to the model as a single user turn rather than a replayed conversation because the
     * model is not continuing this conversation — it is reading it. Sent as alternating roles, a
     * small model answers the last thing the child said instead of assessing the fortnight.
     */
    private Evidence gather(String childId) throws Exception {
        List<ChatDTO.DayDTO> days = chatService.getPartnerDays(childId);
        // getPartnerDays answers newest-first, and the newest MAX_DAYS are the ones worth reading —
        // but the transcript itself runs forwards, which is the order the fortnight happened in.
        List<ChatDTO.DayDTO> window = days.size() > MAX_DAYS ? days.subList(0, MAX_DAYS) : days;

        List<Said> said = new ArrayList<>();
        for (int i = window.size() - 1; i >= 0; i--) {
            String date = window.get(i).getDate();
            for (ChatDTO.MessageDTO m : chatService.getPartnerDay(childId, date)) {
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                said.add(new Said(date, m));
            }
        }
        if (said.size() > MAX_MESSAGES) said = said.subList(said.size() - MAX_MESSAGES, said.size());

        List<DiaryDTO> diaries = userService.getDiaries(childId);
        if (diaries.size() > MAX_DIARIES) diaries = diaries.subList(0, MAX_DIARIES);

        List<Long> stamps = new ArrayList<>();
        int userMessages = 0;
        StringBuilder sb = new StringBuilder();

        String heading = null;
        for (Said s : said) {
            if (!s.date().equals(heading)) {
                heading = s.date();
                sb.append('\n').append('[').append(heading).append(']').append('\n');
            }
            ChatDTO.MessageDTO m = s.message();
            boolean mine = m.getRole() == ChatDTO.MessageDTO.Role.user;
            if (mine) userMessages++;
            if (m.getCreatedAt() != null) stamps.add(m.getCreatedAt());
            sb.append(mine ? "아이: " : "친구: ").append(m.getContent().trim()).append('\n');
        }

        if (!diaries.isEmpty()) {
            sb.append("\n[일기]\n");
            // Oldest-first, like the conversation above it: getDiaries answers newest-first.
            for (int i = diaries.size() - 1; i >= 0; i--) {
                DiaryDTO diary = diaries.get(i);
                String body = diary.getText() != null ? diary.getText() : diary.getSummary();
                if (body == null || body.isBlank()) continue;
                if (diary.getCreatedAt() != null) stamps.add(diary.getCreatedAt());
                sb.append(diary.getDate())
                  .append(diary.getMood() == null ? "" : " (" + diary.getMood().name() + ")")
                  .append(' ').append(body.trim()).append('\n');
            }
        }

        return new Evidence(sb.toString(), stamps, userMessages, diaries.size());
    }

    // ---- reading the model's answer ----

    /**
     * The model's answer as one category's stored JSON, or null if there is no usable report in it.
     *
     * The answer is not stored as it came. {@code T_CHILD_REPORT.DATA} is an opaque blob the
     * backend never validates, which means the screen's types are enforced here or nowhere: every
     * field is rebuilt into the shape {@code types.ts} declares, numbers are clamped to the ranges
     * the charts can draw, and lists are capped and de-duplicated because React keys them by value.
     * A blob that is merely *nearly* right takes down its whole card — {@code fetchChildReports}
     * catches the parse failure per category and leaves that one empty.
     * <p>
     * {@code existing} is read for one reason: the height and weight a person may have recorded
     * before. Everything else is replaced.
     * <p>
     * Package-private, not private: this is the part a small model breaks, and
     * {@code ReportServiceTest} covers the shapes it comes back with.
     */
    String parse(ReportDTO.Category category, String answer, ReportDTO existing) {
        String json = extractJson(answer);
        if (json == null) return null;

        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            log.info("report answer was not JSON: {}", e.toString());
            return null;
        }
        if (!node.isObject()) return null;

        ObjectNode data = switch (category) {
            case FOOD -> food(node);
            case HEALTH -> health(node, existing);
            case FRIENDSHIP -> friendship(node);
            case PERSONALITY -> personality(node);
            case LEARNING -> learning(node);
        };

        return data == null ? null : MAPPER.writeValueAsString(data);
    }

    private static ObjectNode food(JsonNode node) {
        String balanceNote = prose(node, "balanceNote", MAX_NOTE_LENGTH);
        List<String> favorite = strings(node, "favorite");
        List<String> caution = strings(node, "caution");

        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode weekly = data.putArray("weekly");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode row : array(node, "weekly")) {
            String day = text(row, "day", MAX_LABEL_LENGTH);
            // Two bars under one label is a chart that reads as one day eaten twice.
            if (day == null || !seen.add(day)) continue;

            ObjectNode entry = weekly.addObject();
            entry.put("day", day);
            entry.put("vegetable", bounded(row, "vegetable", MAX_PORTION));
            entry.put("protein", bounded(row, "protein", MAX_PORTION));
            entry.put("carbs", bounded(row, "carbs", MAX_PORTION));
            entry.put("dairy", bounded(row, "dairy", MAX_PORTION));
            if (weekly.size() >= MAX_WEEKLY) break;
        }

        // Nothing said and nothing counted: an empty card is worse than no card.
        if (balanceNote == null && favorite.isEmpty() && caution.isEmpty() && weekly.isEmpty()) return null;

        data.put("balanceNote", balanceNote == null ? "" : balanceNote);
        ArrayNode good = data.putArray("favorite");
        favorite.forEach(good::add);
        ArrayNode bad = data.putArray("caution");
        caution.forEach(bad::add);

        return data;
    }

    private static ObjectNode health(JsonNode node, ReportDTO existing) {
        String note = prose(node, "note", MAX_NOTE_LENGTH);

        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode timeline = data.putArray("timeline");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode row : array(node, "timeline")) {
            String date = text(row, "date", MAX_LABEL_LENGTH);
            String entryNote = prose(row, "note", MAX_NOTE_LENGTH);
            // The screen keys these rows by date, so a repeat would drop one of them anyway.
            if (date == null || entryNote == null || !seen.add(date)) continue;

            ObjectNode entry = timeline.addObject();
            entry.put("date", date);
            entry.put("status", status(row));
            entry.put("note", entryNote);
            if (timeline.size() >= MAX_TIMELINE) break;
        }

        if (note == null && timeline.isEmpty()) return null;

        carryMeasurements(data, existing);
        data.put("note", note == null ? "" : note);

        return data;
    }

    /**
     * Height and weight, carried over from whatever was stored before — never from the model.
     *
     * There is no table of growth measurements anywhere in this system and no child says their own
     * height to an AI friend, so a model asked for these two numbers can only invent them. The
     * card prints them as "105cm · 18kg" beside a real timeline, and a parent reads that as a
     * measurement somebody took. 0 is the screen's "not measured yet"; {@code emptyChildReports()}
     * starts there too.
     */
    private static void carryMeasurements(ObjectNode data, ReportDTO existing) {
        JsonNode previous = stored(existing);
        for (String field : List.of("heightCm", "weightKg")) {
            JsonNode value = previous == null ? null : previous.get(field);
            if (value != null && value.isNumber()) data.set(field, value);
            else data.put(field, 0);
        }
    }

    /** The blob already stored for this category, or null if there is none or it cannot be read. */
    private static JsonNode stored(ReportDTO existing) {
        if (existing == null || existing.getData() == null) return null;

        try {
            JsonNode node = MAPPER.readTree(existing.getData());
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ObjectNode friendship(JsonNode node) {
        String groupNote = prose(node, "groupNote", MAX_NOTE_LENGTH);

        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode closest = data.putArray("closest");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode row : array(node, "closest")) {
            String name = prose(row, "name", MAX_LABEL_LENGTH);
            if (name == null || !seen.add(name)) continue;

            ObjectNode entry = closest.addObject();
            entry.put("name", name);
            entry.put("strength", percent(row, "strength"));
            entry.put("note", nullToEmpty(prose(row, "note", MAX_NOTE_LENGTH)));
            if (closest.size() >= MAX_LIST) break;
        }

        if (groupNote == null && closest.isEmpty()) return null;

        data.put("sociabilityScore", percent(node, "sociabilityScore"));
        data.put("groupNote", nullToEmpty(groupNote));

        return data;
    }

    private static ObjectNode personality(JsonNode node) {
        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode traits = data.putArray("traits");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode row : array(node, "traits")) {
            String trait = text(row, "trait", MAX_LABEL_LENGTH);
            if (trait == null || !TRAITS.contains(trait) || !seen.add(trait)) continue;

            ObjectNode entry = traits.addObject();
            entry.put("trait", trait);
            entry.put("value", percent(row, "value"));
        }
        if (traits.size() < MIN_TRAITS) return null;

        String mbtiLike = prose(node, "mbtiLike", MAX_TITLE_LENGTH);
        String summary = prose(node, "summary", MAX_NOTE_LENGTH);
        // These two are the whole of the "AI 파트너 분석 & 팁" card at the top of the report, so a
        // radar with nothing written beside it is half a screen of blank gradient.
        if (mbtiLike == null && summary == null) return null;

        data.put("mbtiLike", nullToEmpty(mbtiLike));
        data.put("summary", nullToEmpty(summary));

        return data;
    }

    private static ObjectNode learning(JsonNode node) {
        String recentTopic = prose(node, "recentTopic", MAX_TITLE_LENGTH);
        String interestNote = prose(node, "interestNote", MAX_NOTE_LENGTH);

        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode subjects = data.putArray("subjects");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode row : array(node, "subjects")) {
            String subject = prose(row, "subject", MAX_LABEL_LENGTH);
            if (subject == null || !seen.add(subject)) continue;

            ObjectNode entry = subjects.addObject();
            entry.put("subject", subject);
            entry.put("progress", percent(row, "progress"));
            if (subjects.size() >= MAX_SUBJECTS) break;
        }

        if (recentTopic == null && interestNote == null && subjects.isEmpty()) return null;

        data.put("recentTopic", nullToEmpty(recentTopic));
        data.put("interestNote", nullToEmpty(interestNote));

        return data;
    }

    // ---- field readers ----

    /**
     * The rows at {@code field}.
     *
     * A model asked for a list of one regularly answers with the object itself, which is the same
     * answer differently shaped. Anything else reads as no rows; the loops call {@link #text} on
     * each row, and {@code get} on a non-object node is null, so a list of strings costs nothing.
     */
    private static Iterable<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) return List.of();
        if (value.isArray()) return value;
        if (value.isObject()) return List.of(value);

        return List.of();
    }

    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) return null;

        String s = value.asString().trim();
        if (s.isEmpty()) return null;

        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * A field that is meant to read as a sentence.
     *
     * As {@link #text}, except that an answer with no letters in it is treated as nothing said.
     * A small model drops stray tokens where prose belongs — ":-3," and "-8," are both real
     * answers this one gave for a health note — and they print, on a child's health card, beside
     * dates that are real. Nothing said is the honest reading of a note made only of punctuation.
     * <p>
     * Not used for labels: a timeline `date` is legitimately "08/19", with no letter in it.
     */
    private static String prose(JsonNode node, String field, int max) {
        String s = text(node, field, max);

        return s != null && s.codePoints().anyMatch(Character::isLetter) ? s : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * A short list of words, de-duplicated and capped.
     *
     * As with the diary's tags, a model asked for an array sometimes answers with the string it
     * would have joined, so both are read.
     */
    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) return List.of();

        List<String> raw = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(v -> { if (v.isString()) raw.add(v.asString()); });
        } else if (value.isString()) {
            raw.addAll(List.of(value.asString().split(",")));
        }

        Set<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            String cleaned = s.trim();
            if (cleaned.isEmpty()) continue;
            // These become "👍 당근" chips. A stray token with no letters in it is not a food.
            if (cleaned.codePoints().noneMatch(Character::isLetter)) continue;
            if (cleaned.length() > MAX_LABEL_LENGTH) cleaned = cleaned.substring(0, MAX_LABEL_LENGTH);
            out.add(cleaned);
            if (out.size() >= MAX_LIST) break;
        }

        return List.copyOf(out);
    }

    private static int percent(JsonNode node, String field) {
        return bounded(node, field, 100);
    }

    /**
     * A number the chart has to be able to draw.
     *
     * Out of range is clamped rather than dropped: a bar drawn at 140% escapes its track and a
     * negative one vanishes, and neither failure looks like a data problem on screen. Missing
     * reads as 0 — the same "we don't know" the empty skeleton starts at, and the honest answer
     * when the model left a field out because there was no evidence for it.
     */
    private static int bounded(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null) return 0;

        // asInt(default) coerces "70" as well as 70, and falls back on anything it cannot read.
        return Math.max(0, Math.min(max, value.asInt(0)));
    }

    /** An unknown status is not a reason to lose the day; `mild` is the one that claims least. */
    private static String status(JsonNode row) {
        String value = text(row, "status", MAX_LABEL_LENGTH);
        if (value == null) return "mild";

        String lower = value.trim().toLowerCase();

        return STATUSES.contains(lower) ? lower : "mild";
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
