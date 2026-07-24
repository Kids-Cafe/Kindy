package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String name;
    private byte[] password;
    private byte[] email;
    private byte[] addr1;
    private byte[] addr2;
    private String registerDate;
    private boolean exists;
    public static UserDTO fromId(String id) {
        UserDTO result = new UserDTO();
        result.setId(id);
        return result;
    }
}
