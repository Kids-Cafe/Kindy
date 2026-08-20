package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ChatDTO;

import java.util.List;

@Mapper
public interface IChatMapper {
    List<ChatDTO> selectList(ChatDTO pDTO) throws Exception;
    List<ChatDTO> selectListByUser(ChatDTO pDTO) throws Exception;
    ChatDTO select(ChatDTO pDTO) throws Exception;
    /** The existing conversation between two people in one kindergarten, either way round. */
    ChatDTO selectByParticipants(ChatDTO pDTO) throws Exception;
    int insert(ChatDTO pDTO) throws Exception;
    int delete(ChatDTO pDTO) throws Exception;
}
