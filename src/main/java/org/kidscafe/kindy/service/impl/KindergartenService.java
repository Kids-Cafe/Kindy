package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.mapper.*;
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

    @Override
    public List<KindergartenDTO> getList() throws Exception {
        log.info("Calling getList");

        return kindergartenMapper.getList();
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

    @Transactional
    @Override
    public int create(KindergartenDTO pDTO) throws Exception {
        log.info("Calling create");

        return kindergartenMapper.insert(pDTO);
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

        return kindergartenMapper.updateOwner(pDTO);
    }

    @Override
    public List<RelationshipDTO> getMembers(long id) throws Exception {
        log.info("Calling getMembers");

        RelationshipDTO pDTO = new RelationshipDTO();
        pDTO.setKindergartenId(id);

        return relationshipMapper.getList(pDTO);
    }

    @Override
    public List<RelationshipDTO> getMemberships(String userId) throws Exception {
        log.info("Calling getMemberships");

        RelationshipDTO pDTO = new RelationshipDTO();
        pDTO.setUserId(userId);

        List<RelationshipDTO> result = new java.util.ArrayList<>(relationshipMapper.getListByUser(pDTO));

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

        return relationshipMapper.updateRole(pDTO);
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
    public int remove(long id, String userId) throws Exception {
        log.info("Calling remove");

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

    // Only the invitee may accept/reject an INVITE; only an existing teacher may accept/reject a JOIN
    // request, since the finer-grained MANAGE_MEMBER permission check is out of scope for this pass.
    private void checkInviteCounterparty(InviteDTO invite, String userId) throws Exception {
        if (invite.getDirection() == InviteDTO.Direction.INVITE) {
            if (!userId.equals(invite.getUserId())) throw new IllegalAccessException();
        } else {
            RelationshipDTO admin = relationshipMapper.getInfo(RelationshipDTO.fromId(invite.getKindergartenId(), userId));
            if (admin == null || admin.getType() != RelationshipDTO.Type.TEACHER) throw new IllegalAccessException();
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
    public int createRole(long id, String name) throws Exception {
        log.info("Calling createRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setKindergartenId(id);
        pDTO.setName(name);

        return roleMapper.insert(pDTO);
    }

    @Override
    public int renameRole(long roleId, String name) throws Exception {
        log.info("Calling renameRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setId(roleId);
        pDTO.setName(name);

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
