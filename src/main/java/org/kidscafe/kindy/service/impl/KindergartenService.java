package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.mapper.*;
import org.kidscafe.kindy.service.IAccessService;
import org.kidscafe.kindy.service.IKindergartenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class KindergartenService implements IKindergartenService {
    private final IKindergartenMapper kindergartenMapper;
    private final IRelationshipMapper relationshipMapper;
    private final IRoleMapper roleMapper;
    private final IPermissionMapper permissionMapper;
    private final INoticeMapper noticeMapper;
    private final IScheduleMapper scheduleMapper;
    private final IFamilyMapper familyMapper;
    private final IInviteMapper inviteMapper;
    private final IUserMapper userMapper;
    private final IAccessService accessService;

    @Override
    public List<KindergartenDTO> getList(String q) throws Exception {
        log.info("Calling getList");

        return kindergartenMapper.getList(q);
    }

    @Override
    public KindergartenDTO getInfo(long id) throws Exception {
        log.info("Calling getInfo");

        return kindergartenMapper.getInfo(KindergartenDTO.fromId(id));
    }

    @Override
    public KindergartenDTO getInfoByBrn(String brn) throws Exception {
        log.info("Calling getInfoByBrn");

        KindergartenDTO pDTO = new KindergartenDTO();
        pDTO.setBrn(brn);

        return kindergartenMapper.getInfo(pDTO);
    }

    /**
     * Creates the kindergarten and enrols its owner as a member in the same transaction.
     * <p>
     * Access checks already treat the owner as a member ({@link IAccessService#isOwner}), but the
     * <em>listings</em> — getMembers / getMemberships — read T_RELATIONSHIP directly, so an owner
     * without a row would be missing from their own member list and would never see the
     * kindergarten in their workspace list.
     */
    @Transactional
    @Override
    public int create(KindergartenDTO pDTO) throws Exception {
        log.info("Calling create");

        int result = kindergartenMapper.insert(pDTO);
        if (result == 1 && pDTO.getId() != null && pDTO.getOwner() != null) {
            this.enrolOwner(pDTO.getId(), pDTO.getOwner());
        }
        return result;
    }

    /** Gives the owner a TEACHER membership if they don't already have one of any type. */
    private void enrolOwner(long kindergartenId, String owner) throws Exception {
        RelationshipDTO pDTO = RelationshipDTO.fromId(kindergartenId, owner);
        pDTO.setType(RelationshipDTO.Type.TEACHER);
        relationshipMapper.insertIfAbsent(pDTO);
    }

    @Transactional
    @Override
    public int update(KindergartenDTO pDTO) throws Exception {
        log.info("Calling update");

        return kindergartenMapper.update(pDTO);
    }

    @Transactional
    @Override
    public int transfer(long id, String userId) throws Exception {
        log.info("Calling transfer");

        KindergartenDTO pDTO = KindergartenDTO.fromId(id);
        pDTO.setOwner(userId);

        int result = kindergartenMapper.updateOwner(pDTO);
        // The controller only lets an existing member receive the kindergarten, so this is a
        // safeguard rather than the normal path.
        if (result == 1) this.enrolOwner(id, userId);
        return result;
    }

    @Override
    public List<RelationshipDTO> getMembers(long id) throws Exception {
        log.info("Calling getMembers");

        RelationshipDTO pDTO = new RelationshipDTO();
        pDTO.setKindergartenId(id);

        List<RelationshipDTO> result = new java.util.ArrayList<>(relationshipMapper.getList(pDTO));

        // Kindergartens created before owners were auto-enrolled have no row for their owner.
        // They can still manage the place, so leaving them out of its member list is misleading.
        KindergartenDTO kindergarten = kindergartenMapper.getInfo(KindergartenDTO.fromId(id));
        if (kindergarten != null && kindergarten.getOwner() != null
                && result.stream().noneMatch(r -> kindergarten.getOwner().equals(r.getUserId()))) {
            result.add(this.impliedOwnerMembership(id, kindergarten.getName(), kindergarten.getOwner()));
        }

        return result;
    }

    /**
     * A stand-in membership for an owner who has no T_RELATIONSHIP row, so listings agree with
     * what {@link IAccessService} already allows. Nothing is written to the database here — the
     * row itself is created by {@code create()} for new kindergartens and by the back-fill
     * migration for existing ones.
     */
    private RelationshipDTO impliedOwnerMembership(long kindergartenId, String kindergartenName, String owner) {
        RelationshipDTO rDTO = RelationshipDTO.fromId(kindergartenId, owner);
        rDTO.setKindergartenName(kindergartenName);
        rDTO.setType(RelationshipDTO.Type.TEACHER);
        rDTO.setRoleIds(List.of());
        try {
            UserDTO user = userMapper.getInfo(UserDTO.fromId(owner));
            if (user != null) rDTO.setUserName(user.getName());
        } catch (Exception e) {
            log.info("impliedOwnerMembership name lookup failed: {}", e.toString());
        }
        return rDTO;
    }

    @Override
    public List<RelationshipDTO> getMemberships(String userId) throws Exception {
        log.info("Calling getMemberships");

        RelationshipDTO pDTO = new RelationshipDTO();
        pDTO.setUserId(userId);

        List<RelationshipDTO> result = new java.util.ArrayList<>(relationshipMapper.getListByUser(pDTO));

        // Same reasoning as getMembers: an owner without a membership row would otherwise never
        // see their own kindergarten in the workspace list.
        for (KindergartenDTO owned : kindergartenMapper.getListByOwner(userId)) {
            if (owned.getId() == null) continue;
            if (result.stream().anyMatch(r -> owned.getId().equals(r.getKindergartenId()))) continue;
            result.add(this.impliedOwnerMembership(owned.getId(), owned.getName(), userId));
        }

        FamilyDTO fDTO = new FamilyDTO();
        fDTO.setParent(userId);
        for (FamilyDTO family : familyMapper.selectList(fDTO)) {
            if (!userId.equals(family.getParent())) continue;

            RelationshipDTO childQuery = new RelationshipDTO();
            childQuery.setUserId(family.getChild());
            for (RelationshipDTO r : relationshipMapper.getListByUser(childQuery)) {
                if (r.getType() == RelationshipDTO.Type.CHILD) result.add(r);
            }
        }

        return result;
    }

    @Transactional
    @Override
    public int add(long id, String userId, RelationshipDTO.Type type) throws Exception {
        log.info("Calling add");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setType(type);

        return relationshipMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int assign(long id, String userId, long roleId) throws Exception {
        log.info("Calling assign");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setRoleId(roleId);

        // Keeps the legacy scalar column and the join table in step, so callers that still read
        // RelationshipDTO.roleId keep seeing the most recently assigned role.
        relationshipMapper.insertRole(pDTO);

        return relationshipMapper.updateRole(pDTO);
    }

    @Transactional
    @Override
    public int assignRole(long id, String userId, long roleId) throws Exception {
        log.info("Calling assignRole");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setRoleId(roleId);

        return relationshipMapper.insertRole(pDTO);
    }

    @Transactional
    @Override
    public int unassignRole(long id, String userId, long roleId) throws Exception {
        log.info("Calling unassignRole");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setRoleId(roleId);

        int result = relationshipMapper.deleteRole(pDTO);

        // The scalar column would otherwise still point at a role the member no longer holds.
        RelationshipDTO current = relationshipMapper.getInfo(pDTO);
        if (current != null && current.getRoleId() != null && current.getRoleId() == roleId) {
            RelationshipDTO clear = RelationshipDTO.fromId(id, userId);
            clear.setRoleId(null);
            relationshipMapper.updateRole(clear);
        }

        return result;
    }

    @Transactional
    @Override
    public int setNickname(long id, String userId, String nickname) throws Exception {
        log.info("Calling setNickname");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setNickname(nickname);

        return relationshipMapper.updateNickname(pDTO);
    }

    @Transactional
    @Override
    public int setClass(long id, String userId, Long classId) throws Exception {
        log.info("Calling setClass");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setClassId(classId);

        return relationshipMapper.updateClass(pDTO);
    }

    @Transactional
    @Override
    public int remove(long id, String userId) throws Exception {
        log.info("Calling remove");

        relationshipMapper.deleteRoles(RelationshipDTO.fromId(id, userId));

        return relationshipMapper.delete(RelationshipDTO.fromId(id, userId));
    }

    @Override
    public RelationshipDTO has(long id, String userId) throws Exception {
        log.info("Calling has");

        return relationshipMapper.getExists(RelationshipDTO.fromId(id, userId));
    }

    @Transactional
    @Override
    public int inviteUser(long id, String inviterId, String userId, RelationshipDTO.Type type, Long roleId) throws Exception {
        log.info("Calling inviteUser");

        InviteDTO pDTO = new InviteDTO();
        pDTO.setKindergartenId(id);
        pDTO.setInviterId(inviterId);
        pDTO.setUserId(userId);
        pDTO.setType(type);
        pDTO.setRoleId(roleId);
        pDTO.setDirection(InviteDTO.Direction.INVITE);

        return inviteMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int requestJoin(long id, String userId, RelationshipDTO.Type type) throws Exception {
        log.info("Calling requestJoin");

        InviteDTO pDTO = new InviteDTO();
        pDTO.setKindergartenId(id);
        pDTO.setUserId(userId);
        pDTO.setType(type);
        pDTO.setDirection(InviteDTO.Direction.JOIN);

        return inviteMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int cancelInvite(long id, String requesterId) throws Exception {
        log.info("Calling cancelInvite");

        InviteDTO invite = inviteMapper.getInfo(InviteDTO.fromId(id));
        if (invite == null || invite.getStatus() != InviteDTO.Status.PENDING) throw new IllegalStateException();

        String owner = invite.getDirection() == InviteDTO.Direction.INVITE ? invite.getInviterId() : invite.getUserId();
        if (!requesterId.equals(owner)) throw new IllegalAccessException();

        invite.setStatus(InviteDTO.Status.CANCELED);
        return inviteMapper.updateStatus(invite);
    }

    @Transactional
    @Override
    public int acceptInvite(long id, String userId) throws Exception {
        log.info("Calling acceptInvite");

        InviteDTO invite = inviteMapper.getInfo(InviteDTO.fromId(id));
        if (invite == null || invite.getStatus() != InviteDTO.Status.PENDING) throw new IllegalStateException();

        this.checkInviteCounterparty(invite, userId);

        RelationshipDTO rDTO = RelationshipDTO.fromId(invite.getKindergartenId(), invite.getUserId());
        rDTO.setType(invite.getType());
        relationshipMapper.insert(rDTO);

        invite.setStatus(InviteDTO.Status.ACCEPTED);
        return inviteMapper.updateStatus(invite);
    }

    @Transactional
    @Override
    public int rejectInvite(long id, String userId) throws Exception {
        log.info("Calling rejectInvite");

        InviteDTO invite = inviteMapper.getInfo(InviteDTO.fromId(id));
        if (invite == null || invite.getStatus() != InviteDTO.Status.PENDING) throw new IllegalStateException();

        this.checkInviteCounterparty(invite, userId);

        invite.setStatus(InviteDTO.Status.REJECTED);
        return inviteMapper.updateStatus(invite);
    }

    // Only the invitee may accept/reject an INVITE; a JOIN request is answered by whoever manages
    // members at the kindergarten it was sent to.
    private void checkInviteCounterparty(InviteDTO invite, String userId) throws Exception {
        if (invite.getDirection() == InviteDTO.Direction.INVITE) {
            if (!userId.equals(invite.getUserId())) throw new IllegalAccessException();
        } else {
            if (!accessService.hasPermission(invite.getKindergartenId(), userId, RoleDTO.Permission.MANAGE_MEMBER))
                throw new IllegalAccessException();
        }
    }

    @Override
    public List<InviteDTO> getInvites(long id) throws Exception {
        log.info("Calling getInvites");

        InviteDTO pDTO = new InviteDTO();
        pDTO.setKindergartenId(id);

        return inviteMapper.getListByKindergarten(pDTO);
    }

    @Override
    public List<InviteDTO> getUserInvites(String userId) throws Exception {
        log.info("Calling getUserInvites");

        InviteDTO pDTO = new InviteDTO();
        pDTO.setUserId(userId);

        return inviteMapper.getListByUser(pDTO);
    }

    @Override
    public List<RoleDTO> getRoles(long id) throws Exception {
        log.info("Calling getRoles");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setKindergartenId(id);

        return roleMapper.getList(pDTO);
    }

    @Transactional
    @Override
    public int createRole(long id, String name, String color) throws Exception {
        log.info("Calling createRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setKindergartenId(id);
        pDTO.setName(name);
        pDTO.setColor(color);

        return roleMapper.insert(pDTO);
    }

    @Override
    public int renameRole(long roleId, String name, String color) throws Exception {
        log.info("Calling renameRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setId(roleId);
        pDTO.setName(name);
        pDTO.setColor(color);

        return roleMapper.updateName(pDTO);
    }

    @Override
    public int deleteRole(long roleId) throws Exception {
        log.info("Calling deleteRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setId(roleId);

        return roleMapper.delete(pDTO);
    }

    @Override
    public List<RoleDTO.Permission> getRolePermissions(long roleId) throws Exception {
        log.info("Calling getRolePermissions");

        return permissionMapper.getList(new RoleDTO.PermissionDTO(roleId, null)).stream().map(RoleDTO.PermissionDTO::getPermission).toList();
    }

    @Override
    public boolean hasRolePermission(long roleId, RoleDTO.Permission permission) throws Exception {
        log.info("Calling hasRolePermission");

        return permissionMapper.getInfo(new RoleDTO.PermissionDTO(roleId, permission)) != null;
    }

    @Transactional
    @Override
    public int addRolePermission(long roleId, RoleDTO.Permission permission) throws Exception {
        log.info("Calling addRolePermission");

        RoleDTO.PermissionDTO pDTO = new RoleDTO.PermissionDTO(roleId, permission);

        return permissionMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int removeRolePermission(long roleId, RoleDTO.Permission permission) throws Exception {
        log.info("Calling removeRolePermission");

        RoleDTO.PermissionDTO pDTO = new RoleDTO.PermissionDTO(roleId, permission);

        return permissionMapper.delete(pDTO);
    }

    @Override
    public List<NoticeDTO> getNotices(long id) throws Exception {
        log.info("Calling getNotices");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(id);

        return noticeMapper.getList(pDTO);
    }

    @Override
    public NoticeDTO getNoticeInfo(long noticeId) throws Exception {
        log.info("Calling getNoticeInfo");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setId(noticeId);

        return noticeMapper.getInfo(pDTO);
    }

    @Transactional
    @Override
    public int createNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling createNotice");

        return noticeMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int updateNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling updateNotice");

        return noticeMapper.update(pDTO);
    }

    @Transactional
    @Override
    public int deleteNotice(long noticeId) throws Exception {
        log.info("Calling deleteNotice");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setId(noticeId);

        return noticeMapper.delete(pDTO);
    }

    @Transactional
    @Override
    public List<ScheduleDTO> getSchedules(long id) throws Exception {
        log.info("Calling getSchedules");

        ScheduleDTO pDTO = new ScheduleDTO();
        pDTO.setKindergartenId(id);

        return scheduleMapper.selectList(pDTO);
    }

    @Override
    public ScheduleDTO getScheduleInfo(long scheduleId) throws Exception {
        log.info("Calling getScheduleInfo");

        ScheduleDTO pDTO = new ScheduleDTO();
        pDTO.setId(scheduleId);

        return scheduleMapper.select(pDTO);
    }

    @Transactional
    @Override
    public int createSchedule(ScheduleDTO pDTO) throws Exception {
        log.info("Calling createSchedule");

        return scheduleMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int updateSchedule(ScheduleDTO pDTO) throws Exception {
        log.info("Calling updateSchedule");

        return scheduleMapper.update(pDTO);
    }

    @Transactional
    @Override
    public int deleteSchedule(long scheduleId) throws Exception {
        log.info("Calling deleteSchedule");

        ScheduleDTO pDTO = new ScheduleDTO();
        pDTO.setId(scheduleId);

        return scheduleMapper.delete(pDTO);
    }
}
