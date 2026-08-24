package org.kidscafe.kindy.service.impl;

import org.junit.jupiter.api.Test;
import org.kidscafe.kindy.dto.ReportDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two decisions the report generator makes on its own.
 *
 * <b>Reading the model's answer</b> — the first half. {@code T_CHILD_REPORT.DATA} is an opaque
 * blob the backend never validates, so the shapes the screen's charts require are enforced in
 * {@code parse} or nowhere, and every malformed string below is one a small model has actually
 * answered with.
 * <p>
 * <b>Deciding whether to write a category again</b> — the second half. Each regeneration is five
 * model calls, and the reports screen runs one automatically every time it opens.
 * <p>
 * Nothing here talks to the model, the database or Spring: the methods under test are pure, and
 * the service is built with null collaborators because they never reach them.
 */
class ReportServiceTest {
    private final ReportService service = new ReportService(null, null);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonNode read(ReportDTO.Category category, String answer) {
        return this.read(category, answer, null);
    }

    private JsonNode read(ReportDTO.Category category, String answer, ReportDTO existing) {
        String data = service.parse(category, answer, existing);

        return data == null ? null : MAPPER.readTree(data);
    }

    private static ReportDTO stored(String data) {
        ReportDTO report = new ReportDTO();
        report.setData(data);
        return report;
    }

    // ---- reading the model's answer ----

    @Test
    void readsTheOrdinaryAnswer() {
        JsonNode food = this.read(ReportDTO.Category.FOOD, """
                {"weekly": [{"day": "월", "vegetable": 3, "protein": 2, "carbs": 4, "dairy": 1}],
                 "balanceNote": "채소를 잘 먹었어요.", "favorite": ["당근"], "caution": ["버섯"]}""");

        assertNotNull(food);
        assertEquals(1, food.get("weekly").size());
        assertEquals("월", food.get("weekly").get(0).get("day").asString());
        assertEquals(3, food.get("weekly").get(0).get("vegetable").asInt(-1));
        assertEquals("채소를 잘 먹었어요.", food.get("balanceNote").asString());
        assertEquals("당근", food.get("favorite").get(0).asString());
        assertEquals("버섯", food.get("caution").get(0).asString());
    }

    @Test
    void findsTheObjectInsideAFenceOrAPreamble() {
        JsonNode learning = this.read(ReportDTO.Category.LEARNING, """
                물론이죠! 학습 발달 현황입니다.
                ```json
                {"subjects": [{"subject": "한글", "progress": 60}], "recentTopic": "공룡",
                 "interestNote": "공룡 이름을 많이 알고 있어요."}
                ```
                도움이 되었길 바랍니다.""");

        assertNotNull(learning);
        assertEquals("한글", learning.get("subjects").get(0).get("subject").asString());
        assertEquals("공룡", learning.get("recentTopic").asString());
    }

    @Test
    void keepsHeightAndWeightOutOfTheModelsHands() {
        // The one number in these reports a model can only invent: nothing in this system records
        // a child's height, and no child tells an AI friend theirs. The card prints it beside a
        // real timeline, where a parent reads it as something somebody measured.
        ReportDTO existing = stored("""
                {"timeline": [], "heightCm": 105, "weightKg": 18, "note": "지난 기록"}""");

        JsonNode health = this.read(ReportDTO.Category.HEALTH, """
                {"timeline": [{"date": "8/19", "status": "good", "note": "잘 지냈어요."}],
                 "heightCm": 120, "weightKg": 25, "note": "건강합니다."}""", existing);

        assertNotNull(health);
        assertEquals(105, health.get("heightCm").asInt(-1));
        assertEquals(18, health.get("weightKg").asInt(-1));
        // Everything else is the new answer.
        assertEquals("건강합니다.", health.get("note").asString());
    }

    @Test
    void startsHeightAndWeightAtZeroWhenNobodyHasEverRecordedThem() {
        JsonNode health = this.read(ReportDTO.Category.HEALTH, """
                {"timeline": [], "heightCm": 120, "weightKg": 25, "note": "건강합니다."}""");

        assertNotNull(health);
        assertEquals(0, health.get("heightCm").asInt(-1));
        assertEquals(0, health.get("weightKg").asInt(-1));
    }

