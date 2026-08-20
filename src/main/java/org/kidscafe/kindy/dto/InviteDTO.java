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
    /** Real name of the account that would become a member. */
    private String userName;
    /**
     * Account type of {@link #userId}. TYPE alone cannot tell a child's own application from an
     * adult's, which is why the pending list used to label both of them "child · parent".
     */
    private UserDTO.AccountType accountType;
    private String inviterId;
    /** Real name of whoever filed the ticket — for a JOIN, often the applicant's guardian. */
    private String inviterName;
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
