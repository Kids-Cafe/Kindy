package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.KindergartenDTO;
import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.RoleDTO;
import org.kidscafe.kindy.mapper.IKindergartenMapper;
import org.kidscafe.kindy.mapper.IRelationshipMapper;
import org.kidscafe.kindy.mapper.IRoleMapper;
import org.kidscafe.kindy.service.IKindergartenService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class KindergartenService implements IKindergartenService {
    private final IKindergartenMapper kindergartenMapper;
    private final IRelationshipMapper relationshipMapper;
    private final IRoleMapper roleMapper;

    @Override
    public List<KindergartenDTO> getList() throws Exception {
        log.info("Calling getList");

        return kindergartenMapper.getList();
    }

    @Override
    public KindergartenDTO getInfo(long id) throws Exception {
        log.info("Calling getList");

        return kindergartenMapper.getInfo(KindergartenDTO.fromId(id));
    }

    @Override
    public int create(KindergartenDTO pDTO) throws Exception {
        log.info("Calling create");

        return kindergartenMapper.insert(pDTO);
    }

    @Override
    public int update(KindergartenDTO pDTO) throws Exception {
        log.info("Calling update");

        return kindergartenMapper.update(pDTO);
    }

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
    public int add(long id, String userId, RelationshipDTO.Type type) throws Exception {
        log.info("Calling add");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setType(type);

        return relationshipMapper.insert(pDTO);
    }

    @Override
    public int assign(long id, String userId, long roleId) throws Exception {
        log.info("Calling assign");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setRoleId(roleId);

        return relationshipMapper.updateRole(pDTO);
    }

    @Override
    public int setNickname(long id, String userId, String nickname) throws Exception {
        log.info("Calling setNickname");

        RelationshipDTO pDTO = RelationshipDTO.fromId(id, userId);
        pDTO.setNickname(nickname);

        return relationshipMapper.updateNickname(pDTO);
    }

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

    @Override
    public List<RoleDTO> getRoles(long id) throws Exception {
        log.info("Calling createRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setKindergartenId(id);

        return roleMapper.getList(pDTO);
    }

    @Override
    public int createRole(long id, String name) throws Exception {
        log.info("Calling createRole");

        RoleDTO pDTO = new RoleDTO();
        pDTO.setKindergartenId(id);
        pDTO.setName(name);

        return roleMapper.insert(pDTO);
    }

    @Override
    public int addRolePermission(long roleId, RoleDTO.Permission permission) throws Exception {
        // TODO
        return 0;
    }

    @Override
    public int removeRolePermission(long roleId, RoleDTO.Permission permission) throws Exception {
        // TODO
        return 0;
    }
}
