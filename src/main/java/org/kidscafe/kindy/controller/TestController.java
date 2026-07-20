package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.service.ITranscriptionService;
import org.kidscafe.kindy.service.impl.ChatService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/test")
class TestController {
    private final ITranscriptionService transcriptionService;
    private final ChatService chatService;

    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    private final Map<String, ChatDTO> chatDTOMap = new HashMap<>();

    @ResponseBody
    @PostMapping(value = "transcribe", produces = "text/plain")
    public String transcribe(@RequestParam(value = "file") MultipartFile multipartFile) throws Exception {
        this.callLog("transcribe");
        String result = transcriptionService.transcribe(multipartFile);
        log.info(result);
        return result;
    }

    private ChatDTO getChatFromSessionId(String sessionId) {
        ChatDTO pDTO = chatDTOMap.get(sessionId);
        if (pDTO == null) {
            pDTO = chatService.createChat();
            chatDTOMap.put(sessionId, pDTO);
        }
        return pDTO;
    }

    @ResponseBody
    @PostMapping(value = "chat")
    public ChatDTO.ChatMessageDTO chat(HttpServletRequest request, HttpSession session) {
        this.callLog("chat");
        ChatDTO pDTO = getChatFromSessionId(session.getId());
        pDTO.messages().add(new ChatDTO.ChatMessageDTO(ChatDTO.Role.user, request.getParameter("content")));
        return chatService.requestNextMessage(pDTO);
    }

    @ResponseBody
    @GetMapping(value = "chatHistory")
    public ChatDTO chatHistory(HttpSession session) {
        this.callLog("chatHistory");
        return getChatFromSessionId(session.getId());
    }
}
