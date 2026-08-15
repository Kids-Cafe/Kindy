package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.ResultDTO;
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

    @GetMapping(value = "list")
    public ResultDTO<List<ChatDTO>> list(HttpSession session, @RequestParam long kindergartenId) {
        log.info("Calling list");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            if (kindergartenId > 0) return ResultDTO.success("QUERY_COMPLETE", chatService.getList(kindergartenId));
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
            return ResultDTO.success("QUERY_COMPLETE", chatService.getInfo(id));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "transcribe", produces = "text/plain")
    public String transcribe(@RequestParam(value = "file") MultipartFile file) throws Exception {
        log.info("Calling transcribe");
        String result = chatService.transcribe(file.getResource());
        log.info(result);
        return result;
    }
}
