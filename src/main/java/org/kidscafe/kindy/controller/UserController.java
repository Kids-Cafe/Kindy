package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.MsgDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.CmmUtil;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RequestMapping(value = "/user")
@RequiredArgsConstructor
@Controller
public class UserController {

    private final IUserService userService;
    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @ResponseBody
    @PostMapping(value = "getIdExists")
    public UserDTO getIdExists(HttpServletRequest request) throws Exception {
        this.callLog("getIdExists");

        String id = CmmUtil.nvl(request.getParameter("id"));

        log.info("id: {}", id);

        UserDTO pDTO = new UserDTO();
        pDTO.setId(id);

        return Optional.ofNullable(userService.getUserIdExists(pDTO)).orElseGet(UserDTO::new);
    }

    @ResponseBody
    @PostMapping(value = "getEmailExists")
    public UserDTO getEmailExists(HttpServletRequest request) throws Exception {
        this.callLog("getEmailExists");

        String email = CmmUtil.nvl(request.getParameter("email"));

        log.info("email: {}", email);

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(EncryptUtil.encAES128CBC(email));

        return Optional.ofNullable(userService.getEmailExists(pDTO)).orElseGet(UserDTO::new);
    }

    @ResponseBody
    @PostMapping(value = "insertUser")
    public MsgDTO insertUser(HttpServletRequest request) {
        this.callLog("insertUser");

        int res = 0;
        String msg = "";
        MsgDTO dto;

        UserDTO pDTO;

        try {

            String id = CmmUtil.nvl(request.getParameter("id"));
            String name = CmmUtil.nvl(request.getParameter("name"));
            String password = CmmUtil.nvl(request.getParameter("password"));
            String email = CmmUtil.nvl(request.getParameter("email"));
            String addr1 = CmmUtil.nvl(request.getParameter("addr1"));
            String addr2 = CmmUtil.nvl(request.getParameter("addr2"));

            log.info("id: " + id);
            log.info("name: " + name);
            log.info("email: " + email);
            log.info("addr1: " + addr1);
            log.info("addr2: " + addr2);

            pDTO = new UserDTO();

            pDTO.setId(id);
            pDTO.setName(name);

            pDTO.setPassword(EncryptUtil.encHashSHA256(password));

            pDTO.setEmail(EncryptUtil.encAES128CBC(email));
            pDTO.setAddr1(addr1);
            pDTO.setAddr2(addr2);

            res = userService.insertUserInfo(pDTO);

            log.info("회원가입 결과: " + res);

            if (res == 1) {
                msg = "회원가입되었습니다.";

            } else if (res == 2) {
                msg = "이미 가입된 아이디입니다.";

            } else {
                msg = "오류로 인해 회원가입이 실패하였습니다.";

            }

        } catch(Exception e){

            msg = "실패하였습니다.: " + e;
            log.info(e.toString());

        } finally{
            dto = new MsgDTO();
            dto.setResult(res);
            dto.setMsg(msg);

            log.info("{}.insertUserInfo End!", this.getClass().getName());
        }

        return dto;
    }

    @ResponseBody
    @PostMapping(value = "login")
    public MsgDTO login(HttpServletRequest request, HttpSession session) {
        this.callLog("login");

        int res = 0;
        String msg = "";
        MsgDTO dto;

        UserDTO pDTO;

        try {

            String id = CmmUtil.nvl(request.getParameter("id"));
            String password = CmmUtil.nvl(request.getParameter("password"));

            log.info("id: {}", id);

            pDTO = new UserDTO();

            pDTO.setId(id);

            pDTO.setPassword(EncryptUtil.encHashSHA256(password));

            UserDTO rDTO = userService.getLogin(pDTO);

            if (!CmmUtil.nvl(rDTO.getId()).isEmpty()) {

                res = 1;

                msg = "로그인이 성공했습니다";

                session.setAttribute("SS_USER_ID", id);
                session.setAttribute("SS_USER_NAME", CmmUtil.nvl(rDTO.getName()));

            } else {
                msg = "아이디와 비밀번호가 올바르지 않습니다.";

            }

        } catch (Exception e) {
            msg = "시스템 문제로 로그인이 실패했습니다.";
            res = 2;
            log.info(e.toString());

        } finally {
            dto = new MsgDTO();
            dto.setResult(res);
            dto.setMsg(msg);
        }

        return dto;
    }

    @ResponseBody
    @PostMapping(value = "searchId")
    public UserDTO searchId(HttpServletRequest request, ModelMap model) throws Exception {
        this.callLog("searchId");

        String name = CmmUtil.nvl(request.getParameter("name"));
        String email = CmmUtil.nvl(request.getParameter("email"));

        log.info("name: {} / email: {}", name, email);

        UserDTO pDTO = new UserDTO();
        pDTO.setName(name);
        pDTO.setEmail(EncryptUtil.encAES128CBC(email));

        return Optional.ofNullable(userService.searchUserIdOrPasswordProc(pDTO))
                .orElseGet(UserDTO::new);
    }

    @ResponseBody
    @PostMapping(value = "searchPassword")
    public UserDTO searchPassword(HttpServletRequest request, ModelMap model, HttpSession session) throws Exception {
        this.callLog("searchPassword");

        String id = CmmUtil.nvl(request.getParameter("id"));
        String name = CmmUtil.nvl(request.getParameter("name"));
        String email = CmmUtil.nvl(request.getParameter("email"));

        log.info("id: {} / name: {} / email: {} ", id, name, email);

        UserDTO pDTO = new UserDTO();
        pDTO.setId(id);
        pDTO.setName(name);
        pDTO.setEmail(EncryptUtil.encAES128CBC(email));

        return Optional.ofNullable(userService.searchUserIdOrPasswordProc(pDTO)).orElseGet(UserDTO::new);
    }

    @PostMapping(value = "newPasswordProc")
    public String newPasswordProc(HttpServletRequest request, ModelMap model, HttpSession session) throws Exception {
        this.callLog("newPasswordProc");

        String msg;

        String newPassword = CmmUtil.nvl((String) session.getAttribute("NEW_PASSWORD"));

        if (!newPassword.isEmpty()) {

            String password = CmmUtil.nvl(request.getParameter("password"));

            UserDTO pDTO = new UserDTO();
            pDTO.setId(newPassword);
            pDTO.setPassword(EncryptUtil.encHashSHA256(password));

            userService.newPasswordProc(pDTO);

            session.setAttribute("NEW_PASSWORD", "");
            session.removeAttribute("NEW_PASSWORD");

            msg = "비밀번호가 재설정되었습니다.";

        } else {
            msg = "비정상 접근입니다.";
        }

        return msg;
    }
}