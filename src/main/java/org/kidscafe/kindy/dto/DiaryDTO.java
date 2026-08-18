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
    private Long createdAt;
}
