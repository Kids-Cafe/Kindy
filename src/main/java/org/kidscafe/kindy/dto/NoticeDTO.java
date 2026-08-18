package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoticeDTO {
    private Long id;
    private Long kindergartenId;
    private Integer num;
    private String title;
    private String content;
    private String author;
    private Boolean pinned;
    private Boolean bannerEnabled;
    private Long createdAt;
    private Long updatedAt;

    private String authorName;
}
