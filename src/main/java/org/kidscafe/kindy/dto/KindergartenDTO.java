package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KindergartenDTO {
    private Long id;
    private String name;
    private String brn;
    private String owner;
    private String address;
    private String addressDetail;
    private String postcode;
    private Long createdAt;
    private Long updatedAt;

    public static KindergartenDTO fromId(long id) {
        KindergartenDTO result = new KindergartenDTO();
        result.setId(id);
        return result;
    }
}
