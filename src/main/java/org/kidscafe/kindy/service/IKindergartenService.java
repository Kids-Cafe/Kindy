package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.KindergartenDTO;
import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.RoleDTO;

import java.util.List;

public interface IKindergartenService {
    List<KindergartenDTO> getList() throws Exception;
    KindergartenDTO getInfo(long id) throws Exception;
    int create(KindergartenDTO pDTO) throws Exception;
    int update(KindergartenDTO pDTO) throws Exception;
    int transfer(long id, String userId) throws Exception;
    int register(long id, String userId, RelationshipDTO.Type type) throws Exception;
    int assign(long id, String userId, long roleId) throws Exception;
    int setNickname(long id, String userId, String nickname) throws Exception;
    int remove(long id, String userId) throws Exception;
    boolean has(long id, String userId) throws Exception;
    int createRole(long id, String name) throws Exception;
    int addRolePermission(long roleId, RoleDTO.Permission permission) throws Exception;
    int removeRolePermission(long roleId, RoleDTO.Permission permission) throws Exception;
}
