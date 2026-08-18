package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.RoleDTO;

import java.util.Set;

/**
 * Answers "may this user do this?" for the controllers. Every check fails closed: a lookup that
 * blows up or a row that isn't there reads as "no", never as "yes".
 * <p>
 * The model is: the kindergarten's OWNER may do everything inside it; everyone else derives their
 * rights from the roles they hold there (T_RELATIONSHIP_ROLE plus the legacy scalar ROLE_ID), and
 * plain membership only ever grants read access. A parent inherits read access to the
 * kindergartens their children belong to, which is how {@code getMemberships} already behaves.
 * <p>
 * Nothing here declares checked exceptions, so the checks can sit at the top of a controller
 * method next to the session guard instead of inside its try block.
 */
public interface IAccessService {
    /** The user's own membership row, or null if they aren't a member. */
    RelationshipDTO getMembership(long kindergartenId, String userId);

    boolean isOwner(long kindergartenId, String userId);

    /** Owner or a T_RELATIONSHIP row of any type. */
    boolean isMember(long kindergartenId, String userId);

    boolean isTeacher(long kindergartenId, String userId);

    /** Read access: {@link #isMember} plus parents of member children. */
    boolean canView(long kindergartenId, String userId);

    /** Every permission the user's roles grant in this kindergarten; all of them for the owner. */
    Set<RoleDTO.Permission> getPermissions(long kindergartenId, String userId);

    boolean hasPermission(long kindergartenId, String userId, RoleDTO.Permission permission);

    /** Read access to a class — i.e. to the kindergarten that owns it. */
    boolean canViewClass(long classId, String userId);

    /** {@code permission} on the kindergarten that owns the class. */
    boolean canManageClass(long classId, String userId, RoleDTO.Permission permission);

    // Owner lookups, so a controller holding only a leaf id can find the kindergarten to check
    // against. All return null when the row is gone.
    Long getKindergartenOfRole(long roleId);

    Long getKindergartenOfClass(long classId);

    Long getKindergartenOfNotice(long noticeId);

    Long getKindergartenOfSchedule(long scheduleId);

    Long getClassOfSupply(long supplyId);

    Long getClassOfPhoto(long photoId);

    Long getSupplyOfComment(long commentId);

    String getChildOfNote(long noteId);

    Long getNoteOfComment(long commentId);

    boolean isParentOf(String userId, String childId);

    /** The child themself, one of their parents, or a teacher at a kindergarten they attend. */
    boolean canViewChild(String userId, String childId);

    /** Write access to a child's records: a parent or a teacher, but not the child themself. */
    boolean canManageChild(String userId, String childId);
}
