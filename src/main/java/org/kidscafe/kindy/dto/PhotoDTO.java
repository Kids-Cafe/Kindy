package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhotoDTO {
    private Long id;
    private Long classId;
    private String url;
    private String caption;
    /** Decoration theme picked by the uploader: clip / polaroid / frame-wood / frame-gold. */
    private String theme;
    private String author;
    private Long createdAt;

    private String authorName;
}
