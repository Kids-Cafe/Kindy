package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDTO {
    Long id;
    Long kindergartenId;
    String name;

    public enum Permission {
        MANAGE_NOTICE,
        MANAGE_CLASS,
        MANAGE_MEMBER,
        MANAGE_SCHEDULE,
        MANAGE_SUPPLY
    }

    public static class PermissionDTO {
        Long roleId;
        Permission permission;
    }
}
