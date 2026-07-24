package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultDTO {
    public enum Status {
        success,
        error
    }

    private Status status;
    private String code;
    private Object data;

    public static ResultDTO success(String code, Object data) {
        return new ResultDTO(Status.success, code, data);
    }

    public static ResultDTO success(String code) {
        return success(code, null);
    }

    public static ResultDTO error(String code, Object data) {
        return new ResultDTO(Status.error, code, data);
    }

    public static ResultDTO error(String code) {
        return error(code, null);
    }
}
