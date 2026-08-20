package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.FamilyInviteDTO;
import org.kidscafe.kindy.dto.InviteDTO;
import org.kidscafe.kindy.dto.ParentNoteDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IAccessService;
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
    private final IAccessService accessService;
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

    /** Carries a ResultDTO error code out of a parameter helper. */
    private static class ParameterException extends Exception {
        private final String code;

        ParameterException(String code) {
            super(code);
            this.code = code;
        }
    }

    /**
     * Reads signup parameters into a UserDTO. The per-account-type rules live here and only here,
     * so user/create and user/child/create cannot drift apart: a child arrives with no email and
     * no address but must have a birth date, an adult must have verified their address earlier in
     * the same session.
     * <p>
     * {@code forcedType} pins the account type instead of reading it from the request. That is how
     * user/child/create stays safe without touching the verification rule: it passes CHILD, so the
     * accountType parameter is ignored and control never reaches the verified-email branch at all.
     * Nothing here lets an ADULT skip that branch — the only new thing is a caller that could never
     * have needed it.
     */
    private UserDTO parseNewUser(HttpServletRequest request, HttpSession session,
                                 UserDTO.AccountType forcedType) throws Exception {
        UserDTO pDTO = new UserDTO();
        pDTO.setId(request.getParameter("id"));
        pDTO.setName(request.getParameter("name"));
        String password = request.getParameter("password");
        if (password == null || password.length() < 8) throw new ParameterException("INVALID_PARAMETER");
        byte[] salt = encryptUtil.getSecureSalt();
        pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
        pDTO.setPasswordSalt(salt);
        pDTO.setPhone(request.getParameter("phone"));

        if (forcedType != null) {
            pDTO.setAccountType(forcedType);
        } else {
            String accountType = request.getParameter("accountType");
            pDTO.setAccountType(accountType == null
                    ? UserDTO.AccountType.ADULT
                    : UserDTO.AccountType.valueOf(accountType));
        }

        boolean isChild = pDTO.getAccountType() == UserDTO.AccountType.CHILD;

        // A child signs up without an email of their own, so there is nothing to verify and the
        // column stays NULL. Adults must have verified the address earlier in this same session.
        if (!isChild) {
            String email = request.getParameter("email");
            if (email == null) throw new ParameterException("MISSING_PARAMETER");
            if (!email.equals(session.getAttribute("SESSION_VERIFIED_EMAIL")))
                throw new ParameterException("EMAIL_NOT_VERIFIED");
            pDTO.setEmail(email);
        }

        pDTO.setBirthDate(request.getParameter("birthDate"));
        String gender = request.getParameter("gender");
        if (gender != null) pDTO.setGender(UserDTO.Gender.valueOf(gender));
        pDTO.setGuardianName(request.getParameter("guardianName"));
        pDTO.setGuardianPhone(request.getParameter("guardianPhone"));

        if (!isChild) {
            pDTO.setAddress(encryptUtil.encAES128CBC(request.getParameter("address")));
            pDTO.setAddressDetail(encryptUtil.encAES128CBC(request.getParameter("addressDetail")));
            pDTO.setPostcode(encryptUtil.encAES128CBC(request.getParameter("postcode")));
        }

        return pDTO;
    }

    @PostMapping(value = "create")
    public ResultDTO<Void> create(HttpServletRequest request, HttpSession session) {
        log.info("Calling create");

        String attemptedId = request.getParameter("id");
        try {
            UserDTO pDTO = this.parseNewUser(request, session, null);

            log.info("User Register Attempt: {}", pDTO.getId());

            int res = userService.create(pDTO);

            log.info("User Register Result: {}", res);

            if (res == 1) {
                return ResultDTO.success("SIGNUP_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (ParameterException e) {
            return ResultDTO.error(e.code);
        } catch (NullPointerException e) {
            return ResultDTO.error("MISSING_PARAMETER");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (DuplicateKeyException e) {
            log.info("Duplicate ID: {}", attemptedId);
            return ResultDTO.error("DUPLICATE_ID");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * Registers a child account on behalf of the signed-in adult and links the two in the same
     * transaction. This is the path that needs no consent step: whoever creates the account is by
     * definition one party to the relationship, so there is no second party to ask.
     * <p>
     * The account type is forced to CHILD rather than read from the request, so this cannot be
     * used to create an adult and skip email verification. A child may not call it — an account
     * without a guardian of its own is not a place to hang more accounts off.
     */
    @PostMapping(value = "child/create")
    public ResultDTO<Void> createChild(HttpServletRequest request, HttpSession session) {
        log.info("Calling createChild");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String attemptedId = request.getParameter("id");
        try {
            UserDTO caller = userService.getInfo(userId);
            if (caller == null || caller.getAccountType() != UserDTO.AccountType.ADULT)
                return ResultDTO.error("INVALID_ACCESS");

            UserDTO pDTO = this.parseNewUser(request, session, UserDTO.AccountType.CHILD);

            log.info("Child Register Attempt: {} by {}", pDTO.getId(), userId);

            int res = userService.createChildFor(userId, pDTO);
            if (res != 1) return ResultDTO.error("UNKNOWN_ERROR");

            return ResultDTO.success("CREATE_COMPLETE");
        } catch (ParameterException e) {
            return ResultDTO.error(e.code);
        } catch (NullPointerException e) {
            return ResultDTO.error("MISSING_PARAMETER");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (DuplicateKeyException e) {
            log.info("Duplicate ID: {}", attemptedId);
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

    /**
     * The full profile of the caller, or — with {@code userId} — of a child they are entitled to
     * see. Omitting the parameter behaves exactly as before, which matters because the login flow
     * calls this immediately after authenticating.
     * <p>
     * The gate is {@link IAccessService#canViewChild}, the same one on note/list and report/list,
     * so a parent and the child's teachers see the profile and nobody else does. It is deliberately
     * not the looser isMember: the response carries the target's decrypted address, and while a
     * child's address columns are NULL by check constraint, canViewChild is what keeps this from
     * becoming a way to read an adult colleague's home address.
     */
    @GetMapping(value = "info")
    public ResultDTO<UserDTO.PlainUserDTO> info(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling info");
        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");

        String target = request.getParameter("userId");
        if (target != null && !target.equals(id) && !accessService.canViewChild(id, target))
            return ResultDTO.error("INVALID_ACCESS");

        UserDTO rDTO = userService.getInfo(target == null ? id : target);
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

    /**
     * Updates the parts of the profile a member may change about themselves.
     *
     * My-page edits one section at a time, so every parameter is optional and only the ones sent
     * are written — posting a phone number must not wipe the stored address. The address still
     * travels as a set: a road address without its postcode is not a usable address.
     * <p>
     * The real name is not editable here on purpose. What a member is called inside a kindergarten
     * is their nickname, which lives on the relationship — see kindergarten/setNickname. A child's
     * name is a different matter and has its own endpoint: see child/update.
     */
    @PostMapping(value = "update")
    public ResultDTO<Void> update(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling update");

        String id = (String) session.getAttribute("SESSION_USER_ID");
        if (id == null) return ResultDTO.error("INVALID_ACCESS");
        UserDTO pDTO = UserDTO.fromId(id);

        String phone = request.getParameter("phone");
        if (phone != null) pDTO.setPhone(phone);

        String address = request.getParameter("address");
        String postcode = request.getParameter("postcode");
        if (address != null || postcode != null) {
            if (address == null || postcode == null) return ResultDTO.error("MISSING_PARAMETER");
            pDTO.setAddress(encryptUtil.encAES128CBC(address));
            pDTO.setPostcode(encryptUtil.encAES128CBC(postcode));
            if (pDTO.getAddress() == null || pDTO.getPostcode() == null) return ResultDTO.error("MISSING_PARAMETER");
            // The detail line belongs to the address that just replaced it, so callers should send
            // it alongside — an empty string clears it. Omitting it leaves the stored one alone.
            pDTO.setAddressDetail(encryptUtil.encAES128CBC(request.getParameter("addressDetail")));
        }

        if (pDTO.getPhone() == null && pDTO.getAddress() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            userService.update(pDTO);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * A parent editing their child's profile. Like update, every field is optional and only what
     * arrives is written.
     * <p>
     * The gate is {@link IAccessService#isParentOf}, deliberately narrower than the canManageChild
     * used by note/create and report/save. Those record what a teacher observed about a child and
     * belong to the teacher's job; a child's legal name, birth date and guardian's phone number
     * are the family's own record, and a teacher having write access to them is a different thing
     * entirely.
     * <p>
     * Address and email are absent because the T_USER check constraints forbid a child from having
     * either. The password is absent because changing it is a different act with its own flow.
     */
    @PostMapping(value = "child/update")
    public ResultDTO<Void> updateChild(HttpServletRequest request, HttpSession session) {
        log.info("Calling updateChild");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String childId = request.getParameter("childId");
        if (childId == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!accessService.isParentOf(userId, childId)) return ResultDTO.error("INVALID_ACCESS");

        UserDTO pDTO = UserDTO.fromId(childId);

        // Blank is not a value for the columns that are NOT NULL, and for the optional guardian
        // fields it reads as "leave it alone" rather than "clear it" — see the mapper comment.
        String name = blankToNull(request.getParameter("name"));
        String phone = blankToNull(request.getParameter("phone"));
        String birthDate = blankToNull(request.getParameter("birthDate"));
        String gender = blankToNull(request.getParameter("gender"));

        if (birthDate != null && !birthDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            return ResultDTO.error("INVALID_PARAMETER");

        pDTO.setName(name);
        pDTO.setPhone(phone);
        pDTO.setBirthDate(birthDate);
        try {
            if (gender != null) pDTO.setGender(UserDTO.Gender.valueOf(gender));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }
        pDTO.setGuardianName(blankToNull(request.getParameter("guardianName")));
        pDTO.setGuardianPhone(blankToNull(request.getParameter("guardianPhone")));

        if (pDTO.getName() == null && pDTO.getPhone() == null && pDTO.getBirthDate() == null
                && pDTO.getGender() == null && pDTO.getGuardianName() == null
                && pDTO.getGuardianPhone() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            UserDTO target = userService.getInfo(childId);
            if (target == null) return ResultDTO.error("USER_NOT_FOUND");
            // A family row does not itself prove the other end is a child account, so check.
            if (target.getAccountType() != UserDTO.AccountType.CHILD) return ResultDTO.error("INVALID_PARAMETER");

            if (userService.updateChildInfo(pDTO) == 0) return ResultDTO.error("USER_NOT_FOUND");
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        // Defaults to the caller's own diary; reading someone else's needs the same standing as
        // their other child records.
        String userId = request.getParameter("userId");
        if (userId == null) userId = sessionUserId;
        String date = request.getParameter("date");
        if (date == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!accessService.canViewChild(sessionUserId, userId)) return ResultDTO.error("INVALID_ACCESS");

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

        if (!accessService.canViewChild(userId, childId)) return ResultDTO.error("INVALID_ACCESS");

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

        // A note is written *about* a child, so the child themself can't be the one writing it.
        if (!accessService.canManageChild(userId, pDTO.getChildId())) return ResultDTO.error("INVALID_ACCESS");

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
            ParentNoteDTO note = userService.getParentNote(id);
            if (note == null) return ResultDTO.error("NOT_FOUND");
            // The author, or anyone else who could have written it about that child.
            if (!userId.equals(note.getAuthor()) && !accessService.canManageChild(userId, note.getChildId()))
                return ResultDTO.error("INVALID_ACCESS");

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

        if (!this.canAccessNote(noteId, userId)) return ResultDTO.error("INVALID_ACCESS");

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

        if (!this.canAccessNote(noteId, userId)) return ResultDTO.error("INVALID_ACCESS");

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
            ParentNoteDTO.CommentDTO comment = userService.getParentNoteComment(id);
            if (comment == null) return ResultDTO.error("NOT_FOUND");
            // Own comment, or moderation by whoever manages the child the note is about.
            if (!userId.equals(comment.getAuthor())) {
                String childId = accessService.getChildOfNote(comment.getNoteId());
                if (childId == null || !accessService.canManageChild(userId, childId))
                    return ResultDTO.error("INVALID_ACCESS");
            }

            userService.deleteParentNoteComment(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** Comments live under a note, so they inherit that note's child-level read access. */
    private boolean canAccessNote(long noteId, String userId) {
        String childId = accessService.getChildOfNote(noteId);
        return childId != null && accessService.canViewChild(userId, childId);
    }

    // One row per (child, category); `data` is the category's JSON blob, stored verbatim.
    @GetMapping(value = "report/list")
    public ResultDTO<List<ReportDTO>> reportList(HttpServletRequest request, HttpSession session) {
        log.info("Calling reportList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String childId = request.getParameter("childId");
        if (childId == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!accessService.canViewChild(userId, childId)) return ResultDTO.error("INVALID_ACCESS");

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

        // A report is written about a child by their teacher or parent, never by the child.
        if (!accessService.canManageChild(userId, pDTO.getChildId())) return ResultDTO.error("INVALID_ACCESS");

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

    /**
     * Retired. This used to create the T_FAMILY row on one side's say-so: the only check was that
     * the caller was either the parent or the child, so anyone could name an arbitrary account as
     * their child and, because being a parent is what {@link IAccessService#canManageChild} keys
     * on, immediately read and write that child's diary, notes and reports.
     * <p>
     * The row is now only ever created by an accepted request — family/invite then
     * family/invite/accept, answered by someone other than whoever asked. Left in place rather
     * than deleted so an older client gets a code it can explain instead of a 404.
     */
    @PostMapping(value = "family/add")
    public ResultDTO<Void> addFamily() {
        log.info("Calling addFamily");
        return ResultDTO.error("NOT_AVAILABLE");
    }

    /** Pending link requests this account has standing in, each flagged with whether it may answer. */
    @GetMapping(value = "family/invite/list")
    public ResultDTO<List<FamilyInviteDTO>> familyInviteList(HttpSession session) {
        log.info("Calling familyInviteList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", userService.getFamilyInvites(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * Asks for a parent↔child link. Three people can start one: the adult who would become the
     * parent, the child themself, or a guardian the child already has proposing a second one.
     * Anyone else has no standing and is turned away here.
     */
    @PostMapping(value = "family/invite")
    public ResultDTO<Void> inviteFamily(HttpServletRequest request, HttpSession session) {
        log.info("Calling inviteFamily");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        String parent = request.getParameter("parent");
        if (parent == null) return ResultDTO.error("MISSING_PARAMETER");
        String child = request.getParameter("child");
        if (child == null) return ResultDTO.error("MISSING_PARAMETER");

        if (!parent.equals(userId) && !child.equals(userId) && !accessService.isParentOf(userId, child))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            userService.requestFamilyLink(userId, parent, child);
            return ResultDTO.success("INVITE_COMPLETE");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (IllegalStateException e) {
            return ResultDTO.error("ALREADY_LINKED");
        } catch (DuplicateKeyException e) {
            // The pending-only unique key on T_FAMILY_INVITE caught a second live request.
            return ResultDTO.error("DUPLICATE_REQUEST");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "family/invite/accept")
    public ResultDTO<Void> acceptFamilyInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling acceptFamilyInvite");
        return this.respondToFamilyInvite(request, session, "accept");
    }

    @PostMapping(value = "family/invite/reject")
    public ResultDTO<Void> rejectFamilyInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling rejectFamilyInvite");
        return this.respondToFamilyInvite(request, session, "reject");
    }

    @PostMapping(value = "family/invite/cancel")
    public ResultDTO<Void> cancelFamilyInvite(HttpServletRequest request, HttpSession session) {
        log.info("Calling cancelFamilyInvite");
        return this.respondToFamilyInvite(request, session, "cancel");
    }

    /**
     * The three answers differ only in which service call they make and which code they report, so
     * the session guard, id parsing and exception mapping live here once. Mirrors how
     * KindergartenService reports the same two failures for kindergarten invites: no standing is
     * INVALID_ACCESS, already answered is INVALID_PARAMETER.
     */
    private ResultDTO<Void> respondToFamilyInvite(HttpServletRequest request, HttpSession session, String action) {
        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            switch (action) {
                case "accept" -> userService.acceptFamilyLink(id, userId);
                case "reject" -> userService.rejectFamilyLink(id, userId);
                default -> userService.cancelFamilyLink(id, userId);
            }
        } catch (IllegalAccessException e) {
            return ResultDTO.error("INVALID_ACCESS");
        } catch (IllegalStateException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("ALREADY_LINKED");
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }

        return switch (action) {
            case "accept" -> ResultDTO.success("ACCEPT_COMPLETE");
            case "reject" -> ResultDTO.success("REJECT_COMPLETE");
            default -> ResultDTO.success("CANCEL_COMPLETE");
        };
    }

    /**
     * Breaks a parent↔child link. Unlike creating one, either side may do this alone: removing the
     * row only ever takes access away, so there is nothing for the other side to consent to.
     * <p>
     * Removing the last parent is allowed too. It leaves the child with no guardian, which is a
     * state the rest of the system has to cope with anyway — sealing the exit so that a child can
     * never end up unattached would trap them instead.
     */
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