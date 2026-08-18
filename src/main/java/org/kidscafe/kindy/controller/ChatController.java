package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.service.IAccessService;
import org.kidscafe.kindy.service.IChatService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final IChatService chatService;
    private final IAccessService accessService;

    @GetMapping(value = "list")
    public ResultDTO<List<ChatDTO>> list(HttpSession session, @RequestParam long kindergartenId) {
        log.info("Calling list");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            // A conversation is private to its two sides, so the kindergarten filter narrows the
            // user's own chats rather than exposing everyone else's.
            if (kindergartenId > 0) {
                if (!accessService.canView(kindergartenId, userId)) return ResultDTO.error("INVALID_ACCESS");
                return ResultDTO.success("QUERY_COMPLETE", chatService.getList(kindergartenId).stream()
                        .filter(c -> userId.equals(c.getHost()) || userId.equals(c.getClient()))
                        .toList());
            }
            return ResultDTO.success("QUERY_COMPLETE", chatService.getList(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<ChatDTO> info(HttpSession session, @RequestParam long id) {
        log.info("Calling info");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO rDTO = chatService.getInfo(id);
            if (rDTO == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(rDTO, userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // Returns the created chat (with its generated id) so the caller can start sending immediately.
    @PostMapping(value = "create")
    public ResultDTO<ChatDTO> create(HttpSession session,
                                     @RequestParam long kindergartenId,
                                     @RequestParam(required = false) String host,
                                     @RequestParam(required = false) String client) {
        log.info("Calling create");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        // The session user is one side of the chat unless they said otherwise.
        if (host == null) host = userId;
        if (client == null) client = userId;
        if (!userId.equals(host) && !userId.equals(client)) return ResultDTO.error("INVALID_ACCESS");

        // Both sides have to belong to the kindergarten the chat is filed under, otherwise anyone
        // could open a thread with a stranger by naming an arbitrary kindergarten.
        if (!accessService.canView(kindergartenId, userId)) return ResultDTO.error("INVALID_ACCESS");
        String other = userId.equals(host) ? client : host;
        if (!accessService.canView(kindergartenId, other)) return ResultDTO.error("INVALID_PARAMETER");

        ChatDTO pDTO = new ChatDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setHost(host);
        pDTO.setClient(client);

        try {
            chatService.create(pDTO);
            return ResultDTO.success("CREATE_COMPLETE", pDTO);
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // NUM is assigned by the database, so two senders racing on the same chat can't collide.
    @PostMapping(value = "send")
    public ResultDTO<Void> send(HttpSession session,
                                @RequestParam long chatId,
                                @RequestParam String content,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String role) {
        log.info("Calling send");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO chat = chatService.getInfo(chatId);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            ChatDTO.MessageDTO pDTO = new ChatDTO.MessageDTO();
            pDTO.setChatId(chatId);
            pDTO.setContent(content);
            pDTO.setType(type == null ? ChatDTO.MessageDTO.Type.TEXT : ChatDTO.MessageDTO.Type.valueOf(type));
            pDTO.setRole(role == null ? ChatDTO.MessageDTO.Role.user : ChatDTO.MessageDTO.Role.valueOf(role));

            chatService.appendMessage(pDTO);
            return ResultDTO.success("SEND_COMPLETE");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "transcribe", produces = "text/plain")
    public String transcribe(HttpSession session, @RequestParam(value = "file") MultipartFile file) throws Exception {
        log.info("Calling transcribe");

        // Not tied to a chat, but it bills an external API, so it isn't open to anonymous callers.
        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return "";

        String result = chatService.transcribe(file.getResource());
        log.info(result);
        return result;
    }

    @GetMapping(value = "messages")
    public ResultDTO<List<ChatDTO.MessageDTO>> messages(HttpSession session, @RequestParam long id) {
        log.info("Calling messages");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO chat = chatService.getInfo(id);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", chatService.getMessages(id));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** Chats are two-sided: only the host and the client may read or write one. */
    private static boolean isParticipant(ChatDTO chat, String userId) {
        return userId.equals(chat.getHost()) || userId.equals(chat.getClient());
    }
}