    @Test
    void clampsNumbersToWhatTheChartCanDraw() {
        // A bar drawn at 140% escapes its track and a negative one vanishes; neither reads as a
        // data problem on screen.
        JsonNode friendship = this.read(ReportDTO.Category.FRIENDSHIP, """
                {"sociabilityScore": 140, "closest": [{"name": "민준", "strength": -20, "note": "짝꿍이에요."}],
                 "groupNote": "친구들과 잘 지내요."}""");

        assertNotNull(friendship);
        assertEquals(100, friendship.get("sociabilityScore").asInt(-1));
        assertEquals(0, friendship.get("closest").get(0).get("strength").asInt(-1));
    }

    @Test
    void readsANumberItSentAsAString() {
        JsonNode learning = this.read(ReportDTO.Category.LEARNING, """
                {"subjects": [{"subject": "한글", "progress": "60"}], "interestNote": "글자를 좋아해요."}""");

        assertNotNull(learning);
        assertEquals(60, learning.get("subjects").get(0).get("progress").asInt(-1));
    }

    @Test
    void treatsAMissingNumberAsZeroRatherThanGuessing() {
        JsonNode food = this.read(ReportDTO.Category.FOOD, """
                {"weekly": [{"day": "월"}], "balanceNote": "골고루 먹었어요."}""");

        assertNotNull(food);
        JsonNode monday = food.get("weekly").get(0);
        assertEquals(0, monday.get("vegetable").asInt(-1));
        assertEquals(0, monday.get("protein").asInt(-1));
        assertEquals(0, monday.get("carbs").asInt(-1));
        assertEquals(0, monday.get("dairy").asInt(-1));
    }

    @Test
    void acceptsAListAsOneCommaSeparatedString() {
        // Asked for an array, a small model regularly answers with the string it would have joined.
        JsonNode food = this.read(ReportDTO.Category.FOOD, """
                {"balanceNote": "잘 먹어요.", "favorite": "당근, 사과 ,, 우유"}""");

        assertNotNull(food);
        assertEquals(3, food.get("favorite").size());
        assertEquals("당근", food.get("favorite").get(0).asString());
        assertEquals("우유", food.get("favorite").get(2).asString());
    }

    @Test
    void readsASingleRowItSentAsAnObject() {
        // A list of one, differently shaped — the same answer.
        JsonNode food = this.read(ReportDTO.Category.FOOD, """
                {"weekly": {"day": "화", "vegetable": 2, "protein": 2, "carbs": 3, "dairy": 1},
                 "balanceNote": "잘 먹어요."}""");

        assertNotNull(food);
        assertEquals(1, food.get("weekly").size());
        assertEquals("화", food.get("weekly").get(0).get("day").asString());
    }

    @Test
    void dropsRowsThatWouldCollideOnTheirKey() {
        // The health card keys its rows by date, so a repeat would silently drop one anyway — and
        // two bars under one weekday reads as a day eaten twice.
        JsonNode health = this.read(ReportDTO.Category.HEALTH, """
                {"timeline": [{"date": "8/19", "status": "good", "note": "괜찮았어요."},
                              {"date": "8/19", "status": "bad", "note": "배가 아팠어요."}],
                 "note": "대체로 건강합니다."}""");

        assertNotNull(health);
        assertEquals(1, health.get("timeline").size());
        assertEquals("good", health.get("timeline").get(0).get("status").asString());
    }

    @Test
    void capsTheListsTheScreenHasRoomFor() {
        JsonNode friendship = this.read(ReportDTO.Category.FRIENDSHIP, """
                {"sociabilityScore": 70, "groupNote": "잘 지내요.", "closest": [
                    {"name": "가", "strength": 90, "note": "짝꿍"}, {"name": "나", "strength": 80, "note": "짝꿍"},
                    {"name": "다", "strength": 70, "note": "짝꿍"}, {"name": "라", "strength": 60, "note": "짝꿍"},
                    {"name": "마", "strength": 50, "note": "짝꿍"}, {"name": "바", "strength": 40, "note": "짝꿍"}]}""");

        assertNotNull(friendship);
        assertEquals(5, friendship.get("closest").size());
        assertEquals("마", friendship.get("closest").get(4).get("name").asString());
    }

    @Test
    void fallsBackToMildOnAStatusItInvented() {
        // `mild` is the middle face. Reading a day the model could not classify as "좋음" would
        // claim more than we know.
        JsonNode health = this.read(ReportDTO.Category.HEALTH, """
                {"timeline": [{"date": "8/19", "status": "보통", "note": "그냥 그랬어요."}], "note": "괜찮아요."}""");

        assertNotNull(health);
        assertEquals("mild", health.get("timeline").get(0).get("status").asString());
    }

