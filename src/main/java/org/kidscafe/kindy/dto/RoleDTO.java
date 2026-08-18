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
public class RoleDTO {
    private Long id;
    private Long kindergartenId;
    private String name;
    private String color;

    public enum Permission {
        MANAGE_NOTICE,
        /** Creating, renaming and deleting classes, and assigning members to them. */
        MANAGE_CLASS,
        MANAGE_MEMBER,
        MANAGE_SCHEDULE,
        MANAGE_SUPPLY,
        /** Uploading, editing and removing class photos. Separate from MANAGE_CLASS so a
         *  teacher can be trusted with the album without being able to restructure classes. */
        MANAGE_PHOTO
    }

    @Getter
    @ToString
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PermissionDTO {
        Long roleId;
        Permission permission;
    }
}
