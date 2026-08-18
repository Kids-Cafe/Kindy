package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.InviteDTO;
import org.kidscafe.kindy.dto.ParentNoteDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IKindergartenService;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RequestMapping(value = "/api/user")
@RequiredArgsConstructor
@RestController
public class UserController {
    private final IUserService userService;
    private final IKindergartenService kindergartenService;
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

    @GetMapping(value = "getVerificationEmail")
    public ResultDTO<UserDTO> getVerificationEmail(HttpServletRequest request, HttpSession session) {
        log.info("Calling getVerificationEmail");

        String email = request.getParameter("email");
        if (email == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            UserDTO user = userService.getEmailExists(email);
            if (user == null) return ResultDTO.error("UNKNOWN_ERROR");
            if (user.getExists()) return ResultDTO.error("EMAIL_EXISTS");
            String code = userService.sendVerificationCode(email);
            log.info("code: {}", code);
            session.setAttribute("SESSION_VERIFICATION_CODE", code);
            return ResultDTO.success("SENT_CODE");
        } catch(Exception e) {
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "verifyEmail")
    public ResultDTO<UserDTO> verifyEmail(HttpServletRequest request, HttpSession session) {
        log.info("Calling verifyEmail");

        String email = request.getParameter("email");
        if (email == null) return ResultDTO.error("MISSING_PARAMETER");
        String code = request.getParameter("code");
        if (code == null) return ResultDTO.error("MISSING_PARAMETER");

        if (code.equals(session.getAttribute("SESSION_VERIFICATION_CODE"))) {
            session.removeAttribute("SESSION_VERIFICATION_CODE");
            session.setAttribute("SESSION_VERIFIED_EMAIL", email);
            return ResultDTO.success("VERIFICATION_COMPLETE");
        } else {
            return ResultDTO.error("INVALID_PARAMETER");
        }
    }

    @PostMapping(value = "create")
    public ResultDTO<Void> create(HttpServletRequest request, HttpSession session) {
        log.info("Calling create");

        UserDTO pDTO = new UserDTO();
        pDTO.setId(request.getParameter("id"));
        pDTO.setName(request.getParameter("name"));
        String password = request.getParameter("password");
        if (password == null || password.length() < 8) return ResultDTO.error("INVALID_PARAMETER");
        byte[] salt = encryptUtil.getSecureSalt();
        pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
        pDTO.setPasswordSalt(salt);
        pDTO.setEmail(request.getParameter("email"));
        if (!pDTO.getEmail().equals(session.getAttribute("SESSION_VERIFIED_EMAIL"))) return ResultDTO.error("EMAIL_NOT_VERIFIED");
        pDTO.setPhone(request.getParameter("phone"));

        String accountType = request.getParameter("accountType");
        try {
            pDTO.setAccountType(accountType == null ? UserDTO.AccountType.ADULT : UserDTO.AccountType.valueOf(accountType));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        pDTO.setBirthDate(request.getParameter("birthDate"));
        String gender = request.getParameter("gender");
        try {
            if (gender != null) pDTO.setGender(UserDTO.Gender.valueOf(gender));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        pDTO.setGuardianName(request.getParameter("guardianName"));
        pDTO.setGuardianPhone(request.getParameter("guardianPhone"));

        try {
            if (pDTO.getAccountType() != UserDTO.AccountType.CHILD) {
                pDTO.setAddress(encryptUtil.encAES128CBC(request.getParameter("address")));
                pDTO.setAddressDetail(encryptUtil.encAES128CBC(request.getParameter("addressDetail")));
                pDTO.setPostcode(encryptUtil.encAES128CBC(request.getParameter("postcode")));
            }

            log.info("User Register Attempt: {}", pDTO.getId());

            int res = userService.create(pDTO);

            log.info("User Register Result: {}", res);

            if (res == 1) {
                return ResultDTO.success("SIGNUP_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (NullPointerException e) {
            return ResultDTO.error("MISSING_PARAMETER");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (DuplicateKeyException e) {
            log.info("Duplicate ID: {}", pDTO.getId());
            return ResultDTO.error("DUPLICATE_ID");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "login")
    public ResultDTO<UserDTO> login(HttpServletRequest request, HttpSession session) {
        log.info("Calling login");

        String id = request.getParameter("id");
        if (id == null) return ResultDTO.error("MISSING_PARAMETER");
        String password = request.getParameter("password");
        if (password == null) return ResultDTO.error("MISSING_PARAMETER");

        log.info("Login Attempt: {}", id);

        try {
            UserDTO rDTO = userService.login(id, password);
            if (rDTO == null) return ResultDTO.error("SIGNIN_NO_MATCHES");

            session.invalidate();
            session = request.getSession(true);
            session.setMaxInactiveInterval(3600);
            session.setAttribute("SESSION_USER_ID", rDTO.getId());
            session.setAttribute("SESSION_USER_NAME", rDTO.getName());

            return ResultDTO.success("SIGNIN_COMPLETE", rDTO);
        } catch (Exception e) {
            log.info(e.getMessage());
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
                rDTO.getPhone(),
                encryptUtil.decAES128CBC(rDTO.getAddress()),
                encryptUtil.decAES128CBC(rDTO.getAddressDetail()),
                encryptUtil.decAES128CBC(rDTO.getPostcode()),
                rDTO.getAccountType(),
                rDTO.getBirthDate(),
                rDTO.getGender(),
                rDTO.getGuardianName(),
                rDTO.getGuardianPhone(),
                rDTO.getOnboardingCompleted(),
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
            userService.update(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "onboarding/complete")
    public ResultDTO<Void> completeOnboarding(HttpSession session) throws Exception {
        log.info("Calling completeOnboarding");

        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            userService.completeOnboarding(id);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
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

        userService.updatePassword(newPassword, password);

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

    @GetMapping(value = "diary/list")
    public ResultDTO<List<DiaryDTO>> diaryList(HttpSession session) {
        log.info("Calling diaryList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getDiaries(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "diary/info")
    public ResultDTO<DiaryDTO> diaryInfo(HttpServletRequest request, HttpSession session) {
        log.info("Calling diaryInfo");

        String userId = request.getParameter("userId");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");
        String date = request.getParameter("date");
        if (date == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getDiaryInfo(userId, date));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "diary/create")
    public ResultDTO<Void> createDiary(HttpServletRequest request, HttpSession session) {
        log.info("Calling createDiary");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setUserId(userId);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (!this.applyDiaryFields(pDTO, request)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            userService.createDiary(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "diary/modify")
    public ResultDTO<Void> modifyDiary(HttpServletRequest request, HttpSession session) {
        log.info("Calling modifyDiary");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setUserId(userId);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (!this.applyDiaryFields(pDTO, request)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            userService.updateDiary(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "diary/delete")
    public ResultDTO<Void> deleteDiary(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteDiary");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");
        String date = request.getParameter("date");
        if (date == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            userService.deleteDiary(userId, date);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * Copies the optional diary body fields off the request. `tags` is a comma-separated list;
     * leaving it out keeps whatever tags the entry already has. Returns false on a bad mood value.
     */
    private boolean applyDiaryFields(DiaryDTO pDTO, HttpServletRequest request) {
        pDTO.setTitle(request.getParameter("title"));
        pDTO.setSummary(request.getParameter("summary"));
        pDTO.setText(request.getParameter("text"));

        String mood = request.getParameter("mood");
        if (mood != null && !mood.isBlank()) {
            try {
                pDTO.setMood(DiaryDTO.Mood.valueOf(mood));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        String tags = request.getParameter("tags");
        if (tags != null) {
            pDTO.setTags(Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toList());
        }

        return true;
    }

    // Invite-flow lookup by partial login id or name. Public fields only, capped server-side.
    @GetMapping(value = "search")
    public ResultDTO<List<UserDTO.PlainUserDTO>> search(HttpServletRequest request, HttpSession session) {
        log.info("Calling search");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String q = request.getParameter("q");
        if (q == null) return ResultDTO.error("MISSING_PARAMETER");
        q = q.trim();
        if (q.length() < 2) return ResultDTO.error("INVALID_PARAMETER");

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.searchUsers(q));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "note/list")
    public ResultDTO<List<ParentNoteDTO>> noteList(HttpServletRequest request, HttpSession session) {
        log.info("Calling noteList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String childId = request.getParameter("childId");
        if (childId == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: Access check — only the child's parents and their teachers should see these

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getParentNotes(childId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "note/create")
    public ResultDTO<Void> createNote(HttpServletRequest request, HttpSession session) {
        log.info("Calling createNote");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        ParentNoteDTO pDTO = new ParentNoteDTO();
        pDTO.setChildId(request.getParameter("childId"));
        if (pDTO.getChildId() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setContent(request.getParameter("content"));
        if (pDTO.getContent() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setAuthor(userId);

        // TODO: Access check

        try {
            userService.createParentNote(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "note/delete")
    public ResultDTO<Void> deleteNote(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteNote");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            userService.deleteParentNote(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "note/comment/list")
    public ResultDTO<List<ParentNoteDTO.CommentDTO>> noteCommentList(HttpServletRequest request, HttpSession session) {
        log.info("Calling noteCommentList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long noteId;
        try {
            noteId = Long.parseLong(request.getParameter("noteId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getParentNoteComments(noteId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "note/comment/create")
    public ResultDTO<Void> createNoteComment(HttpServletRequest request, HttpSession session) {
        log.info("Calling createNoteComment");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long noteId;
        try {
            noteId = Long.parseLong(request.getParameter("noteId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        ParentNoteDTO.CommentDTO pDTO = new ParentNoteDTO.CommentDTO();
        pDTO.setNoteId(noteId);
        pDTO.setAuthor(userId);
        pDTO.setContent(request.getParameter("content"));
        if (pDTO.getContent() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            userService.createParentNoteComment(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "note/comment/delete")
    public ResultDTO<Void> deleteNoteComment(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteNoteComment");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            userService.deleteParentNoteComment(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // One row per (child, category); `data` is the category's JSON blob, stored verbatim.
    @GetMapping(value = "report/list")
    public ResultDTO<List<ReportDTO>> reportList(HttpServletRequest request, HttpSession session) {
        log.info("Calling reportList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String childId = request.getParameter("childId");
        if (childId == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: Access check

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getReports(childId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "report/save")
    public ResultDTO<Void> saveReport(HttpServletRequest request, HttpSession session) {
        log.info("Calling saveReport");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        ReportDTO pDTO = new ReportDTO();
        pDTO.setChildId(request.getParameter("childId"));
        if (pDTO.getChildId() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setData(request.getParameter("data"));
        if (pDTO.getData() == null) return ResultDTO.error("MISSING_PARAMETER");

        // TODO: Access check

        try {
            pDTO.setCategory(ReportDTO.Category.valueOf(request.getParameter("category")));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            userService.saveReport(pDTO);
            return ResultDTO.success("SAVE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "invite/list")
    public ResultDTO<List<InviteDTO>> inviteList(HttpSession session) {
        log.info("Calling inviteList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getUserInvites(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "family/list")
    public ResultDTO<List<FamilyDTO>> familyList(HttpSession session) {
        log.info("Calling familyList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getFamilies(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "family/add")
    public ResultDTO<Void> addFamily(HttpServletRequest request, HttpSession session) {
        log.info("Calling addFamily");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String parent = request.getParameter("parent");
        if (parent == null) return ResultDTO.error("MISSING_PARAMETER");
        String child = request.getParameter("child");
        if (child == null) return ResultDTO.error("MISSING_PARAMETER");
        if (!parent.equals(userId) && !child.equals(userId)) return ResultDTO.error("INVALID_ACCESS");
        if (parent.equals(child)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            userService.addFamily(parent, child);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "family/remove")
    public ResultDTO<Void> removeFamily(HttpServletRequest request, HttpSession session) {
        log.info("Calling removeFamily");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String parent = request.getParameter("parent");
        if (parent == null) return ResultDTO.error("MISSING_PARAMETER");
        String child = request.getParameter("child");
        if (child == null) return ResultDTO.error("MISSING_PARAMETER");
        if (!parent.equals(userId) && !child.equals(userId)) return ResultDTO.error("INVALID_ACCESS");
        if (parent.equals(child)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            userService.removeFamily(parent, child);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}