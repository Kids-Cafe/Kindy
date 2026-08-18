package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.RelationshipDTO;

import java.util.List;

@Mapper
public interface IRelationshipMapper {
    RelationshipDTO getExists(RelationshipDTO pDTO) throws Exception;
    List<RelationshipDTO> getList(RelationshipDTO pDTO) throws Exception;
    List<RelationshipDTO> getListByUser(RelationshipDTO pDTO) throws Exception;
    RelationshipDTO getInfo(RelationshipDTO pDTO) throws Exception;
    int insert(RelationshipDTO pDTO) throws Exception;
    int updateRole(RelationshipDTO pDTO) throws Exception;
    int updateNickname(RelationshipDTO pDTO) throws Exception;
    int insertRole(RelationshipDTO pDTO) throws Exception;
    int deleteRole(RelationshipDTO pDTO) throws Exception;
    int deleteRoles(RelationshipDTO pDTO) throws Exception;
    int delete(RelationshipDTO pDTO) throws Exception;
}
