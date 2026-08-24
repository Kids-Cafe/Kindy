package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * One category of a child's report card. {@link #data} is the category's JSON blob, stored and
 * returned verbatim — the backend does not validate its shape against the frontend's report types.
 * <p>
 * Reports are append-only. Writing a category again inserts a new row rather than overwriting the
 * old one, so "a child's food report" is a series of versions and {@link #id} names one of them.
 * That identity is what lets a chat data card keep showing the report it was actually sent with:
 * a card stores an id, not a category, and a card from last March is still last March's report.
 */
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportDTO {
    public enum Category {
        FOOD,
        HEALTH,
        FRIENDSHIP,
        PERSONALITY,
        LEARNING
    }

    /** This version's id. Assigned by the database on insert; never reused, never rewritten. */
    private Long id;
    private String childId;
    private Category category;
    private String data;
    /**
     * When this version was written.
     *
     * There is no UPDATED_AT: the row is immutable, so there is nothing for one to mean. This is
     * also what {@code ReportService.isStale} compares the child's records against — see
     * docs/migration-report-identity.sql PHASE 2 for why the old column had to go rather than stay
     * as a harmless duplicate.
     */
    private Long createdAt;
}
