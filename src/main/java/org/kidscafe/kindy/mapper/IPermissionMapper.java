package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.RoleDTO;

import java.util.List;

@Mapper
public interface IPermissionMapper {
    List<RoleDTO.PermissionDTO> getList(RoleDTO.PermissionDTO pDTO) throws Exception;
    /** Every permission a member holds in one kindergarten, across all of their roles. */
    List<RoleDTO.PermissionDTO> getListByUser(RelationshipDTO pDTO) throws Exception;
    RoleDTO.PermissionDTO getInfo(RoleDTO.PermissionDTO pDTO) throws Exception;
    int insert(RoleDTO.PermissionDTO pDTO) throws Exception;
    int delete(RoleDTO.PermissionDTO pDTO) throws Exception;
}
