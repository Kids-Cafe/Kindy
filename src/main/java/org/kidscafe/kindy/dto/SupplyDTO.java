package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplyDTO {
    private Long id;
    private Long classId;
    private String date;
    private String title;
    private String content;
    private Long createdAt;
    private Long updatedAt;
}
