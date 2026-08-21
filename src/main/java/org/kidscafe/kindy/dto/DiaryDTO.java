package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryDTO {
    public enum Mood {
        happy,
        excited,
        calm,
        sad,
        upset
    }

    private Long id;
    private String userId;
    private String date;
    private Mood mood;
    private String title;
    /** Short AI-written recap shown on the diary card. */
    private String summary;
    /** Full diary body. Stored in T_DIARY.CONTENT. */
    private String text;
    /** @deprecated single-tag column kept for existing rows; use {@link #tags}. */
    @Deprecated
    private String tag;
    private List<String> tags;
    /**
     * How far into the day's conversation this entry was written from — the CREATED_AT, in epoch
     * milliseconds, of the newest chat message it saw.
     *
     * Null means a person wrote or edited it, and the generator leaves those alone forever. A
     * value older than the day's newest message means the child kept talking after it was written,
     * which is the ordinary state of today's diary and the one case worth rewriting.
     */
    private Long sourceAt;
    private Long createdAt;
    private Long updatedAt;
}
