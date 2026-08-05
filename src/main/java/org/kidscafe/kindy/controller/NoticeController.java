package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.NoticeDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.service.INoticeService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping(value = "/api/notice")
@RequiredArgsConstructor
@RestController
public class NoticeController {
    private final INoticeService noticeService;

    @GetMapping(value = "testForceInjectSessionUserId")
    public void testForceInjectSessionUserId(HttpSession session) {
        session.setAttribute("SESSION_USER_ID", "TEST");
    }

    @GetMapping(value = "list")
    public ResultDTO list(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling list");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        String userId = (String) session.getAttribute("SESSION_USER_ID");

        if (!noticeService.hasAccess(userId, kindergartenId)) return ResultDTO.error("INVALID_ACCESS");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(kindergartenId);
        log.info(pDTO.toString());

        return ResultDTO.success("QUERY_COMPLETE", noticeService.getNoticeList(pDTO));
    }

    @PostMapping(value = "insert")
    public ResultDTO insert(HttpServletRequest request, HttpSession session) {
        log.info("Calling insert");

        try {
            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            if (!noticeService.hasAccess(userId, kindergartenId)) return ResultDTO.error("INVALID_ACCESS");

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            pDTO.setAuthor(userId);
            pDTO.setTitle(request.getParameter("title"));
            pDTO.setPinned(Boolean.parseBoolean(request.getParameter("pinned")));
            pDTO.setContent(request.getParameter("content"));
            log.info(pDTO.toString());

            noticeService.insertNotice(pDTO);

            return ResultDTO.success("REGISTER_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO info(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling info");
        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        String userId = (String) session.getAttribute("SESSION_USER_ID");

        if (!noticeService.hasAccess(userId, kindergartenId)) return ResultDTO.error("INVALID_ACCESS");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(kindergartenId);
        try {
            pDTO.setNum(Integer.parseInt(request.getParameter("num")));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        log.info(pDTO.toString());

        NoticeDTO rDTO = noticeService.getNotice(pDTO);
        if (rDTO == null) return ResultDTO.error("NOTICE_NOT_FOUND");

        return ResultDTO.success("QUERY_COMPLETE", rDTO);
    }

    @PostMapping(value = "edit")
    public ResultDTO edit(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling edit");

        try {
            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            if (!noticeService.hasAccess(userId, kindergartenId)) return ResultDTO.error("INVALID_ACCESS");

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            try {
                pDTO.setNum(Integer.parseInt(request.getParameter("num")));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }
            pDTO.setTitle(request.getParameter("title"));
            pDTO.setPinned(Boolean.parseBoolean(request.getParameter("pinned")));
            pDTO.setContent(request.getParameter("content"));
            log.info(pDTO.toString());

            noticeService.updateNotice(pDTO);

            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "delete")
    public ResultDTO delete(HttpServletRequest request, HttpSession session) {
        log.info("Calling delete");

        try {
            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            if (!noticeService.hasAccess(userId, kindergartenId)) return ResultDTO.error("INVALID_ACCESS");

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            try {
                pDTO.setNum(Integer.parseInt(request.getParameter("num")));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }
            log.info(pDTO.toString());

            noticeService.deleteNotice(pDTO);

            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
