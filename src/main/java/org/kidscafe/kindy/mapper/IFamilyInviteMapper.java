package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.kidscafe.kindy.dto.FamilyInviteDTO;

import java.util.List;

@Mapper
public interface IFamilyInviteMapper {
    FamilyInviteDTO getInfo(FamilyInviteDTO pDTO) throws Exception;

    /**
     * Every pending request this user has standing in: as the proposed parent, as the child, as
     * the one who asked, or as an existing parent of the child in question.
     */
    List<FamilyInviteDTO> getListForUser(@Param("userId") String userId) throws Exception;

    int insert(FamilyInviteDTO pDTO) throws Exception;

    /** Only moves a request that is still pending, so answering twice writes nothing the second time. */
    int updateStatus(FamilyInviteDTO pDTO) throws Exception;
}
