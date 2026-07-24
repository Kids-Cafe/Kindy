package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.MsgDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.CmmUtil;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RequestMapping(value = "/api/user")
@RequiredArgsConstructor
@Controller
public class UserController {

    private final IUserService userService;
    private final EncryptUtil encryptUtil;
    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @ResponseBody
    @GetMapping(value = "getIdExists")
    public ResultDTO getIdExists(HttpServletRequest request) throws Exception {
        this.callLog("getIdExists");

        String id = request.getParameter("id");

        if (id == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("id: {}", id);

        UserDTO user = userService.getIdExists(UserDTO.fromId(id));

        return ResultDTO.success(user.isExists() ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @ResponseBody
    @GetMapping(value = "getEmailExists")
    public ResultDTO getEmailExists(HttpServletRequest request) throws Exception {
        this.callLog("getEmailExists");

        String email = request.getParameter("email");

        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("email: {}", email);

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(encryptUtil.encAES128CBC(email));
        UserDTO user = userService.getEmailExists(pDTO);

        return ResultDTO.success(user.isExists() ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @ResponseBody
    @PostMapping(value = "insertUser")
    public ResultDTO insertUser(HttpServletRequest request) {
        this.callLog("insertUser");

        ResultDTO result;

        try {
            UserDTO pDTO = new UserDTO();
            pDTO.setId(request.getParameter("id"));
            if (pDTO.getId() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setName(request.getParameter("name"));
            if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setPassword(encryptUtil.encHashSHA256(request.getParameter("password")));
            pDTO.setEmail(encryptUtil.encAES128CBC(request.getParameter("email")));
            pDTO.setAddr1(encryptUtil.encAES128CBC(request.getParameter("addr1")));
            if (pDTO.getAddr1() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setAddr2(encryptUtil.encAES128CBC(request.getParameter("addr2")));

            log.info(pDTO.toString());

            int res = userService.insertUser(pDTO);

            log.info("회원가입 결과: " + res);

            if (res == 1) {
                result = ResultDTO.success("SIGNUP_COMPLETE");
            } else if (res == 2) {
                result = ResultDTO.error("DUPLICATE_ID");
            } else {
                result = ResultDTO.error("UNKNOWN_ERROR");
            }

        } catch (Exception e) {
            result = ResultDTO.error("UNKNOWN_ERROR", e);
            log.info(e.toString());
        }

        return result;
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

            pDTO.setPassword(encryptUtil.encHashSHA256(password));

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
        pDTO.setEmail(encryptUtil.encAES128CBC(email));

        return Optional.ofNullable(userService.searchIdOrPassword(pDTO))
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
        pDTO.setEmail(encryptUtil.encAES128CBC(email));

        return Optional.ofNullable(userService.searchIdOrPassword(pDTO)).orElseGet(UserDTO::new);
    }

    @PostMapping(value = "newPassword")
    public String newPassword(HttpServletRequest request, ModelMap model, HttpSession session) throws Exception {
        this.callLog("newPassword");

        String msg;

        String newPassword = CmmUtil.nvl((String) session.getAttribute("NEW_PASSWORD"));

        if (!newPassword.isEmpty()) {

            String password = CmmUtil.nvl(request.getParameter("password"));

            UserDTO pDTO = new UserDTO();
            pDTO.setId(newPassword);
            pDTO.setPassword(encryptUtil.encHashSHA256(password));

            userService.newPassword(pDTO);

            session.setAttribute("NEW_PASSWORD", "");
            session.removeAttribute("NEW_PASSWORD");

            msg = "비밀번호가 재설정되었습니다.";

        } else {
            msg = "비정상 접근입니다.";
        }

        return msg;
    }
}