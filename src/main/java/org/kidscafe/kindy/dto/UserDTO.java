package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class UserDTO {
    private String id;
    private String name;
    private byte[] password;
    private byte[] email;
    private String addr1;
    private String addr2;
    private String registerDate;
    private boolean exists;
}
