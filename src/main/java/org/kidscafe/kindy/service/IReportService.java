package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ReportDTO;

import java.util.List;

/**
 * Writes a child's growth report from what they told their AI partner and from their diary.
 *
 * The reports themselves are stored and read through {@link IUserService} — this only produces
 * them. Each of the five {@link ReportDTO.Category categories} is one row and one model call, and
 * {@code DATA} is a JSON blob whose shape the backend never validates: matching what the screen
 * expects is entirely this service's job.
 * <p>
 * A category it has already written it will write again once enough has been said since, which is
 * what keeps a report from freezing at whatever the child had told their partner in their first
 * week. {@code T_CHILD_REPORT.CREATED_AT} is how far the last write got.
 * <p>
 * <b>Writing again does not erase.</b> Each write inserts a new row with its own id and leaves every
 * earlier version in place, so "the child's food report" is the newest of a series rather than a
 * blob that changes underneath whoever is holding it. That matters off this screen: a chat data card
 * stores the report id it was sent with, so a card from last March still shows March's numbers
 * instead of quietly restating itself in today's terms. See docs/migration-report-identity.sql.
 * <p>
 * <b>What it still cannot do:</b> tell a generated report from one a person saved. The diary has
 * {@code SOURCE_AT} for exactly that, and reports have no such column — so a hand-saved report would
 * be written past. It is no longer destroyed, which makes this survivable rather than merely
 * unexercised ({@code user/report/save} still has no caller), but a screen that lets a teacher edit
 * a report needs the column so the overwrite does not happen at all.
 */
public interface IReportService {
    /**
     * Writes every category that is missing or has fallen behind the child's records.
     *
     * Returns what was actually written, which is empty when there was nothing to write — a child
     * with too little conversation, or one whose reports are all current. That is the ordinary
     * outcome, not an error. {@code force} rewrites all five regardless.
     */
    List<ReportDTO> generateAll(String childId, boolean force) throws Exception;

    /**
     * One category. Returns null when there is nothing to write — too little to go on, the report
     * is already current, or the model answered with nothing usable. {@code force} skips only the
     * "already current" test: a child who has barely spoken still gets no report, because the only
     * way to fill five categories out of four sentences is to invent them.
     */
    ReportDTO generate(String childId, ReportDTO.Category category, boolean force) throws Exception;
}
