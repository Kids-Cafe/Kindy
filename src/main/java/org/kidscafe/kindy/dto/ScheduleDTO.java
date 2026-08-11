package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleDTO {
    private Long id;
    private Long kindergartenId;
    private String date;
    private String time;
    private String title;
    private Long classId;
    private Long createdAt;
    private Long updatedAt;
}
