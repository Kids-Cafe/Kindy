package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.*;

import java.util.List;

public interface IKindergartenService {
    List<KindergartenDTO> getList(String q) throws Exception;
    KindergartenDTO getInfo(long id) throws Exception;
    KindergartenDTO getInfoByBrn(String brn) throws Exception;
    int create(KindergartenDTO pDTO) throws Exception;
    int update(KindergartenDTO pDTO) throws Exception;
    int transfer(long id, String userId) throws Exception;
    List<RelationshipDTO> getMembers(long id) throws Exception;
    List<RelationshipDTO> getMemberships(String userId) throws Exception;
    int add(long id, String userId, RelationshipDTO.Type type) throws Exception;
    int assign(long id, String userId, long roleId) throws Exception;
    int assignRole(long id, String userId, long roleId) throws Exception;
    int unassignRole(long id, String userId, long roleId) throws Exception;
    int setNickname(long id, String userId, String nickname) throws Exception;
    int remove(long id, String userId) throws Exception;
    RelationshipDTO has(long id, String userId) throws Exception;
    int inviteUser(long id, String inviterId, String userId, RelationshipDTO.Type type, Long roleId) throws Exception;
    int requestJoin(long id, String userId, RelationshipDTO.Type type) throws Exception;
    int cancelInvite(long id, String requesterId) throws Exception;
    int acceptInvite(long id, String userId) throws Exception;
    int rejectInvite(long id, String userId) throws Exception;
    List<InviteDTO> getInvites(long id) throws Exception;
    List<InviteDTO> getUserInvites(String userId) throws Exception;
    List<RoleDTO> getRoles(long id) throws Exception;
    int createRole(long id, String name, String color) throws Exception;
    int renameRole(long roleId, String name, String color) throws Exception;
    int deleteRole(long roleId) throws Exception;
    List<RoleDTO.Permission> getRolePermissions(long roleId) throws Exception;
    boolean hasRolePermission(long roleId, RoleDTO.Permission permission) throws Exception;
    int addRolePermission(long roleId, RoleDTO.Permission permission) throws Exception;
    int removeRolePermission(long roleId, RoleDTO.Permission permission) throws Exception;
    List<NoticeDTO> getNotices(long id) throws Exception;
    NoticeDTO getNoticeInfo(long noticeId) throws Exception;
    int createNotice(NoticeDTO pDTO) throws Exception;
    int updateNotice(NoticeDTO pDTO) throws Exception;
    int deleteNotice(long noticeId) throws Exception;
    List<ScheduleDTO> getSchedules(long id) throws Exception;
    ScheduleDTO getScheduleInfo(long scheduleId) throws Exception;
    int createSchedule(ScheduleDTO pDTO) throws Exception;
    int updateSchedule(ScheduleDTO pDTO) throws Exception;
    int deleteSchedule(long scheduleId) throws Exception;
}
