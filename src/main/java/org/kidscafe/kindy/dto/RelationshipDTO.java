package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

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
    private String userName;
    private Type type;
    private Long classId;
    // Legacy single-role column on T_RELATIONSHIP. Still written by assign() so anything reading
    // it keeps working; roleIds is the authoritative set (T_RELATIONSHIP_ROLE).
    private Long roleId;
    private String roleName;
    private List<Long> roleIds;
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
