package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ChatDTO;
import org.springframework.core.io.Resource;

import java.util.List;

public interface IChatService {
    List<ChatDTO> getList(long kindergartenId) throws Exception;
    List<ChatDTO> getList(String userId) throws Exception;
    ChatDTO getInfo(long id) throws Exception;
    int create(ChatDTO pDTO) throws Exception;
    int create(long kindergartenId, String host, String client) throws Exception;
    int delete(long id) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int start, int end) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int num) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int num, boolean reversed) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, long timestamp, int num) throws Exception;
    int addMessage(ChatDTO.MessageDTO pDTO) throws Exception;
    /** Appends with a NUM assigned by the database instead of by the caller. */
    int appendMessage(ChatDTO.MessageDTO pDTO) throws Exception;
    String transcribe(Resource resource) throws Exception;
    ChatDTO.MessageDTO requestMessage(long chatId) throws Exception;
    Resource synthesize(String text) throws Exception;
    Resource convert(Resource resource) throws Exception;
}
