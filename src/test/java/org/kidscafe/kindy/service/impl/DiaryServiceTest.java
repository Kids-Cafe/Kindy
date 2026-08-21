package org.kidscafe.kindy.service.impl;

import org.junit.jupiter.api.Test;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.DiaryDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two decisions the diary generator makes on its own.
 *
 * <b>Reading the model's answer</b> — the entries are written by a 3B model, and every string in
 * the first half is a shape one has actually come back with.
 * <p>
 * <b>Deciding whether to write a day again</b> — the second half. Today's diary has to keep up
 * with a child who is still talking, without ever overwriting a day somebody wrote themselves.
 * <p>
 * Nothing here talks to the model, the database or Spring: the methods under test are pure, and
 * the service is built with null collaborators because they never reach them.
 */
class DiaryServiceTest {
    private final DiaryService service = new DiaryService(null, null);

    @Test
    void readsTheOrdinaryAnswer() {
        DiaryDTO entry = service.parse("""
                {"mood": "happy", "title": "동물원 소풍", "summary": "동물원에 갔어.",
                 "text": "나는 오늘 소풍을 갔어. 기린이 정말 컸어.", "tags": ["소풍", "동물원"]}""");

        assertNotNull(entry);
        assertEquals(DiaryDTO.Mood.happy, entry.getMood());
        assertEquals("동물원 소풍", entry.getTitle());
        assertEquals("동물원에 갔어.", entry.getSummary());
        assertEquals("나는 오늘 소풍을 갔어. 기린이 정말 컸어.", entry.getText());
        assertEquals(List.of("소풍", "동물원"), entry.getTags());
    }

    @Test
    void findsTheObjectInsideAFenceOrAPreamble() {
        DiaryDTO entry = service.parse("""
                물론이죠! 오늘의 일기입니다.
                ```json
                {"mood": "calm", "title": "조용한 하루", "text": "나는 오늘 책을 읽었어."}
                ```
                도움이 되었길 바랍니다.""");

        assertNotNull(entry);
        assertEquals("조용한 하루", entry.getTitle());
        assertEquals("나는 오늘 책을 읽었어.", entry.getText());
    }

    @Test
    void fallsBackToCalmOnAMoodItInvented() {
        DiaryDTO entry = service.parse("{\"mood\": \"기쁨\", \"text\": \"나는 오늘 놀았어.\"}");

        assertNotNull(entry);
        assertEquals(DiaryDTO.Mood.calm, entry.getMood());
    }

    @Test
    void acceptsTagsAsOneCommaSeparatedString() {
        // Asked for an array, a small model regularly answers with the string it would have joined.
        DiaryDTO entry = service.parse("{\"text\": \"나는 오늘 놀았어.\", \"tags\": \"#소풍, 동물원 ,, 기린\"}");

        assertNotNull(entry);
        assertEquals(List.of("소풍", "동물원", "기린"), entry.getTags());
    }

    @Test
    void usesTheSummaryAsTheBodyWhenOnlyASummaryCameBack() {
        // CONTENT is NOT NULL. A summary-only answer still wrote the day down.
        DiaryDTO entry = service.parse("{\"mood\": \"sad\", \"summary\": \"오늘은 속상했어.\"}");

        assertNotNull(entry);
        assertEquals("오늘은 속상했어.", entry.getText());
        assertEquals("오늘은 속상했어.", entry.getSummary());
        // No title either, so the summary stands in — an untitled card is worse than a repeated line.
        assertEquals("오늘은 속상했어.", entry.getTitle());
    }

    @Test
    void keepsTheTitleWithinItsColumn() {
        String long_ = "가".repeat(400);
        DiaryDTO entry = service.parse("{\"title\": \"" + long_ + "\", \"text\": \"나는 오늘 놀았어.\"}");

        assertNotNull(entry);
        assertEquals(256, entry.getTitle().length());
    }

    @Test
    void capsTheTagsAndDropsDuplicates() {
        DiaryDTO entry = service.parse(
                "{\"text\": \"나는 오늘 놀았어.\", \"tags\": [\"놀이\", \"놀이\", \"a\", \"b\", \"c\", \"d\", \"e\"]}");

        assertNotNull(entry);
        assertTrue(entry.getTags().size() <= 5, entry.getTags().toString());
        assertEquals(List.of("놀이", "a", "b", "c", "d"), entry.getTags());
    }

    // ---- staleness: when a written day is worth writing again ----

    private static DiaryDTO written(Long sourceAt) {
        DiaryDTO entry = new DiaryDTO();
        entry.setSourceAt(sourceAt);
        return entry;
    }

    private static ChatDTO.MessageDTO saidAt(long at) {
        ChatDTO.MessageDTO m = new ChatDTO.MessageDTO();
        m.setCreatedAt(at);
        m.setRole(ChatDTO.MessageDTO.Role.user);
        m.setType(ChatDTO.MessageDTO.Type.TEXT);
        return m;
    }

    @Test
    void rewritesTodayOnceTheChildHasKeptTalking() {
        // The whole point: the entry was written at noon, the child talked again at three.
        DiaryDTO noon = written(1_200L);

        assertTrue(DiaryService.isStale(noon, 1_500L));
        assertTrue(DiaryService.isStale(noon, List.of(saidAt(1_000L), saidAt(1_400L), saidAt(1_500L))));
    }

    @Test
    void leavesAnEntryAloneWhenNobodyHasSaidAnythingSince() {
        // Re-opening the diary screen must not spend a model call on a day that has not moved.
        DiaryDTO current = written(1_500L);

        assertFalse(DiaryService.isStale(current, 1_500L));
        assertFalse(DiaryService.isStale(current, List.of(saidAt(1_000L), saidAt(1_500L))));
    }

    @Test
    void waitsForARealExchangeBeforeRewriting() {
        // One more turn is not a different day, and every rewrite costs a model call.
        DiaryDTO entry = written(1_500L);

        assertFalse(DiaryService.isStale(entry, List.of(saidAt(1_500L), saidAt(1_600L))));
        assertTrue(DiaryService.isStale(entry, List.of(saidAt(1_500L), saidAt(1_600L), saidAt(1_700L))));
    }

    @Test
    void neverRewritesADayAPersonWrote() {
        // A null SOURCE_AT is what user/diary/create and user/diary/modify leave behind. No amount
        // of later conversation makes somebody's own words ours to replace.
        DiaryDTO mine = written(null);

        assertFalse(DiaryService.isStale(mine, Long.MAX_VALUE));
        assertFalse(DiaryService.isStale(mine, List.of(saidAt(1L), saidAt(2L), saidAt(3L), saidAt(4L))));
    }

    @Test
    void refusesAnAnswerWithNoDiaryInIt() {
        // Nothing to store, and an empty entry on the child's diary is worse than no entry.
        assertNull(service.parse("죄송합니다. 일기를 쓸 수 없습니다."));
        assertNull(service.parse("{\"mood\": \"happy\"}"));
        assertNull(service.parse("{\"text\": \"   \"}"));
        assertNull(service.parse("{ 이건 JSON이 아닙니다 }"));
    }
}
