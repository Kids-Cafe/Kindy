package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.DiaryDTO;

import java.util.List;

/**
 * Writes a child's diary from what they told their AI partner.
 *
 * The diary itself is stored and read through {@link IUserService} — this only produces entries.
 * <p>
 * A day it has already written it will write again once the child says more, which is what keeps
 * today's diary — begun while the day is still going — from stopping at whatever had been said by
 * mid-morning. What it never touches is a day a person wrote or edited: {@code T_DIARY.SOURCE_AT}
 * records how far into the conversation an entry was generated from, and the two human-facing
 * endpoints ({@code user/diary/create}, {@code user/diary/modify}) store null there, which reads
 * as "this one is not ours to rewrite".
 */
public interface IDiaryService {
    /**
     * Writes a diary for every day the child has talked to their partner and has no entry yet —
     * or has one that the conversation has since outgrown — newest day first.
     *
     * Days with too little conversation to say anything about are skipped, not written as empty
     * entries. Returns what was actually written, which is empty when there was nothing to write.
     * At most {@link #MAX_DAY_ATTEMPTS} days are done in one call so a single request can't hang
     * on a hundred model calls; the rest are picked up by the next one.
     */
    List<DiaryDTO> generateAll(String userId) throws Exception;

    /**
     * One day. Returns null when that day has no diary to write — too little was said, the entry
     * is already current, it belongs to a person, or the model answered with nothing usable.
     * {@code force} rewrites the entry regardless, and is the only way to overwrite a hand-written
     * one.
     */
    DiaryDTO generate(String userId, String date, boolean force) throws Exception;

    /**
     * How many days one {@link #generateAll} call will take on before leaving the rest for later.
     *
     * Attempts, not entries: the point is to bound how many model calls one HTTP request makes,
     * and a day that came back unusable cost that call just the same.
     */
    int MAX_DAY_ATTEMPTS = 7;
}
