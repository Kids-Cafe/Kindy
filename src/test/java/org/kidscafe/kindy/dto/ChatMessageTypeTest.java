package org.kidscafe.kindy.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one hand-written translation between the two vocabularies.
 *
 * {@code T_CHAT_MESSAGE.TYPE} says FRIEND and STUDY where {@code T_CHILD_REPORT.CATEGORY} says
 * FRIENDSHIP and LEARNING, so the mapping cannot be {@code valueOf(type.name())} and has to be
 * written out. Getting it wrong is quiet and bad: {@code chat/send} pins whatever category this
 * returns, so a card labelled 식사 would render — and permanently store a pointer to — a 건강 report.
 * The same pairing is spelled out once more in docs/migration-report-identity.sql PHASE 4, which
 * back-fills old cards, and the two must agree.
 */
class ChatMessageTypeTest {
    @Test
    void textIsNotAReport() {
        assertNull(ChatDTO.MessageDTO.Type.TEXT.category());
    }

    @Test
    void everyCardTypeNamesItsCategory() {
        assertEquals(ReportDTO.Category.FOOD, ChatDTO.MessageDTO.Type.FOOD.category());
        assertEquals(ReportDTO.Category.HEALTH, ChatDTO.MessageDTO.Type.HEALTH.category());
        assertEquals(ReportDTO.Category.PERSONALITY, ChatDTO.MessageDTO.Type.PERSONALITY.category());
        // The two that do not share a name.
        assertEquals(ReportDTO.Category.FRIENDSHIP, ChatDTO.MessageDTO.Type.FRIEND.category());
        assertEquals(ReportDTO.Category.LEARNING, ChatDTO.MessageDTO.Type.STUDY.category());
    }

    @Test
    void theTwoEnumsStayInStep() {
        // A category added on one side and forgotten on the other leaves either a report no card can
        // show or a card with nothing to pin. Both fail silently at runtime; this fails at build.
        Set<ReportDTO.Category> mapped = Arrays.stream(ChatDTO.MessageDTO.Type.values())
                .map(ChatDTO.MessageDTO.Type::category)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertEquals(Set.of(ReportDTO.Category.values()), mapped);
        // And no two card types point at the same report, which would leave one unreachable.
        assertEquals(ChatDTO.MessageDTO.Type.values().length - 1, mapped.size());
    }
}
