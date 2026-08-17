package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationshipDTO {
    public enum Type {
        CHILD,
        TEACHER
    }

    private Long kindergartenId;
    private String kindergartenName;
    private String userId;
    private Type type;
    private Long classId;
    private Long roleId;
    private String roleName;
    private String nickname;
    private Long createdAt;
    private Long updatedAt;
    private Boolean exists;

    public static RelationshipDTO fromId(long kindergartenId, String userId) {
        RelationshipDTO result = new RelationshipDTO();
        result.setKindergartenId(kindergartenId);
        result.setUserId(userId);
        return result;
    }
}
