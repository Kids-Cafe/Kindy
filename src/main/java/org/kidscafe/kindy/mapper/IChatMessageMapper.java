package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ChatDTO;

import java.util.List;

@Mapper
public interface IChatMessageMapper {
    List<ChatDTO.MessageDTO> selectList(ChatDTO.MessageDTO pDTO) throws Exception;
    ChatDTO.MessageDTO select(ChatDTO.MessageDTO pDTO) throws Exception;
    int insert(ChatDTO.MessageDTO pDTO) throws Exception;
    int delete(ChatDTO.MessageDTO pDTO) throws Exception;
}
