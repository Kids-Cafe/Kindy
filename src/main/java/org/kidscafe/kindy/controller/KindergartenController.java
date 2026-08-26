package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.service.IAccessService;
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
    private final IAccessService accessService;

    // `q` is optional: with it, this is the signup-time kindergarten search; without it, the
    // (capped) full list, as before.
    @GetMapping(value = "list")
    public ResultDTO<List<KindergartenDTO>> list(HttpServletRequest request) {
        log.debug("Calling list");

        String q = request.getParameter("q");
        if (q != null) {
            q = q.trim();
            if (q.isEmpty()) q = null;
        }

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getList(q));
        } catch (Exception e) {
            log.warn("list failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<KindergartenDTO> info(HttpServletRequest request, HttpSession session) {
        log.debug("Calling info");

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

        try {
            KindergartenDTO rDTO = brn != null ? kindergartenService.getInfoByBrn(brn) : kindergartenService.getInfo(id);
            if (rDTO == null) return ResultDTO.error("KINDERGARTEN_NOT_FOUND");

            // Signup search needs to resolve a kindergarten before the user belongs to it, so
            // outsiders still get the public fields — but not the owner or the BRN.
            if (!accessService.canView(rDTO.getId(), userId)) return ResultDTO.success("QUERY_COMPLETE", publicInfo(rDTO));

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (Exception e) {
            log.warn("info failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** The subset of a kindergarten anyone signed in may see — the same fields `list` returns. */
    private static KindergartenDTO publicInfo(KindergartenDTO pDTO) {
        KindergartenDTO rDTO = KindergartenDTO.fromId(pDTO.getId());
        rDTO.setName(pDTO.getName());
        rDTO.setAddress(pDTO.getAddress());
        rDTO.setAddressDetail(pDTO.getAddressDetail());
        rDTO.setPostcode(pDTO.getPostcode());
        return rDTO;
    }

    // Returns the created row (with its generated id) so the caller doesn't have to look it up by BRN.
    @PostMapping(value = "create")
    public ResultDTO<KindergartenDTO> create(HttpServletRequest request, HttpSession session) {
        log.debug("Calling create");

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
                log.info("Kindergarten created: id={} name={} owner={}", pDTO.getId(), pDTO.getName(), userId);
                return ResultDTO.success("REGISTER_COMPLETE", pDTO);
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate BRN: {}", pDTO.getBrn());
            return ResultDTO.error("DUPLICATE_KEY");
        // Enrolling the owner refuses a child account: running a kindergarten is an adult's job.
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("create failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "update")
    public ResultDTO<Void> update() {
        log.debug("Calling update");
        // TODO: BRN verification
        return ResultDTO.error("NOT_AVAILABLE");
    }

    @PostMapping(value = "transfer")
    public ResultDTO<Void> transfer(HttpServletRequest request, HttpSession session) {
        log.debug("Calling transfer");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.isOwner(id, userId)) return ResultDTO.error("INVALID_ACCESS");

        String owner = (request.getParameter("owner"));
        if (owner == null) return ResultDTO.error("MISSING_PARAMETER");
        // Handing the kindergarten to someone who isn't in it would lock everyone out of it.
        if (!accessService.isMember(id, owner)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            int res = kindergartenService.transfer(id, owner);
            if (res == 1) {
                log.info("Kindergarten {} transferred to {} by {}", id, owner, userId);
                return ResultDTO.success("TRANSFER_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        // A child attends the kindergarten; they cannot be handed ownership of it.
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("transfer failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "members")
    public ResultDTO<List<RelationshipDTO>> members(HttpServletRequest request, HttpSession session) {
        log.debug("Calling members");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canView(id, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getMembers(id));
        } catch (Exception e) {
            log.warn("members failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "memberships")
    public ResultDTO<List<RelationshipDTO>> memberships(HttpSession session) {
        log.debug("Calling memberships");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getMemberships(sessionUserId));
        } catch (Exception e) {
            log.warn("memberships failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "join")
    public ResultDTO<Void> join(HttpServletRequest request, HttpSession session) {
        log.debug("Calling join");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String type = request.getParameter("type");

        // Children do not apply for themselves — a guardian names the child in userId. Omitting it
        // keeps the original meaning: the caller is applying on their own behalf.
        String userId = request.getParameter("userId");
        if (userId == null || userId.isBlank()) userId = sessionUserId;

        try {
            kindergartenService.requestJoin(id, sessionUserId, userId, RelationshipDTO.Type.valueOf(type));
            log.info("Join requested: kindergarten={} user={} by {}", id, userId, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("join failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("JOIN_REQUESTED");
    }

    @PostMapping(value = "invite")
    public ResultDTO<Void> invite(HttpServletRequest request, HttpSession session) {
        log.debug("Calling invite");

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

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        // A role from another kindergarten would grant nothing here and confuse the members list.
        if (roleId != null && !Long.valueOf(id).equals(accessService.getKindergartenOfRole(roleId)))
            return ResultDTO.error("INVALID_PARAMETER");

        try {
            kindergartenService.inviteUser(id, sessionUserId, userId, RelationshipDTO.Type.valueOf(type), roleId);
            log.info("Invite sent: kindergarten={} to={} by={} role={}", id, userId, sessionUserId, roleId);
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("invite failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("INVITE_COMPLETE");
    }

    @PostMapping(value = "invite/cancel")
    public ResultDTO<Void> cancelInvite(HttpServletRequest request, HttpSession session) {
        log.debug("Calling cancelInvite");

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
            log.info("Invite {} cancelled by {}", id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("cancelInvite failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("CANCEL_COMPLETE");
    }

    @PostMapping(value = "invite/accept")
    public ResultDTO<Void> acceptInvite(HttpServletRequest request, HttpSession session) {
        log.debug("Calling acceptInvite");

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
            log.info("Invite {} accepted by {}", id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        // IllegalStateException: the ticket is gone or already answered.
        // IllegalArgumentException: its TYPE does not match the account it names — a stale ticket
        // from before that rule existed. Either way the ticket, not the caller, is the problem.
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("acceptInvite failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ACCEPT_COMPLETE");
    }

    @PostMapping(value = "invite/reject")
    public ResultDTO<Void> rejectInvite(HttpServletRequest request, HttpSession session) {
        log.debug("Calling rejectInvite");

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
            log.info("Invite {} rejected by {}", id, sessionUserId);
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("rejectInvite failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("REJECT_COMPLETE");
    }

    @GetMapping(value = "invite/list")
    public ResultDTO<List<InviteDTO>> inviteList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling inviteList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getInvites(id));
        } catch (Exception e) {
            log.warn("inviteList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "add")
    public ResultDTO<Void> add(HttpServletRequest request, HttpSession session) {
        // TODO: Replace this with join() and invite()
        log.debug("Calling add");

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

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.add(id, userId, RelationshipDTO.Type.valueOf(type));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("add failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ADD_COMPLETE");
    }

    @PostMapping(value = "assign")
    public ResultDTO<Void> assign(HttpServletRequest request, HttpSession session) {
        log.debug("Calling assign");

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

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        if (!Long.valueOf(id).equals(accessService.getKindergartenOfRole(roleId)))
            return ResultDTO.error("INVALID_PARAMETER");

        try {
            kindergartenService.assign(id, userId, roleId);
        } catch (Exception e) {
            log.warn("assign failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("ASSIGN_COMPLETE");
    }

    @PostMapping(value = "setNickname")
    public ResultDTO<Void> setNickname(HttpServletRequest request, HttpSession session) {
        // TODO: Replace this with join() and invite()
        log.debug("Calling setNickname");

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

        // Members rename themselves; renaming anyone else is member management.
        if (!sessionUserId.equals(userId)
                && !accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        if (sessionUserId.equals(userId) && !accessService.isMember(id, sessionUserId))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.setNickname(id, userId, nickname);
        } catch (Exception e) {
            log.warn("setNickname failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("UPDATE_COMPLETE");
    }

    /**
     * Puts a member in a class, or takes them out of one. Omitting {@code classId} (or sending it
     * empty) unassigns. Works for children and teachers alike — both are rows in T_RELATIONSHIP.
     */
    @PostMapping(value = "setClass")
    public ResultDTO<Void> setClass(HttpServletRequest request, HttpSession session) {
        log.debug("Calling setClass");

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

        Long classId = null;
        String classIdParam = request.getParameter("classId");
        if (classIdParam != null && !classIdParam.isBlank()) {
            try {
                classId = Long.parseLong(classIdParam);
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }
        }

        // Deciding who sits in which class is a decision about members, not about the classes:
        // MANAGE_CLASS creates, renames and deletes the classes themselves. The owner passes
        // either way — getPermissions grants them everything.
        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        // A class from another kindergarten would silently detach the member from this one.
        if (classId != null && !Long.valueOf(id).equals(accessService.getKindergartenOfClass(classId)))
            return ResultDTO.error("INVALID_PARAMETER");
        // Only actual members have a row to update; the owner may have none (see getMembers).
        if (accessService.getMembership(id, userId) == null) return ResultDTO.error("NOT_FOUND");

        try {
            kindergartenService.setClass(id, userId, classId);
        } catch (Exception e) {
            log.warn("setClass failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("UPDATE_COMPLETE");
    }

    @PostMapping(value = "remove")
    public ResultDTO<Void> remove(HttpServletRequest request, HttpSession session) {
        log.debug("Calling remove");

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

        // Anyone may leave; removing someone else needs MANAGE_MEMBER. The owner can't be removed
        // at all — transfer the kindergarten first.
        if (!sessionUserId.equals(userId)
                && !accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        if (accessService.isOwner(id, userId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.remove(id, userId);
            log.info("Member {} removed from kindergarten {} by {}", userId, id, sessionUserId);
        } catch (Exception e) {
            log.warn("remove failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("REMOVE_COMPLETE");
    }

    @GetMapping(value = "has")
    public ResultDTO<RelationshipDTO> has(HttpServletRequest request, HttpSession session) {
        log.debug("Calling has");

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

        // Asking about yourself is always fine; asking about someone else means you have to be in
        // the kindergarten too.
        if (!sessionUserId.equals(userId) && !accessService.canView(id, sessionUserId))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.has(id, userId));
        } catch (Exception e) {
            log.warn("has failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "role/list")
    public ResultDTO<List<RoleDTO>> roleList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling roleList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canView(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getRoles(kindergartenId));
        } catch (Exception e) {
            log.warn("roleList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/create")
    public ResultDTO<Void> createRole(HttpServletRequest request, HttpSession session) {
        log.debug("Calling createRole");

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
        String color = request.getParameter("color");

        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.createRole(kindergartenId, name, color);
            log.info("Role created in kindergarten {}: {} by {}", kindergartenId, name, sessionUserId);
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.warn("createRole failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
        return ResultDTO.success("CREATE_COMPLETE");
    }

    @PostMapping(value = "role/permissions")
    public ResultDTO<List<RoleDTO.Permission>> rolePermissions(HttpServletRequest request, HttpSession session) {
        log.debug("Calling rolePermissions");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfRole(id);
        if (kindergartenId == null) return ResultDTO.error("ROLE_NOT_FOUND");
        if (!accessService.canView(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getRolePermissions(id));
        } catch (Exception e) {
            log.warn("rolePermissions failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/rename")
    public ResultDTO<Void> renameRole(HttpServletRequest request, HttpSession session) {
        log.debug("Calling renameRole");

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
        String color = request.getParameter("color");

        Long kindergartenId = accessService.getKindergartenOfRole(id);
        if (kindergartenId == null) return ResultDTO.error("ROLE_NOT_FOUND");
        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.renameRole(id, name, color);
            log.info("Role {} renamed to {} by {}", id, name, sessionUserId);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.warn("renameRole failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/delete")
    public ResultDTO<Void> deleteRole(HttpServletRequest request, HttpSession session) {
        log.debug("Calling deleteRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfRole(id);
        if (kindergartenId == null) return ResultDTO.error("ROLE_NOT_FOUND");
        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.deleteRole(id);
            log.info("Role {} deleted by {}", id, sessionUserId);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.warn("deleteRole failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // Unlike assign(), which overwrites the single legacy role column, these add to / remove from
    // the member's role set — a member may hold several roles at once.
    @PostMapping(value = "role/assign")
    public ResultDTO<Void> assignRole(HttpServletRequest request, HttpSession session) {
        log.debug("Calling assignRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id, roleId;
        try {
            id = Long.parseLong(request.getParameter("id"));
            roleId = Long.parseLong(request.getParameter("roleId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");
        if (!Long.valueOf(id).equals(accessService.getKindergartenOfRole(roleId)))
            return ResultDTO.error("INVALID_PARAMETER");

        try {
            kindergartenService.assignRole(id, userId, roleId);
            log.info("Role {} assigned to {} in kindergarten {} by {}", roleId, userId, id, sessionUserId);
            return ResultDTO.success("ASSIGN_COMPLETE");
        } catch (Exception e) {
            log.warn("assignRole failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/unassign")
    public ResultDTO<Void> unassignRole(HttpServletRequest request, HttpSession session) {
        log.debug("Calling unassignRole");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id, roleId;
        try {
            id = Long.parseLong(request.getParameter("id"));
            roleId = Long.parseLong(request.getParameter("roleId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!accessService.hasPermission(id, sessionUserId, RoleDTO.Permission.MANAGE_MEMBER))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.unassignRole(id, userId, roleId);
            log.info("Role {} unassigned from {} in kindergarten {} by {}", roleId, userId, id, sessionUserId);
            return ResultDTO.success("UNASSIGN_COMPLETE");
        } catch (Exception e) {
            log.warn("unassignRole failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/permission/add")
    public ResultDTO<Void> addRolePermission(HttpServletRequest request, HttpSession session) {
        log.debug("Calling addRolePermission");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // Deciding what a role may do is the owner's call alone: letting MANAGE_MEMBER holders
        // edit permissions would let them grant themselves everything else.
        Long kindergartenId = accessService.getKindergartenOfRole(id);
        if (kindergartenId == null) return ResultDTO.error("ROLE_NOT_FOUND");
        if (!accessService.isOwner(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            RoleDTO.Permission permission = RoleDTO.Permission.valueOf(request.getParameter("permission"));
            kindergartenService.addRolePermission(id, permission);
            log.info("Permission {} added to role {} by {}", permission, id, sessionUserId);
            return ResultDTO.success("ADD_COMPLETE");
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.warn("addRolePermission failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "role/permission/remove")
    public ResultDTO<Void> removeRolePermission(HttpServletRequest request, HttpSession session) {
        log.debug("Calling removeRolePermission");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfRole(id);
        if (kindergartenId == null) return ResultDTO.error("ROLE_NOT_FOUND");
        if (!accessService.isOwner(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            RoleDTO.Permission permission = RoleDTO.Permission.valueOf(request.getParameter("permission"));
            kindergartenService.removeRolePermission(id, permission);
            log.info("Permission {} removed from role {} by {}", permission, id, sessionUserId);
            return ResultDTO.success("REMOVE_COMPLETE");
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.warn("removeRolePermission failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "notice/list")
    public ResultDTO<List<NoticeDTO>> noticeList(HttpServletRequest request, HttpSession session) throws Exception {
        log.debug("Calling noticeList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canView(kindergartenId, userId)) return ResultDTO.error("INVALID_ACCESS");

        return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getNotices(kindergartenId));
    }

    @GetMapping(value = "notice/info")
    public ResultDTO<NoticeDTO> noticeInfo(HttpServletRequest request, HttpSession session) throws Exception {
        log.debug("Calling noticeInfo");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            NoticeDTO rDTO = kindergartenService.getNoticeInfo(id);

            if (rDTO == null) return ResultDTO.error("NOTICE_NOT_FOUND");
            if (!accessService.canView(rDTO.getKindergartenId(), userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
    }

    @PostMapping(value = "notice/create")
    public ResultDTO<Void> createNotice(HttpServletRequest request, HttpSession session) {
        log.debug("Calling createNotice");

        try {
            String userId = (String) session.getAttribute("SESSION_USER_ID");
            if (userId == null) return ResultDTO.error("INVALID_ACCESS");

            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            if (!accessService.hasPermission(kindergartenId, userId, RoleDTO.Permission.MANAGE_NOTICE))
                return ResultDTO.error("INVALID_ACCESS");

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            pDTO.setAuthor(userId);
            pDTO.setTitle(request.getParameter("title"));
            pDTO.setPinned(Boolean.parseBoolean(request.getParameter("pinned")));
            pDTO.setBannerEnabled(Boolean.parseBoolean(request.getParameter("bannerEnabled")));
            pDTO.setContent(request.getParameter("content"));
            log.debug("Notice for kindergarten {} by {}: {} chars", kindergartenId, userId,
                    pDTO.getContent() == null ? 0 : pDTO.getContent().length());

            kindergartenService.createNotice(pDTO);

            return ResultDTO.success("REGISTER_COMPLETE");
        } catch (Exception e) {
            log.warn("createNotice failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "notice/edit")
    public ResultDTO<Void> editNotice(HttpServletRequest request, HttpSession session) {
        log.debug("Calling editNotice");

        try {
            String userId = (String) session.getAttribute("SESSION_USER_ID");
            if (userId == null) return ResultDTO.error("INVALID_ACCESS");

            long kindergartenId;
            try {
                kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            // The update is keyed on (kindergartenId, num), so checking the kindergarten the
            // caller named is enough — they can't reach a notice outside it.
            if (!accessService.hasPermission(kindergartenId, userId, RoleDTO.Permission.MANAGE_NOTICE))
                return ResultDTO.error("INVALID_ACCESS");

            NoticeDTO pDTO = new NoticeDTO();
            pDTO.setKindergartenId(kindergartenId);
            try {
                pDTO.setNum(Integer.parseInt(request.getParameter("num")));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }
            pDTO.setTitle(request.getParameter("title"));
            pDTO.setPinned(Boolean.parseBoolean(request.getParameter("pinned")));
            pDTO.setBannerEnabled(Boolean.parseBoolean(request.getParameter("bannerEnabled")));
            pDTO.setContent(request.getParameter("content"));
            log.debug("Notice {} in kindergarten {}: {} chars", pDTO.getNum(), kindergartenId,
                    pDTO.getContent() == null ? 0 : pDTO.getContent().length());

            kindergartenService.updateNotice(pDTO);

            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.warn("editNotice failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "notice/delete")
    public ResultDTO<Void> deleteNotice(HttpServletRequest request, HttpSession session) {
        log.debug("Calling deleteNotice");

        try {
            String userId = (String) session.getAttribute("SESSION_USER_ID");
            if (userId == null) return ResultDTO.error("INVALID_ACCESS");

            long id;
            try {
                id = Long.parseLong(request.getParameter("id"));
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            Long kindergartenId = accessService.getKindergartenOfNotice(id);
            if (kindergartenId == null) return ResultDTO.error("NOTICE_NOT_FOUND");
            if (!accessService.hasPermission(kindergartenId, userId, RoleDTO.Permission.MANAGE_NOTICE))
                return ResultDTO.error("INVALID_ACCESS");

            try {
                kindergartenService.deleteNotice(id);
            } catch (NumberFormatException e) {
                return ResultDTO.error("INVALID_PARAMETER");
            }

            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.warn("deleteNotice failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "schedule/list")
    public ResultDTO<List<ScheduleDTO>> scheduleList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling scheduleList");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canView(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getSchedules(kindergartenId));
        } catch (Exception e) {
            log.warn("scheduleList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "schedule/info")
    public ResultDTO<ScheduleDTO> getScheduleInfo(HttpServletRequest request, HttpSession session) {
        log.debug("Calling getSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfSchedule(id);
        if (kindergartenId == null) return ResultDTO.error("SCHEDULE_NOT_FOUND");
        if (!accessService.canView(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getScheduleInfo(id));
        } catch (Exception e) {
            log.warn("getScheduleInfo failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/create")
    public ResultDTO<Void> createSchedule(HttpServletRequest request, HttpSession session) {
        log.debug("Calling createSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_SCHEDULE))
            return ResultDTO.error("INVALID_ACCESS");

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
        if (pDTO.getClassId() != null
                && !Long.valueOf(kindergartenId).equals(accessService.getKindergartenOfClass(pDTO.getClassId())))
            return ResultDTO.error("INVALID_PARAMETER");

        try {
            kindergartenService.createSchedule(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.warn("createSchedule failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/edit")
    public ResultDTO<Void> editSchedule(HttpServletRequest request, HttpSession session) {
        log.debug("Calling editSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfSchedule(id);
        if (kindergartenId == null) return ResultDTO.error("SCHEDULE_NOT_FOUND");
        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_SCHEDULE))
            return ResultDTO.error("INVALID_ACCESS");

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
        if (pDTO.getClassId() != null
                && !kindergartenId.equals(accessService.getKindergartenOfClass(pDTO.getClassId())))
            return ResultDTO.error("INVALID_PARAMETER");

        try {
            kindergartenService.updateSchedule(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.warn("editSchedule failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "schedule/delete")
    public ResultDTO<Void> deleteSchedule(HttpServletRequest request, HttpSession session) {
        log.debug("Calling deleteSchedule");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long kindergartenId = accessService.getKindergartenOfSchedule(id);
        if (kindergartenId == null) return ResultDTO.error("SCHEDULE_NOT_FOUND");
        if (!accessService.hasPermission(kindergartenId, sessionUserId, RoleDTO.Permission.MANAGE_SCHEDULE))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            kindergartenService.deleteSchedule(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.warn("deleteSchedule failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
