package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ChatDTO;
import org.springframework.core.io.Resource;

import java.util.List;

public interface IChatService {
    List<ChatDTO> getList(long kindergartenId) throws Exception;
    List<ChatDTO> getList(String userId) throws Exception;
    ChatDTO getInfo(long id) throws Exception;
    /** The conversation between two people, or null. Order of host/client does not matter. */
    ChatDTO getInfo(long kindergartenId, String host, String client) throws Exception;
    int create(ChatDTO pDTO) throws Exception;
    int create(long kindergartenId, String host, String client) throws Exception;
    /**
     * The conversation between two people, opening it if this is the first message.
     * Never returns a second thread for a pair that already has one.
     */
    ChatDTO ensure(long kindergartenId, String host, String client) throws Exception;
    int delete(long id) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int start, int end) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int num) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, int num, boolean reversed) throws Exception;
    List<ChatDTO.MessageDTO> getMessages(long id, long timestamp, int num) throws Exception;
    /** The last `limit` turns, oldest-first — the window we replay to the LLM. */
    List<ChatDTO.MessageDTO> getRecentMessages(long id, int limit) throws Exception;
    int addMessage(ChatDTO.MessageDTO pDTO) throws Exception;
    /** Appends with a NUM assigned by the database instead of by the caller. */
    int appendMessage(ChatDTO.MessageDTO pDTO) throws Exception;
    /** Appends, then reads the stored row back so the caller gets the assigned NUM and CREATED_AT. */
    ChatDTO.MessageDTO appendMessageAndRead(ChatDTO.MessageDTO pDTO) throws Exception;
    String transcribe(Resource resource) throws Exception;
    /** An answer from the default partner — for callers that have no character to speak as. */
    ChatDTO.MessageDTO requestMessage(long chatId) throws Exception;
    /** As {@link #requestMessage(long, ChatDTO.Partner)}; an unknown name falls back to the default. */
    ChatDTO.MessageDTO requestMessage(long chatId, String partner) throws Exception;
    /** An answer in the voice of the partner the child chose. Only the system prompt differs. */
    ChatDTO.MessageDTO requestMessage(long chatId, ChatDTO.Partner partner) throws Exception;
    Resource synthesize(String text) throws Exception;
    Resource convert(Resource resource) throws Exception;
}
