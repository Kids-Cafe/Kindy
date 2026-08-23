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
    /**
     * The object key while the row is in flight, and the address the browser fetches once the album
     * has been through {@code ClassService.getPhotos}. Which one you are holding depends on where
     * it came from — see the javadoc on {@code getPhotos} and {@code getPhotoInfo}.
     */
    private String url;
    /** The same photo, downscaled for the album grid. Falls back to the original when there is none. */
    private String thumbUrl;
    private String caption;
    /** Decoration theme picked by the uploader: clip / polaroid / frame-wood / frame-gold. */
    private String theme;
    private String author;
    private Long createdAt;

    private String authorName;
}
