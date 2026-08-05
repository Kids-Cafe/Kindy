package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

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

        if (user == null) return ResultDTO.error("UNKNOWN_ERROR");

        return ResultDTO.success("QUERY_COMPLETE", user);
    }

    @GetMapping(value = "getEmailExists")
    public ResultDTO getEmailExists(HttpServletRequest request) throws Exception {
        log.info("Calling getEmailExists");

        String email = request.getParameter("email");

        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("email: {}", email);

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(email);
        UserDTO user = userService.getEmailExists(pDTO);

        if (user == null) return ResultDTO.error("UNKNOWN_ERROR");

        return ResultDTO.success("QUERY_COMPLETE", user);
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
            String password = request.getParameter("password");
            if (password != null && password.length() < 8) return ResultDTO.error("INVALID_PARAMETER");
            byte[] salt = encryptUtil.getSecureSalt();
            pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
            if (pDTO.getPassword() != null) pDTO.setPasswordSalt(salt);
            pDTO.setEmail(request.getParameter("email"));
            if (pDTO.getEmail() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setAddress(encryptUtil.encAES128CBC(request.getParameter("address")));
            if (pDTO.getAddress() == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setAddressDetail(encryptUtil.encAES128CBC(request.getParameter("addressDetail")));
            pDTO.setPostcode(encryptUtil.encAES128CBC(request.getParameter("postcode")));
            if (pDTO.getPostcode() == null) return ResultDTO.error("MISSING_PARAMETER");

            log.info("User Register Attempt: {}", pDTO.getId());
            log.info("Data: {}, {}, {}", password, pDTO.getPasswordSalt(), pDTO.getPassword());

            int res = userService.insertUser(pDTO);

            log.info("User Register Result: {}", res);

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
            String password = request.getParameter("password");
            if (password == null) return ResultDTO.error("MISSING_PARAMETER");

            log.info("Login Attempt: {}", pDTO.getId());

            UserDTO rDTO = userService.login(pDTO);

            if (rDTO == null || rDTO.getPassword() == null) return ResultDTO.error("SIGNIN_NO_MATCHES");

            if (!Arrays.equals(encryptUtil.encHashSHA256(password, rDTO.getPasswordSalt()), rDTO.getPassword())) return ResultDTO.error("SIGNIN_NO_MATCHES");

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
        pDTO.setEmail(request.getParameter("email"));
        if (pDTO.getEmail() == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info(pDTO.toString());

        UserDTO user = userService.searchIdOrPassword(pDTO);

        return ResultDTO.success(user != null ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @PostMapping(value = "searchPassword")
    public ResultDTO searchPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling searchPassword");

        UserDTO pDTO = new UserDTO();

        pDTO.setId(request.getParameter("id"));
        if (pDTO.getId() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setEmail(request.getParameter("email"));
        if (pDTO.getEmail() == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: add email auth before proceeding

        UserDTO rDTO = userService.searchIdOrPassword(pDTO);

        if (rDTO == null) return ResultDTO.error("USER_NOT_FOUND");

        session.setAttribute("NEW_PASSWORD", rDTO.getId());

        return ResultDTO.success("USER_FOUND", rDTO);
    }

    @PostMapping(value = "newPassword")
    public ResultDTO newPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newPassword");

        String newPassword = (String) session.getAttribute("NEW_PASSWORD");

        if (newPassword == null || newPassword.isEmpty()) return ResultDTO.error("INVALID_ACCESS");

        String password = request.getParameter("password");

        if (password == null) return ResultDTO.error("MISSING_PARAMETER");
        if (password.length() < 8) return ResultDTO.error("INVALID_PARAMETER");

        byte[] salt = encryptUtil.getSecureSalt();
        UserDTO pDTO = new UserDTO();
        pDTO.setId(newPassword);
        pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
        pDTO.setPasswordSalt(salt);

        userService.newPassword(pDTO);

        session.removeAttribute("NEW_PASSWORD");

        return ResultDTO.success("PASSWORD_UPDATED");
    }


    @PostMapping(value = "newEmail")
    public ResultDTO newEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newEmail");

        String newEmail = (String) session.getAttribute("NEW_EMAIL");

        if (newEmail == null || newEmail.isEmpty()) return ResultDTO.error("INVALID_ACCESS");

        String email = request.getParameter("email");

        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        UserDTO pDTO = new UserDTO();
        pDTO.setId(newEmail);
        pDTO.setEmail(email);

        userService.updateEmail(pDTO);

        return ResultDTO.success("EMAIL_UPDATED");
    }
}