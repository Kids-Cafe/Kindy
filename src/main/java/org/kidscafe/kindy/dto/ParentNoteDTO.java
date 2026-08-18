package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A note a teacher writes about one child, visible to that child's parents.
 * Parents (and the author) reply with {@link CommentDTO}.
 */
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParentNoteDTO {
    private Long id;
    private String childId;
    private String author;
    private String content;
    private Long createdAt;

    private String authorName;

    @Getter
    @Setter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CommentDTO {
        private Long id;
        private Long noteId;
        private String author;
        private String content;
        private Long createdAt;

        private String authorName;
    }
}
