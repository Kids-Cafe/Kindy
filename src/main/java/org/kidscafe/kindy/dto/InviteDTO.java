package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteDTO {
    public enum Direction {
        INVITE,
        JOIN
    }

    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELED
    }

    private Long id;
    private Long kindergartenId;
    private String kindergartenName;
    private String userId;
    private String inviterId;
    private RelationshipDTO.Type type;
    private Long roleId;
    private Direction direction;
    private Status status;
    private Long createdAt;
    private Long updatedAt;

    public static InviteDTO fromId(long id) {
        InviteDTO result = new InviteDTO();
        result.setId(id);
        return result;
    }
}