    @Test
    void dropsTraitsThatHaveNoAxisToSitOn() {
        // An invented label silently changes the shape of the radar, and the shape is what a
        // parent reads. It also has no tip filed under it in ChildFullReport.
        JsonNode personality = this.read(ReportDTO.Category.PERSONALITY, """
                {"traits": [{"trait": "사교성", "value": 70}, {"trait": "리더십", "value": 90},
                            {"trait": "창의성", "value": 60}, {"trait": "집중력", "value": 50}],
                 "mbtiLike": "호기심 많은 탐험가", "summary": "새로운 것을 좋아합니다."}""");

        assertNotNull(personality);
        assertEquals(3, personality.get("traits").size());
        assertEquals("사교성", personality.get("traits").get(0).get("trait").asString());
        assertEquals("창의성", personality.get("traits").get(1).get("trait").asString());
        assertEquals("집중력", personality.get("traits").get(2).get("trait").asString());
    }

    @Test
    void refusesARadarWithTooFewAxesToBeAShape() {
        assertNull(service.parse(ReportDTO.Category.PERSONALITY, """
                {"traits": [{"trait": "사교성", "value": 70}, {"trait": "창의성", "value": 60}],
                 "mbtiLike": "탐험가", "summary": "새로운 것을 좋아합니다."}""", null));
    }

    @Test
    void refusesARadarWithNothingWrittenBesideIt() {
        // These two lines are the whole of the "AI 파트너 분석 & 팁" card at the top of the report.
        assertNull(service.parse(ReportDTO.Category.PERSONALITY, """
                {"traits": [{"trait": "사교성", "value": 70}, {"trait": "창의성", "value": 60},
                            {"trait": "집중력", "value": 50}]}""", null));
    }

    @Test
    void treatsAStrayTokenWherePraiseBelongsAsNothingSaid() {
        // ":-3," and "-8," are both real answers this model gave for a health note. Left in, they
        // print on a child's health card beside dates that are real.
        JsonNode health = this.read(ReportDTO.Category.HEALTH, """
                {"timeline": [{"date": "8/19", "status": "good", "note": "-8,"},
                              {"date": "8/20", "status": "good", "note": "잘 지냈어요."}],
                 "note": "건강합니다."}""");

        assertNotNull(health);
        assertEquals(1, health.get("timeline").size());
        // The surviving row keeps its date, which is itself legitimately letter-free — the rule
        // applies to prose, not to labels.
        assertEquals("8/20", health.get("timeline").get(0).get("date").asString());

        JsonNode food = this.read(ReportDTO.Category.FOOD, """
                {"balanceNote": "잘 먹어요.", "favorite": ["당근", ":-3,"], "caution": []}""");
        assertNotNull(food);
        assertEquals(1, food.get("favorite").size());
        assertEquals("당근", food.get("favorite").get(0).asString());
    }

    @Test
    void refusesAnAnswerWithNoReportInIt() {
        // Nothing to store, and an empty card on a child's report is worse than no card.
        assertNull(service.parse(ReportDTO.Category.FOOD, "죄송합니다. 리포트를 쓸 수 없습니다.", null));
        assertNull(service.parse(ReportDTO.Category.FOOD, "{}", null));
        assertNull(service.parse(ReportDTO.Category.HEALTH, "{\"note\": \"   \"}", null));
        assertNull(service.parse(ReportDTO.Category.FRIENDSHIP, "{\"sociabilityScore\": 80}", null));
        assertNull(service.parse(ReportDTO.Category.LEARNING, "{ 이건 JSON이 아닙니다 }", null));
    }

    // ---- staleness: when a written category is worth writing again ----

    private static ReportDTO written(Long createdAt) {
        ReportDTO report = new ReportDTO();
        report.setCreatedAt(createdAt);
        return report;
    }

    @Test
    void writesACategoryNobodyHasWrittenYet() {
        assertTrue(ReportService.isStale(null, 1_000L, 9));
        assertTrue(ReportService.isStale(written(null), 1_000L, 9));
    }

    @Test
    void leavesAReportAloneWhenNothingHasBeenSaidSince() {
        // Re-opening the reports screen must not spend five model calls on a child who has not
        // said a word since.
        assertFalse(ReportService.isStale(written(2_000L), 1_500L, 9));
        assertFalse(ReportService.isStale(written(2_000L), 2_000L, 9));
    }

    @Test
    void waitsForRealEvidenceBeforeWritingAgain() {
        // One more "응" is not a different report, and every rewrite costs five model calls.
        assertFalse(ReportService.isStale(written(1_000L), 2_000L, 3));
        assertTrue(ReportService.isStale(written(1_000L), 2_000L, 4));
    }
}
