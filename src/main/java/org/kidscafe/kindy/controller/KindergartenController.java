package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
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
        String brn = request.getParameter("brn");
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (brn != null) return ResultDTO.error("INVALID_PARAMETER");
        } catch (NumberFormatException e) {
            if (brn == null) return ResultDTO.error("INVALID_PARAMETER");
            id = -1;
        }

        // TODO: check if the user has access

        try {
            if (brn != null) return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getInfoByBrn(brn));
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
        pDTO.setOwner(userId);
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

    @GetMapping(value = "memberships")
    public ResultDTO<List<RelationshipDTO>> memberships(HttpSession session) {
        log.info("Calling memberships");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getMemberships(sessionUserId));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "join")
    public ResultDTO<Void> join(HttpServletRequest request, HttpSession session) {
        log.info("Calling join");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String type = request.getParameter("type");

        try {
            kindergartenService.requestJoin(id, sessionUserId, RelationshipDTO.Type.valueOf(type));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("JOIN_REQUESTED");
    }

    @PostMapping(value = "invite")
    public ResultDTO<Void> invite(HttpServletRequest request, HttpSession session) {
        log.info("Calling invite");

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
        Long roleId = null;
        try {
            String roleIdParam = request.getParameter("roleId");
            if (roleIdParam != null) roleId = Long.parseLong(roleIdParam);
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            kindergartenService.inviteUser(id, sessionUserId, userId, RelationshipDTO.Type.valueOf(type), roleId);
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("INVITE_COMPLETE");
    }

    @PostMapping(value = "invite/cancel")
    public ResultDTO<Void> cancelInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling cancelInvite");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.cancelInvite(id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("CANCEL_COMPLETE");
    }

    @PostMapping(value = "invite/accept")
    public ResultDTO<Void> acceptInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling acceptInvite");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.acceptInvite(id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ACCEPT_COMPLETE");
    }

    @PostMapping(value = "invite/reject")
    public ResultDTO<Void> rejectInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling rejectInvite");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.rejectInvite(id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("REJECT_COMPLETE");
    }

    @GetMapping(value = "invite/list")
    public ResultDTO<List<InviteDTO>> inviteList(HttpServletRequest request, HttpSession session) {
        log.info("Calling inviteList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getInvites(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
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

    @GetMapping(value = "role/list")
    public ResultDTO<List<RoleDTO>> roleList(HttpServletRequest request, HttpSession session) {
        log.info("Calling roleList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getRoles(kindergartenId));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/create")
    public ResultDTO<Void> createRole(HttpServletRequest request, HttpSession session) {
        log.info("Calling createRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String name = request.getParameter("name");
        if (name == null) return ResultDTO.error("INVALID_PARAMETER");

        // TODO: Permission check

        try {
            kindergartenService.createRole(kindergartenId, name);
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("CREATE_COMPLETE");
    }

    @PostMapping(value = "role/permissions")
    public ResultDTO<List<RoleDTO.Permission>> rolePermissions(HttpServletRequest request, HttpSession session) {
        log.info("Calling rolePermissions");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getRolePermissions(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/rename")
    public ResultDTO<Void> renameRole(HttpServletRequest request, HttpSession session) {
        log.info("Calling renameRole");

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

        try {
            kindergartenService.renameRole(id, name);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/delete")
    public ResultDTO<Void> deleteRole(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.deleteRole(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "notice/list")
    public ResultDTO<List<NoticeDTO>> noticeList(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling noticeList");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        String userId = (String) session.getAttribute("SESSION_USER_ID");

        // TODO: Access check

        return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getNotices(kindergartenId));
    }

    @GetMapping(value = "notice/info")
    public ResultDTO<NoticeDTO> noticeInfo(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling noticeInfo");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        String userId = (String) session.getAttribute("SESSION_USER_ID");

        // TODO: Access check

        try {
            NoticeDTO rDTO = kindergartenService.getNoticeInfo(id);

            if (rDTO == null) return ResultDTO.error("NOTICE_NOT_FOUND");

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
    }

    @PostMapping(value = "notice/create")
    public ResultDTO<Void> createNotice(HttpServletRequest request, HttpSession session) {
        log.info("Calling createNotice");

        try {
            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            // TODO: Access check

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            pDTO.setAuthor(userId);
            pDTO.setTitle(request.getParameter("title"));
            pDTO.setPinned(Boolean.parseBoolean(request.getParameter("pinned")));
            pDTO.setContent(request.getParameter("content"));
            log.info(pDTO.toString());

            kindergartenService.createNotice(pDTO);

            return ResultDTO.success("REGISTER_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "notice/edit")
    public ResultDTO<Void> editNotice(HttpServletRequest request, HttpSession session) {
        log.info("Calling editNotice");

        try {
            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            // TODO: Access check

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

            kindergartenService.updateNotice(pDTO);

            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "notice/delete")
    public ResultDTO<Void> deleteNotice(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteNotice");

        try {
            long id;
            try {
                id = Long.parseLong(request.getParameter("id"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            String userId = (String) session.getAttribute("SESSION_USER_ID");

            // TODO: Access check

            try {
                kindergartenService.deleteNotice(id);
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "schedule/list")
    public ResultDTO<List<ScheduleDTO>> scheduleList(HttpServletRequest request, HttpSession session) {
        log.info("Calling scheduleList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getSchedules(kindergartenId));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "schedule/info")
    public ResultDTO<ScheduleDTO> getScheduleInfo(HttpServletRequest request, HttpSession session) {
        log.info("Calling getSchedule");

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
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getScheduleInfo(id));
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/create")
    public ResultDTO<Void> createSchedule(HttpServletRequest request, HttpSession session) {
        log.info("Calling createSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Access check

        ScheduleDTO pDTO = new ScheduleDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setTime(request.getParameter("time"));
        pDTO.setTitle(request.getParameter("title"));
        if (pDTO.getTitle() == null) return ResultDTO.error("MISSING_PARAMETER");
        try {
            String classId = request.getParameter("classId");
            if (classId != null) pDTO.setClassId(Long.parseLong(classId));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.createSchedule(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/edit")
    public ResultDTO<Void> editSchedule(HttpServletRequest request, HttpSession session) {
        log.info("Calling editSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        ScheduleDTO pDTO = new ScheduleDTO();
        pDTO.setId(id);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setTime(request.getParameter("time"));
        pDTO.setTitle(request.getParameter("title"));
        if (pDTO.getTitle() == null) return ResultDTO.error("MISSING_PARAMETER");
        try {
            String classId = request.getParameter("classId");
            if (classId != null) pDTO.setClassId(Long.parseLong(classId));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.updateSchedule(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/delete")
    public ResultDTO<Void> deleteSchedule(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            kindergartenService.deleteSchedule(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
