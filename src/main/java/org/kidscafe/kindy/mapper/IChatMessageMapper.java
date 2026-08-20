package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ChatDTO;

import java.util.List;

@Mapper
public interface IChatMessageMapper {
    List<ChatDTO.MessageDTO> selectList(ChatDTO.QueryDTO pDTO) throws Exception;
    /** The last `maxAmount` turns, oldest-first. */
    List<ChatDTO.MessageDTO> selectRecent(ChatDTO.QueryDTO pDTO) throws Exception;
    /** The highest-NUM row of a chat — the one {@code insertNext} just appended. */
    ChatDTO.MessageDTO selectLast(ChatDTO.QueryDTO pDTO) throws Exception;
    ChatDTO.MessageDTO select(ChatDTO.MessageDTO pDTO) throws Exception;
    int insert(ChatDTO.MessageDTO pDTO) throws Exception;
    int insertNext(ChatDTO.MessageDTO pDTO) throws Exception;
    int delete(ChatDTO.MessageDTO pDTO) throws Exception;
}
