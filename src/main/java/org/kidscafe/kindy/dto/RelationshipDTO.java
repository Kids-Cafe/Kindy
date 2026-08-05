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

    Long kindergartenId;
    String userId;
    Type type;
    Long roleId;
    String nickname;
    Long createdAt;
    Long updatedAt;
}
