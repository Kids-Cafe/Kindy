package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.InviteDTO;

import java.util.List;

@Mapper
public interface IInviteMapper {
    InviteDTO getInfo(InviteDTO pDTO) throws Exception;
    List<InviteDTO> getListByKindergarten(InviteDTO pDTO) throws Exception;
    List<InviteDTO> getListByUser(InviteDTO pDTO) throws Exception;
    int insert(InviteDTO pDTO) throws Exception;
    int updateStatus(InviteDTO pDTO) throws Exception;
}
