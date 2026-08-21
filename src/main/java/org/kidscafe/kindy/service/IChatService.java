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
    /**
     * Every day this child talked with their AI partner, newest first, with how much was said.
     * A child ↔ partner chat is one whose two sides are the same person.
     */
    List<ChatDTO.DayDTO> getPartnerDays(String userId) throws Exception;
    /** One such day, in the order it was said. */
    List<ChatDTO.MessageDTO> getPartnerDay(String userId, String date) throws Exception;
    /**
     * One completion against the configured model, for callers that are not a chat turn.
     *
     * Returns the assistant's text, or null when the server answered in a shape we don't know or
     * with nothing in it. {@code format} is Ollama's structured-output switch — pass "json" to ask
     * for parseable output, or null to leave the field off the request entirely.
     */
    String complete(List<ChatDTO.LLMMessageDTO> messages, String format) throws Exception;
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
    /** Plain speech, in nobody's voice in particular. */
    Resource synthesize(String text) throws Exception;
    /** As {@link #synthesize(String, ChatDTO.Partner)}; a missing name means no character. */
    Resource synthesize(String text, String partner) throws Exception;
    /** Speech at the partner's own pace. A null partner keeps the neutral speed. */
    Resource synthesize(String text, ChatDTO.Partner partner) throws Exception;
    Resource convert(Resource resource) throws Exception;
    /** As {@link #convert(Resource, ChatDTO.Partner)}; a missing name means no character. */
    Resource convert(Resource resource, String partner) throws Exception;
    /**
     * Speech recast in the partner's voice — their model if one is configured for them, and their
     * own pitch either way. A null partner keeps the shared model and the neutral shift.
     */
    Resource convert(Resource resource, ChatDTO.Partner partner) throws Exception;
}
