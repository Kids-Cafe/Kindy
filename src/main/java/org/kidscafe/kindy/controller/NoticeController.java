package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.MsgDTO;
import org.kidscafe.kindy.dto.NoticeDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.service.INoticeService;
import org.kidscafe.kindy.util.CmmUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequestMapping(value = "/notice")
@RequiredArgsConstructor
@Controller
public class NoticeController {

    private final INoticeService noticeService;

    @GetMapping(value = "noticeList")
    public ResultDTO noticeList(HttpSession session, ModelMap model)
            throws Exception {
        log.info("Calling noticeList");

        session.setAttribute("SESSION_USER_ID", "USER01");

        List<NoticeDTO> rList = Optional.ofNullable(noticeService.getNoticeList())
                .orElseGet(ArrayList::new);

        model.addAttribute("rList", rList);

        return ;

    }

    @GetMapping(value = "noticeReg")
    public ResultDTO NoticeReg() {

        log.info("Calling NoticeReg");

        return ;
    }

    @ResponseBody
    @PostMapping(value = "noticeInsert")
    public ResultDTO noticeInsert(HttpServletRequest request, HttpSession session) {

        log.info("Calling noticeInsert");

//        String msg = "";
        MsgDTO dto;

        try {

            String userId = CmmUtil.nvl((String) session.getAttribute("SESSION_USER_ID"));
            String title = CmmUtil.nvl(request.getParameter("title"));
            String noticeYn = CmmUtil.nvl(request.getParameter("noticeYn"));
            String contents = CmmUtil.nvl(request.getParameter("contents"));

            log.info("session user_id : {} / title : {} / noticeYn : {} / content : {} ",
                    userId, title, noticeYn, contents);

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setUserId(userId);
            pDTO.setTitle(title);
            pDTO.setNoticeYn(noticeYn);
            pDTO.setContents(contents);

            noticeService.insertNoticeInfo(pDTO);

//            msg = "";

        } catch (Exception e) {

//            msg = "" + e.getMessage();
            log.info(e.toString());

        } finally {

            dto = new MsgDTO();
            dto.setMsg(msg);

        }

        return ;
    }

    @GetMapping(value = "noticeInfo")
    public ResultDTO noticeInfo(HttpServletRequest request, ModelMap model) throws Exception {

        log.info("Calling noticeInfo");

        String nSeq = CmmUtil.nvl(request.getParameter("nSeq"));

        log.info("nSeq : {}", nSeq);

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setNoticeSeq(nSeq);

        NoticeDTO rDTO = Optional.ofNullable(noticeService.getNoticeInfo(pDTO, false))
                .orElseGet(NoticeDTO::new);

        model.addAttribute("rDTO", rDTO);

        log.info("{}.noticeEditInfo End!", this.getClass().getName());

        return ;
    }

    @GetMapping(value = "noticeEditInfo")
    public ResultDTO noticeEditInfo(HttpServletRequest request, ModelMap model) throws Exception {

        log.info("Calling noticeEditInfo");

        String nSeq = CmmUtil.nvl(request.getParameter("nSeq"));

        log.info("nSeq : {}", nSeq);

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setNoticeSeq(nSeq);

        NoticeDTO rDTO = Optional.ofNullable(noticeService.getNoticeInfo(pDTO, false))
                .orElseGet(NoticeDTO::new);

        model.addAttribute("rDTO", rDTO);

        log.info("{}.noticeEditInfo End!", this.getClass().getName());

        return ;
    }

    @ResponseBody
    @PostMapping(value = "noticeUpdate")
    public ResultDTO noticeUpdate(HttpSession session, HttpServletRequest request) {

        log.info("Calling noticeUpdate");

//        String msg = "";
        MsgDTO dto;

        try {

            String userId = CmmUtil.nvl((String) session.getAttribute("SESSION_USER_ID"));
            String nSeq = CmmUtil.nvl(request.getParameter("nSeq"));
            String title = CmmUtil.nvl(request.getParameter("title"));
            String noticeYn = CmmUtil.nvl(request.getParameter("noticeYn"));
            String contents = CmmUtil.nvl(request.getParameter("contents"));

            log.info("user_id : {} / nSeq : {} / title : {} / noticeYn : {} / content : {} ",
                    userId, nSeq, title, noticeYn, contents);

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setUserId(userId);
            pDTO.setNoticeSeq(nSeq);
            pDTO.setTitle(title);
            pDTO.setNoticeYn(noticeYn);
            pDTO.setContents(contents);

            noticeService.updateNoticeInfo(pDTO);

//            msg = ;

        } catch (Exception e) {
//            msg =  + e.getMessage();
            log.info(e.toString());

        } finally {

            dto = new MsgDTO();
            dto.setMsg(msg);

        }

        return ;
    }

    @ResponseBody
    @PostMapping(value = "noticeDelete")
    public ResultDTO noticeDelete (HttpServletRequest request) {

        log.info("Calling noticeDelete");

        String msg = "";
        MsgDTO dto;

        try {

            String nSeq = CmmUtil.nvl(request.getParameter("nSeq"));

            log.info("nSeq : {}", nSeq);

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setNoticeSeq(nSeq);

            noticeService.deleteNoticeInfo(pDTO);

//            msg = ;

        } catch (Exception e) {
//            msg = + e.getMessage();
            log.info(e.toString());

        } finally {

            dto = new MsgDTO();
            dto.setMsg(msg);

        }

        return ;
    }
}
