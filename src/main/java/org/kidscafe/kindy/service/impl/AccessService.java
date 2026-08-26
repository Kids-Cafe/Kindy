package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.mapper.*;
import org.kidscafe.kindy.service.IAccessService;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccessService implements IAccessService {
    private final IKindergartenMapper kindergartenMapper;
    private final IRelationshipMapper relationshipMapper;
    private final IPermissionMapper permissionMapper;
    private final IRoleMapper roleMapper;
    private final IFamilyMapper familyMapper;
    private final IClassMapper classMapper;
    private final INoticeMapper noticeMapper;
    private final IScheduleMapper scheduleMapper;
    private final ISupplyMapper supplyMapper;
    private final ISupplyCommentMapper supplyCommentMapper;
    private final IPhotoMapper photoMapper;
    private final IParentNoteMapper parentNoteMapper;

    @Override
    public RelationshipDTO getMembership(long kindergartenId, String userId) {
        if (userId == null) return null;

        try {
            return relationshipMapper.getInfo(RelationshipDTO.fromId(kindergartenId, userId));
        } catch (Exception e) {
            log.warn("getMembership failed — denying", e);
            return null;
        }
    }

    @Override
    public boolean isOwner(long kindergartenId, String userId) {
        if (userId == null) return false;

        try {
            KindergartenDTO kindergarten = kindergartenMapper.getInfo(KindergartenDTO.fromId(kindergartenId));
            return kindergarten != null && userId.equals(kindergarten.getOwner());
        } catch (Exception e) {
            log.warn("isOwner failed — denying", e);
            return false;
        }
    }

    @Override
    public boolean isMember(long kindergartenId, String userId) {
        return getMembership(kindergartenId, userId) != null || isOwner(kindergartenId, userId);
    }

    @Override
    public boolean isTeacher(long kindergartenId, String userId) {
        RelationshipDTO membership = getMembership(kindergartenId, userId);
        return (membership != null && membership.getType() == RelationshipDTO.Type.TEACHER)
                || isOwner(kindergartenId, userId);
    }

    @Override
    public boolean canView(long kindergartenId, String userId) {
        if (isMember(kindergartenId, userId)) return true;

        // A parent sees whatever their child's membership exposes — the same widening
        // getMemberships() applies.
        for (String child : this.getChildren(userId)) {
            if (getMembership(kindergartenId, child) != null) return true;
        }

        return false;
    }

    @Override
    public Set<RoleDTO.Permission> getPermissions(long kindergartenId, String userId) {
        if (userId == null) return EnumSet.noneOf(RoleDTO.Permission.class);
        if (isOwner(kindergartenId, userId)) return EnumSet.allOf(RoleDTO.Permission.class);

        try {
            Set<RoleDTO.Permission> result = EnumSet.noneOf(RoleDTO.Permission.class);
            for (RoleDTO.PermissionDTO row : permissionMapper.getListByUser(RelationshipDTO.fromId(kindergartenId, userId))) {
                if (row.getPermission() != null) result.add(row.getPermission());
            }
            return result;
        } catch (Exception e) {
            log.warn("getPermissions({}, {}) failed — denying", kindergartenId, userId, e);
            return EnumSet.noneOf(RoleDTO.Permission.class);
        }
    }

    @Override
    public boolean hasPermission(long kindergartenId, String userId, RoleDTO.Permission permission) {
        return this.getPermissions(kindergartenId, userId).contains(permission);
    }

    @Override
    public boolean canViewClass(long classId, String userId) {
        Long kindergartenId = this.getKindergartenOfClass(classId);
        return kindergartenId != null && this.canView(kindergartenId, userId);
    }

    @Override
    public boolean canManageClass(long classId, String userId, RoleDTO.Permission permission) {
        Long kindergartenId = this.getKindergartenOfClass(classId);
        return kindergartenId != null && this.hasPermission(kindergartenId, userId, permission);
    }

    @Override
    public Long getKindergartenOfRole(long roleId) {
        try {
            RoleDTO role = new RoleDTO();
            role.setId(roleId);
            role = roleMapper.getInfo(role);
            return role == null ? null : role.getKindergartenId();
        } catch (Exception e) {
            log.warn("getKindergartenOfRole failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getKindergartenOfClass(long classId) {
        try {
            ClassDTO pDTO = new ClassDTO();
            pDTO.setId(classId);
            ClassDTO rDTO = classMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getKindergartenId();
        } catch (Exception e) {
            log.warn("getKindergartenOfClass failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getKindergartenOfNotice(long noticeId) {
        try {
            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setId(noticeId);
            NoticeDTO rDTO = noticeMapper.getInfo(pDTO);
            return rDTO == null ? null : rDTO.getKindergartenId();
        } catch (Exception e) {
            log.warn("getKindergartenOfNotice failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getKindergartenOfSchedule(long scheduleId) {
        try {
            ScheduleDTO pDTO = new ScheduleDTO();
            pDTO.setId(scheduleId);
            ScheduleDTO rDTO = scheduleMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getKindergartenId();
        } catch (Exception e) {
            log.warn("getKindergartenOfSchedule failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getClassOfSupply(long supplyId) {
        try {
            SupplyDTO pDTO = new SupplyDTO();
            pDTO.setId(supplyId);
            SupplyDTO rDTO = supplyMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getClassId();
        } catch (Exception e) {
            log.warn("getClassOfSupply failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getClassOfPhoto(long photoId) {
        try {
            PhotoDTO pDTO = new PhotoDTO();
            pDTO.setId(photoId);
            PhotoDTO rDTO = photoMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getClassId();
        } catch (Exception e) {
            log.warn("getClassOfPhoto failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getSupplyOfComment(long commentId) {
        try {
            SupplyDTO.CommentDTO pDTO = new SupplyDTO.CommentDTO();
            pDTO.setId(commentId);
            SupplyDTO.CommentDTO rDTO = supplyCommentMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getSupplyId();
        } catch (Exception e) {
            log.warn("getSupplyOfComment failed — denying", e);
            return null;
        }
    }

    @Override
    public String getChildOfNote(long noteId) {
        try {
            ParentNoteDTO pDTO = new ParentNoteDTO();
            pDTO.setId(noteId);
            ParentNoteDTO rDTO = parentNoteMapper.select(pDTO);
            return rDTO == null ? null : rDTO.getChildId();
        } catch (Exception e) {
            log.warn("getChildOfNote failed — denying", e);
            return null;
        }
    }

    @Override
    public Long getNoteOfComment(long commentId) {
        try {
            ParentNoteDTO.CommentDTO pDTO = new ParentNoteDTO.CommentDTO();
            pDTO.setId(commentId);
            ParentNoteDTO.CommentDTO rDTO = parentNoteMapper.selectComment(pDTO);
            return rDTO == null ? null : rDTO.getNoteId();
        } catch (Exception e) {
            log.warn("getNoteOfComment failed — denying", e);
            return null;
        }
    }

    @Override
    public boolean isParentOf(String userId, String childId) {
        if (userId == null || childId == null) return false;

        try {
            FamilyDTO pDTO = new FamilyDTO();
            pDTO.setParent(userId);
            pDTO.setChild(childId);
            return familyMapper.select(pDTO) != null;
        } catch (Exception e) {
            log.warn("isParentOf({}, {}) failed — denying", userId, childId, e);
            return false;
        }
    }

    @Override
    public boolean canViewChild(String userId, String childId) {
        if (userId == null || childId == null) return false;
        if (userId.equals(childId)) return true;

        return this.canManageChild(userId, childId);
    }

    @Override
    public boolean canManageChild(String userId, String childId) {
        if (userId == null || childId == null) return false;
        if (userId.equals(childId)) return false;
        if (this.isParentOf(userId, childId)) return true;

        // Otherwise the caller has to teach at one of the kindergartens the child attends.
        try {
            RelationshipDTO query = new RelationshipDTO();
            query.setUserId(childId);
            for (RelationshipDTO membership : relationshipMapper.getListByUser(query)) {
                if (membership.getKindergartenId() == null) continue;
                if (this.isTeacher(membership.getKindergartenId(), userId)) return true;
            }
        } catch (Exception e) {
            log.warn("canManageChild({}, {}) failed — denying", userId, childId, e);
        }

        return false;
    }

    /** Ids of the users this user is registered as a parent of. */
    private List<String> getChildren(String userId) {
        if (userId == null) return List.of();

        try {
            FamilyDTO query = new FamilyDTO();
            query.setParent(userId);
            query.setChild(userId);
            // selectList matches on either side, so drop the rows where the user is the child.
            return familyMapper.selectList(query).stream()
                    .filter(f -> userId.equals(f.getParent()))
                    .map(FamilyDTO::getChild)
                    .toList();
        } catch (Exception e) {
            log.warn("getChildren failed — denying", e);
            return List.of();
        }
    }
}
