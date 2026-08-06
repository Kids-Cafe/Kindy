package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.RoleDTO;

import java.util.List;

@Mapper
public interface IRoleMapper {
    List<RoleDTO> getList() throws Exception;
    RoleDTO getInfo(RelationshipDTO pDTO) throws Exception;
    int insert(RoleDTO pDTO) throws Exception;
    int updateName(RoleDTO pDTO) throws Exception;
    int delete(RoleDTO pDTO) throws Exception;
}
