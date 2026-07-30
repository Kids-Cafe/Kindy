package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.CmmUtil;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RequestMapping(value = "/api/user")
@RequiredArgsConstructor
@RestController
public class UserController {
    private final IUserService userService;
    private final EncryptUtil encryptUtil;

    @GetMapping(value = "getIdExists")
    public ResultDTO getIdExists(HttpServletRequest request) throws Exception {
        log.info("Calling getIdExists");

        String id = request.getParameter("id");

        if (id == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("id: {}", id);

        UserDTO user = userService.getIdExists(UserDTO.fromId(id));

        return ResultDTO.success(user != null ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @GetMapping(value = "getEmailExists")
    public ResultDTO getEmailExists(HttpServletRequest request) throws Exception {
        log.info("Calling getEmailExists");

        String email = request.getParameter("email");

        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("email: {}", email);

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(encryptUtil.encAES128CBC(email));
        UserDTO user = userService.getEmailExists(pDTO);

        return ResultDTO.success(user != null ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @PostMapping(value = "insertUser")
    public ResultDTO insertUser(HttpServletRequest request) {
        log.info("Calling insertUser");

        ResultDTO result;

        try {
            UserDTO pDTO = new UserDTO();
            pDTO.setId(request.getParameter("id"));
            if (pDTO.getId() == null) return ResultDTO.error("MISSING_PARAMETER");
            if (pDTO.getId().length() < 4) return ResultDTO.error("INVALID_PARAMETER");
            pDTO.setName(request.getParameter("name"));
            if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setPassword(encryptUtil.encHashSHA256(request.getParameter("password")));
            pDTO.setEmail(encryptUtil.encAES128CBC(request.getParameter("email")));
            pDTO.setAddr1(encryptUtil.encAES128CBC(request.getParameter("addr1")));
            if (pDTO.getAddr1() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setAddr2(encryptUtil.encAES128CBC(request.getParameter("addr2")));

            log.info(pDTO.toString());

            int res = userService.insertUser(pDTO);

            log.info("User Register Result: " + res);

            if (res == 1) {
                result = ResultDTO.success("SIGNUP_COMPLETE");
            } else if (res == 2) {
                result = ResultDTO.error("DUPLICATE_ID");
            } else {
                result = ResultDTO.error("UNKNOWN_ERROR");
            }

        } catch (Exception e) {
            result = ResultDTO.error("UNKNOWN_ERROR");
            log.info(e.toString());
        }

        return result;
    }

    @PostMapping(value = "login")
    public ResultDTO login(HttpServletRequest request, HttpSession session) {
        log.info("Calling login");

        try {
            UserDTO pDTO = new UserDTO();

            pDTO.setId(request.getParameter("id"));
            if (pDTO.getId() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setPassword(encryptUtil.encHashSHA256(request.getParameter("password")));
            if (pDTO.getPassword() == null) return ResultDTO.error("MISSING_PARAMETER");

            log.info("Login Attempt: {}", pDTO.getId());

            UserDTO rDTO = userService.login(pDTO);

            if (rDTO == null) return ResultDTO.error("SIGNIN_NO_MATCHES");

            session.invalidate();
            session = request.getSession(true);
            session.setMaxInactiveInterval(3600);
            session.setAttribute("SESSION_USER_ID", rDTO.getId());
            session.setAttribute("SESSION_USER_NAME", rDTO.getName());

            return ResultDTO.success("SIGNIN_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "session")
    public ResultDTO session(HttpSession session) {
        log.info("Calling session");
        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.success("NOT_SIGNED_IN", null);
        UserDTO rDTO = UserDTO.fromId(id);
        rDTO.setName((String) session.getAttribute("SESSION_USER_NAME"));
        return ResultDTO.success("SIGNED_IN", rDTO);
    }

    @PostMapping(value = "logout")
    public ResultDTO logout(HttpSession session) {
        log.info("Calling logout");
        session.invalidate();
        return ResultDTO.success("SIGNOUT_COMPLETE");
    }

    @PostMapping(value = "searchId")
    public ResultDTO searchId(HttpServletRequest request) throws Exception {
        log.info("Calling searchId");

        UserDTO pDTO = new UserDTO();
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setEmail(encryptUtil.encAES128CBC(request.getParameter("email")));
        if (pDTO.getEmail() == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info(pDTO.toString());

        UserDTO user = userService.searchIdOrPassword(pDTO);

        return ResultDTO.success(user != null ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @PostMapping(value = "searchPassword")
    public ResultDTO searchPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling searchPassword");

        String userId = CmmUtil.nvl(request.getParameter("userId"));
        String userName = CmmUtil.nvl(request.getParameter("userName"));
        String email = CmmUtil.nvl(request.getParameter("email"));

        UserDTO pDTO = new UserDTO();
        pDTO.setId(userId);
        pDTO.setName(userName);
        pDTO.setEmail(encryptUtil.encAES128CBC(email));

        UserDTO rDTO = userService.searchIdOrPassword(pDTO);

        session.setAttribute("NEW_PASSWORD", userId);

        return ResultDTO.success(rDTO != null ? "USER_FOUND" : "USER_NOT_FOUND", rDTO);
    }

    @PostMapping(value = "newPassword")
    public ResultDTO newPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newPassword");

        String newPassword = CmmUtil.nvl((String) session.getAttribute("NEW_PASSWORD"));

        if (!newPassword.isEmpty()) {

            String password = CmmUtil.nvl(request.getParameter("password"));

            UserDTO pDTO = new UserDTO();
            pDTO.setId(newPassword);
            pDTO.setPassword(encryptUtil.encHashSHA256(password));

            userService.newPassword(pDTO);

            session.setAttribute("NEW_PASSWORD", "");
            session.removeAttribute("NEW_PASSWORD");

            return ResultDTO.success("PASSWORD_RESET");
        } else {
            return ResultDTO.error("INVALID_ACCESS");
        }
    }


    @PostMapping(value = "newEmail")
    public ResultDTO newEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newEmail");

        String newEmail = CmmUtil.nvl((String) session.getAttribute("NEW_EMAIL"));

        if (!newEmail.isEmpty()) {

            String Email = CmmUtil.nvl(request.getParameter("password"));       //'Email' 부분만 이상하게 회색인거 빼곤 끝

            UserDTO pDTO = new UserDTO();
            pDTO.setId(newEmail);

            userService.newEmail(pDTO);

            return ResultDTO.success("EMAIL_RESET");
        } else {
            return ResultDTO.error("INVALID_ACCESS");

        }
    }
}