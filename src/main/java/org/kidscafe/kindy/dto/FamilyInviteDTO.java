package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A pending request to create a {@link FamilyDTO} row, answered by someone other than whoever
 * asked for it.
 * <p>
 * There is no direction field. Three different situations produce a request — an adult claiming to
 * be a parent, a child asking an adult, an already-linked parent proposing a co-parent — and which
 * one this is falls out of comparing {@code requesterId} against {@code parent} and {@code child}.
 * A two-valued enum like {@link InviteDTO.Direction} cannot hold the third case.
 */
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FamilyInviteDTO {
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELED
    }

    private Long id;
    private String parent;
    private String child;
    private String requesterId;
    private String parentName;
    private String childName;
    private String requesterName;
    private Status status;
    /**
     * Whether the account that asked for this list may answer this particular request. Filled in
     * by the service, because the rule needs the child's current parents and the client cannot see
     * those. Without it every screen would have to re-derive an authorization rule it has no data
     * for, and would get it wrong.
     */
    private Boolean canRespond;
    private Long createdAt;
    private Long updatedAt;

    public static FamilyInviteDTO fromId(long id) {
        FamilyInviteDTO result = new FamilyInviteDTO();
        result.setId(id);
        return result;
    }
}
