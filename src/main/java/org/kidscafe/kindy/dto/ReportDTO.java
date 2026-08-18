package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * One category of a child's report card. {@link #data} is the category's JSON blob, stored and
 * returned verbatim — the backend does not validate its shape against the frontend's report types.
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

    private String childId;
    private Category category;
    private String data;
    private Long createdAt;
    private Long updatedAt;
}
