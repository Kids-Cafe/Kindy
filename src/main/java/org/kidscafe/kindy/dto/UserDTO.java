package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String name;
    private byte[] password;
    private byte[] passwordSalt;
    private String email;
    private byte[] address;
    private byte[] addressDetail;
    private byte[] postcode;
    private Long createdAt;
    private Long updatedAt;
    private Boolean exists;
    public static UserDTO fromId(String id) {
        UserDTO result = new UserDTO();
        result.setId(id);
        return result;
    }
}
