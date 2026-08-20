package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    public enum AccountType {
        ADULT,
        CHILD
    }

    // UNSPECIFIED mirrors the third value the T_USER.GENDER column allows. Without it, reading a
    // row that holds it fails inside MyBatis's enum type handler rather than at the column, so
    // user/info answers 500 for that user and nothing else explains why.
    public enum Gender {
        MALE,
        FEMALE,
        UNSPECIFIED
    }

    private String id;
    private String name;
    private byte[] password;
    private byte[] passwordSalt;
    private String email;
    private String phone;
    private byte[] address;
    private byte[] addressDetail;
    private byte[] postcode;
    private AccountType accountType;
    private String birthDate;
    private Gender gender;
    private String guardianName;
    private String guardianPhone;
    private Boolean onboardingCompleted;
    private Long createdAt;
    private Long updatedAt;
    private Boolean exists;
    public static UserDTO fromId(String id) {
        UserDTO result = new UserDTO();
        result.setId(id);
        return result;
    }

    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlainUserDTO {
        private String id;
        private String name;
        private String email;
        private String phone;
        private String address;
        private String addressDetail;
        private String postcode;
        private AccountType accountType;
        private String birthDate;
        private Gender gender;
        private String guardianName;
        private String guardianPhone;
        private Boolean onboardingCompleted;
        private Long createdAt;
        private Long updatedAt;
    }
}
