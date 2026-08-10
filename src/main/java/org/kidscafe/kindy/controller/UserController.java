package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping(value = "/api/user")
@RequiredArgsConstructor
@RestController
public class UserController {
    private final IUserService userService;
    private final EncryptUtil encryptUtil;

    @GetMapping(value = "getIdExists")
    public ResultDTO<UserDTO> getIdExists(HttpServletRequest request) throws Exception {
        log.info("Calling getIdExists");

        String id = request.getParameter("id");

        if (id == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("id: {}", id);

        UserDTO user = userService.getIdExists(id);

        if (user == null) return ResultDTO.error("UNKNOWN_ERROR");

        return ResultDTO.success("QUERY_COMPLETE", user);
    }

    @GetMapping(value = "getEmailExists")
    public ResultDTO<UserDTO> getEmailExists(HttpServletRequest request) throws Exception {
        log.info("Calling getEmailExists");

        String email = request.getParameter("email");

        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("email: {}", email);

        UserDTO user = userService.getEmailExists(email);

        if (user == null) return ResultDTO.error("UNKNOWN_ERROR");

        return ResultDTO.success("QUERY_COMPLETE", user);
    }

    @PostMapping(value = "insertUser")
    public ResultDTO<Void> insertUser(HttpServletRequest request) {
        log.info("Calling insertUser");

        UserDTO pDTO = new UserDTO();
        pDTO.setId(request.getParameter("id"));
        if (pDTO.getId() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (pDTO.getId().length() < 4) return ResultDTO.error("INVALID_PARAMETER");
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
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

            int res = userService.insertUser(pDTO);

            log.info("User Register Result: {}", res);

            if (res == 1) {
                return ResultDTO.success("SIGNUP_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            log.info("Duplicate ID: {}", pDTO.getId());
            return ResultDTO.error("DUPLICATE_ID");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "login")
    public ResultDTO<Void> login(HttpServletRequest request, HttpSession session) {
        log.info("Calling login");

        try {
            String id = request.getParameter("id");
            if (id == null) return ResultDTO.error("MISSING_PARAMETER");
            String password = request.getParameter("password");
            if (password == null) return ResultDTO.error("MISSING_PARAMETER");

            log.info("Login Attempt: {}", id);

            UserDTO rDTO = userService.login(id, password);
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
    public ResultDTO<UserDTO> session(HttpSession session) {
        log.info("Calling session");
        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.success("NOT_SIGNED_IN", null);
        UserDTO rDTO = UserDTO.fromId(id);
        rDTO.setName((String) session.getAttribute("SESSION_USER_NAME"));
        return ResultDTO.success("SIGNED_IN", rDTO);
    }

    @GetMapping(value = "info")
    public ResultDTO<UserDTO.PlainUserDTO> info(HttpSession session) throws Exception {
        log.info("Calling info");
        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");
        UserDTO rDTO = userService.getInfo(id);
        if (rDTO == null) return ResultDTO.error("USER_NOT_FOUND");
        return ResultDTO.success("QUERY_COMPLETE", new UserDTO.PlainUserDTO(
                rDTO.getId(),
                rDTO.getName(),
                rDTO.getEmail(),
                encryptUtil.decAES128CBC(rDTO.getAddress()),
                encryptUtil.decAES128CBC(rDTO.getAddressDetail()),
                encryptUtil.decAES128CBC(rDTO.getPostcode()),
                rDTO.getCreatedAt(),
                rDTO.getUpdatedAt()
        ));
    }

    @PostMapping(value = "update")
    public ResultDTO<Void> update(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling update");

        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");
        UserDTO pDTO = UserDTO.fromId(id);
        pDTO.setAddress(encryptUtil.encAES128CBC(request.getParameter("address")));
        if (pDTO.getAddress() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setAddressDetail(encryptUtil.encAES128CBC(request.getParameter("addressDetail")));
        pDTO.setPostcode(encryptUtil.encAES128CBC(request.getParameter("postcode")));
        if (pDTO.getPostcode() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            userService.updateInfo(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "logout")
    public ResultDTO<Void> logout(HttpSession session) {
        log.info("Calling logout");
        session.invalidate();
        return ResultDTO.success("SIGNOUT_COMPLETE");
    }

    @PostMapping(value = "searchId")
    public ResultDTO<UserDTO> searchId(HttpServletRequest request) throws Exception {
        log.info("Calling searchId");

        String name = request.getParameter("name");
        if (name == null) return ResultDTO.error("MISSING_PARAMETER");
        String email = request.getParameter("email");
        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        UserDTO user = userService.getId(name, email);

        return ResultDTO.success(user != null ? "USER_FOUND" : "USER_NOT_FOUND", user);
    }

    @PostMapping(value = "searchPassword")
    public ResultDTO<UserDTO> searchPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling searchPassword");

        String id = request.getParameter("id");
        if (id == null) return ResultDTO.error("MISSING_PARAMETER");
        String name = request.getParameter("name");
        if (name == null) return ResultDTO.error("MISSING_PARAMETER");
        String email = request.getParameter("email");
        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: add email auth before proceeding

        UserDTO rDTO = userService.getId(name, email, id);

        if (rDTO == null) return ResultDTO.error("USER_NOT_FOUND");

        session.setAttribute("NEW_PASSWORD", rDTO.getId());

        return ResultDTO.success("USER_FOUND", rDTO);
    }

    @PostMapping(value = "newPassword")
    public ResultDTO<Void> newPassword(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newPassword");

        String newPassword = (String) session.getAttribute("NEW_PASSWORD");

        if (newPassword == null || newPassword.isEmpty()) return ResultDTO.error("INVALID_ACCESS");

        String password = request.getParameter("password");

        if (password == null) return ResultDTO.error("MISSING_PARAMETER");
        if (password.length() < 8) return ResultDTO.error("INVALID_PARAMETER");

        userService.newPassword(newPassword, password);

        session.removeAttribute("NEW_PASSWORD");

        return ResultDTO.success("UPDATE_COMPLETE");
    }


    @PostMapping(value = "newEmail")
    public ResultDTO<Void> newEmail(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling newEmail");

        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");

        String email = request.getParameter("email");
        if (email == null) return ResultDTO.error("MISSING_PARAMETER");
        String password = request.getParameter("password");
        if (password == null) return ResultDTO.error("MISSING_PARAMETER");

        userService.updateEmail(id, email, password);

        return ResultDTO.success("UPDATE_COMPLETE");
    }
}