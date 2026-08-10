package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.KindergartenDTO;
import org.kidscafe.kindy.dto.RelationshipDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.RoleDTO;
import org.kidscafe.kindy.service.IKindergartenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping(value = "/api/kindergarten")
@RequiredArgsConstructor
@RestController
public class KindergartenController {
    private final IKindergartenService kindergartenService;

    @GetMapping(value = "list")
    public ResultDTO<List<KindergartenDTO>> list() {
        log.info("Calling list");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getList());
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<KindergartenDTO> info(HttpServletRequest request, HttpSession session) {
        log.info("Calling info");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: check if the user has access

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getInfo(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "create")
    public ResultDTO<Void> create(HttpServletRequest request, HttpSession session) {
        log.info("Calling create");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        // TODO: BRN verification

        KindergartenDTO pDTO = new KindergartenDTO();
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setBrn(request.getParameter("brn"));
        if (pDTO.getBrn() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (pDTO.getBrn().length() != 10) return ResultDTO.error("INVALID_PARAMETER");
        pDTO.setAddress(request.getParameter("address"));
        if (pDTO.getAddress() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setAddressDetail(request.getParameter("addressDetail"));
        if (pDTO.getAddressDetail() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setPostcode(request.getParameter("postcode"));
        if (pDTO.getPostcode() == null) return ResultDTO.error("MISSING_PARAMETER");
        try {
            int res = kindergartenService.create(pDTO);
            if (res == 1) {
                return ResultDTO.success("REGISTER_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            log.info("Duplicate BRN: {}", pDTO.getBrn());
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "update")
    public ResultDTO<Void> update() {
        log.info("Calling update");
        // TODO: BRN verification
        return ResultDTO.error("NOT_AVAILABLE");
    }

    @PostMapping(value = "transfer")
    public ResultDTO<Void> transfer(HttpServletRequest request, HttpSession session) {
        log.info("Calling transfer");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Owner verification

        String owner = (request.getParameter("owner"));
        if (owner == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            int res = kindergartenService.transfer(id, owner);
            if (res == 1) {
                return ResultDTO.success("TRANSFER_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "members")
    public ResultDTO<List<RelationshipDTO>> members(HttpServletRequest request, HttpSession session) {
        log.info("Calling members");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getMembers(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "join")
    public ResultDTO<Void> join() {
        // TODO
        return ResultDTO.error("NOT_AVAILABLE");
    }

    @PostMapping(value = "invite")
    public ResultDTO<Void> invite() {
        // TODO
        return ResultDTO.error("NOT_AVAILABLE");
    }

    @PostMapping(value = "add")
    public ResultDTO<Void> add(HttpServletRequest request, HttpSession session) {
        // TODO: Replace this with join() and invite()
        log.info("Calling add");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");
        String type = request.getParameter("type");

        // TODO: Permission check

        try {
            kindergartenService.add(id, userId, RelationshipDTO.Type.valueOf(type));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ADD_COMPLETE");
    }

    @PostMapping(value = "assign")
    public ResultDTO<Void> assign(HttpServletRequest request, HttpSession session) {
        log.info("Calling assign");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");
        long roleId;
        try {
            roleId = Long.parseLong(request.getParameter("roleId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.assign(id, userId, roleId);
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ASSIGN_COMPLETE");
    }

    @PostMapping(value = "setNickname")
    public ResultDTO<Void> setNickname(HttpServletRequest request, HttpSession session) {
        // TODO: Replace this with join() and invite()
        log.info("Calling setNickname");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");
        String nickname = request.getParameter("nickname");

        try {
            kindergartenService.setNickname(id, userId, nickname);
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("UPDATE_COMPLETE");
    }

    @PostMapping(value = "remove")
    public ResultDTO<Void> remove(HttpServletRequest request, HttpSession session) {
        log.info("Calling remove");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: Permission check

        try {
            kindergartenService.remove(id, userId);
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("REMOVE_COMPLETE");
    }

    @GetMapping(value = "has")
    public ResultDTO<RelationshipDTO> has(HttpServletRequest request, HttpSession session) {
        log.info("Calling has");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.has(id, userId));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "roles")
    public ResultDTO<List<RoleDTO>> roles(HttpServletRequest request, HttpSession session) {
        log.info("Calling roles");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getRoles(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/create")
    public ResultDTO<Void> createRole(HttpServletRequest request, HttpSession session) {
        log.info("Calling createRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String name = request.getParameter("name");
        if (name == null) return ResultDTO.error("INVALID_PARAMETER");

        // TODO: Permission check

        try {
            kindergartenService.createRole(id, name);
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("CREATE_COMPLETE");
    }
}
