package org.kidscafe.kindy.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.FamilyInviteDTO;
import org.kidscafe.kindy.dto.ParentNoteDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IDiaryMapper;
import org.kidscafe.kindy.mapper.IFamilyInviteMapper;
import org.kidscafe.kindy.mapper.IFamilyMapper;
import org.kidscafe.kindy.mapper.IParentNoteMapper;
import org.kidscafe.kindy.mapper.IReportMapper;
import org.kidscafe.kindy.mapper.IUserMapper;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {
    private final IUserMapper userMapper;
    private final IDiaryMapper diaryMapper;
    private final IFamilyMapper familyMapper;
    private final IFamilyInviteMapper familyInviteMapper;
    private final IParentNoteMapper parentNoteMapper;
    private final IReportMapper reportMapper;
    private final EncryptUtil encryptUtil;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String EMAIL_SENDER;
    private final String EMAIL_VERIFICATION_TITLE = "[Kindy] 인증 메일";
    private final String EMAIL_VERIFICATION_CONTENT = "Kindy 서비스 이용을 위한 인증 메일입니다. 인증 번호: ";

    private static final SecureRandom VERIFICATION_CODE_RANDOM = new SecureRandom();

    @Override
    public UserDTO getIdExists(String id) throws Exception {
        log.info("Calling getIdExists");

        return userMapper.getIdExists(UserDTO.fromId(id));
    }

    @Override
    public UserDTO getEmailExists(String email) throws Exception {
        log.info("Calling getEmailExists");

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(email);

        return userMapper.getEmailExists(pDTO);
    }

    @Override
    public String sendVerificationCode(String email) throws Exception {
        log.info("Calling sendVerificationCode");

        // SecureRandom, not ThreadLocalRandom: this code is a credential. ThreadLocalRandom is a
        // fast statistical generator whose output is predictable from enough observed values, and
        // "enough" is not many when the caller can request codes at will.
        String code = String.format("%06d", VERIFICATION_CODE_RANDOM.nextInt(1000000));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(message, "UTF-8");
        try {
            messageHelper.setTo(email);
            messageHelper.setFrom(EMAIL_SENDER);
            messageHelper.setSubject(EMAIL_VERIFICATION_TITLE);
            messageHelper.setText(EMAIL_VERIFICATION_CONTENT + code, false);
            mailSender.send(message);
            return code;
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return null;
    }

    @Transactional
    @Override
    public int create(UserDTO pDTO) throws Exception {
        log.info("Calling create");

        if (pDTO.getId().length() < 4 || pDTO.getId().length() > 20) throw new IllegalArgumentException();
        if (pDTO.getName() == null) throw new NullPointerException();
        if (pDTO.getPassword() == null) throw new NullPointerException();
        if (pDTO.getPhone() == null) throw new NullPointerException();
        if (pDTO.getAccountType() == UserDTO.AccountType.CHILD) {
            if (pDTO.getBirthDate() == null) throw new NullPointerException();
        } else {
            // Only an adult owns an email address; a child's stays NULL.
            if (pDTO.getEmail() == null) throw new NullPointerException();
            if (pDTO.getAddress() == null) throw new NullPointerException();
            if (pDTO.getAddressDetail() == null) throw new NullPointerException();
            if (pDTO.getPostcode() == null) throw new NullPointerException();
        }

        return userMapper.insertUser(pDTO);
    }

    /**
     * The account and the parent link go in together. If the link fails the account must not
     * survive on its own — an orphaned child account nobody can reach is worse than a failed
     * request, because the id is now taken and the parent cannot retry with it.
     */
    @Transactional
    @Override
    public int createChildFor(String parentId, UserDTO child) throws Exception {
        log.info("Calling createChildFor");

        if (child.getAccountType() != UserDTO.AccountType.CHILD) throw new IllegalArgumentException();

        int result = this.create(child);
        this.addFamily(parentId, child.getId());

        return result;
    }

    @Override
    public UserDTO login(String id, String password) throws Exception {
        log.info("Calling login");

        UserDTO rDTO = userMapper.getLogin(UserDTO.fromId(id));

        if (rDTO == null || rDTO.getPassword() == null) return null;

        if (!Arrays.equals(encryptUtil.encHashSHA256(password, rDTO.getPasswordSalt()), rDTO.getPassword())) return null;

        rDTO.setPassword(null);
        rDTO.setPasswordSalt(null);

        return rDTO;
    }

    @Override
    public UserDTO getInfo(String id) throws Exception {
        log.info("Calling getInfo");

        return userMapper.getInfo(UserDTO.fromId(id));
    }

    @Transactional
    @Override
    public int update(UserDTO pDTO) throws Exception {
        log.info("Calling update");

        // updateInfo builds its SET clause from whichever fields arrived. With none of them the
        // clause is empty and MyBatis emits "UPDATE T_USER SET WHERE ID = ?", which is not valid
        // SQL. The controllers reject that case first; this stops the broken statement from being
        // one refactor away.
        if (pDTO.getPhone() == null && pDTO.getAddress() == null
                && pDTO.getAddressDetail() == null && pDTO.getPostcode() == null) return 0;

        return userMapper.updateInfo(pDTO);
    }

    @Transactional
    @Override
    public int updateChildInfo(UserDTO pDTO) throws Exception {
        log.info("Calling updateChildInfo");

        // Same empty-SET hazard as update(), one statement over.
        if (pDTO.getName() == null && pDTO.getPhone() == null && pDTO.getBirthDate() == null
                && pDTO.getGender() == null && pDTO.getGuardianName() == null
                && pDTO.getGuardianPhone() == null) return 0;

        return userMapper.updateChildInfo(pDTO);
    }

    @Override
    public UserDTO getId(String name, String email) throws Exception {
        log.info("Calling getId");

        UserDTO pDTO = new UserDTO();
        pDTO.setName(name);
        pDTO.setEmail(email);

        return userMapper.getId(pDTO);
    }

    @Override
    public UserDTO getId(String name, String email, String id) throws Exception {
        log.info("Calling getId");

        UserDTO pDTO = UserDTO.fromId(id);
        pDTO.setName(name);
        pDTO.setEmail(email);

        return userMapper.getId(pDTO);
    }

    @Transactional
    @Override
    public int updatePassword(String id, String password) throws Exception {
        log.info("Calling newPassword");

        byte[] salt = encryptUtil.getSecureSalt();
        UserDTO pDTO = UserDTO.fromId(id);
        pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
        pDTO.setPasswordSalt(salt);

        return userMapper.updatePassword(pDTO);
    }

    @Transactional
    @Override
    public int updateEmail(String id, String email, String password) throws Exception {
        log.info("Calling updateEmail");

        UserDTO pDTO = this.login(id, password);
        if (pDTO == null) throw new IllegalArgumentException();

        pDTO.setEmail(email);

        return userMapper.updateEmail(pDTO);
    }

    @Override
    public List<DiaryDTO> getDiaries(String id) throws Exception {
        log.info("Calling getDiaries");

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setUserId(id);

        return diaryMapper.selectList(pDTO);
    }

    @Override
    public DiaryDTO getDiaryInfo(String id, String date) throws Exception {
        log.info("Calling getDiaryInfo");

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setUserId(id);
        pDTO.setDate(date);

        return diaryMapper.select(pDTO);
    }

    @Transactional
    @Override
    public int createDiary(DiaryDTO pDTO) throws Exception {
        log.info("Calling createDiary");

        int result = diaryMapper.insert(pDTO);
        this.replaceDiaryTags(pDTO);

        return result;
    }

    @Transactional
    @Override
    public int updateDiary(DiaryDTO pDTO) throws Exception {
        log.info("Calling updateDiary");

        int result = diaryMapper.update(pDTO);

        // update() is keyed on (userId, date), so the id has to be read back before touching tags.
        if (pDTO.getId() == null) {
            DiaryDTO saved = diaryMapper.select(pDTO);
            if (saved != null) pDTO.setId(saved.getId());
        }
        this.replaceDiaryTags(pDTO);

        return result;
    }

    // A null tag list means "leave the tags alone"; an empty list clears them.
    private void replaceDiaryTags(DiaryDTO pDTO) throws Exception {
        if (pDTO.getId() == null || pDTO.getTags() == null) return;

        diaryMapper.deleteTags(pDTO.getId());
        for (String tag : pDTO.getTags()) {
            if (tag == null || tag.isBlank()) continue;
            diaryMapper.insertTag(pDTO.getId(), tag.trim());
        }
    }

    @Transactional
    @Override
    public int deleteDiary(String id, String date) throws Exception {
        log.info("Calling deleteDiary");

        DiaryDTO pDTO = new DiaryDTO();
        pDTO.setUserId(id);
        pDTO.setDate(date);

        DiaryDTO saved = diaryMapper.select(pDTO);
        if (saved != null && saved.getId() != null) diaryMapper.deleteTags(saved.getId());

        return diaryMapper.delete(pDTO);
    }

    @Override
    public List<UserDTO.PlainUserDTO> searchUsers(String q) throws Exception {
        log.info("Calling searchUsers");

        return userMapper.searchUsers(q).stream()
                .map(u -> new UserDTO.PlainUserDTO(u.getId(), u.getName(), null, null, null, null, null,
                        u.getAccountType(), null, null, null, null, null, null, null, null))
                .toList();
    }

    @Override
    public List<ParentNoteDTO> getParentNotes(String childId) throws Exception {
        log.info("Calling getParentNotes");

        ParentNoteDTO pDTO = new ParentNoteDTO();
        pDTO.setChildId(childId);

        return parentNoteMapper.selectList(pDTO);
    }

    @Override
    public ParentNoteDTO getParentNote(long id) throws Exception {
        log.info("Calling getParentNote");

        ParentNoteDTO pDTO = new ParentNoteDTO();
        pDTO.setId(id);

        return parentNoteMapper.select(pDTO);
    }

    @Transactional
    @Override
    public int createParentNote(ParentNoteDTO pDTO) throws Exception {
        log.info("Calling createParentNote");

        return parentNoteMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int deleteParentNote(long id) throws Exception {
        log.info("Calling deleteParentNote");

        ParentNoteDTO pDTO = new ParentNoteDTO();
        pDTO.setId(id);

        return parentNoteMapper.delete(pDTO);
    }

    @Override
    public List<ParentNoteDTO.CommentDTO> getParentNoteComments(long noteId) throws Exception {
        log.info("Calling getParentNoteComments");

        ParentNoteDTO.CommentDTO pDTO = new ParentNoteDTO.CommentDTO();
        pDTO.setNoteId(noteId);

        return parentNoteMapper.selectCommentList(pDTO);
    }

    @Override
    public ParentNoteDTO.CommentDTO getParentNoteComment(long id) throws Exception {
        log.info("Calling getParentNoteComment");

        ParentNoteDTO.CommentDTO pDTO = new ParentNoteDTO.CommentDTO();
        pDTO.setId(id);

        return parentNoteMapper.selectComment(pDTO);
    }

    @Transactional
    @Override
    public int createParentNoteComment(ParentNoteDTO.CommentDTO pDTO) throws Exception {
        log.info("Calling createParentNoteComment");

        return parentNoteMapper.insertComment(pDTO);
    }

    @Transactional
    @Override
    public int deleteParentNoteComment(long id) throws Exception {
        log.info("Calling deleteParentNoteComment");

        ParentNoteDTO.CommentDTO pDTO = new ParentNoteDTO.CommentDTO();
        pDTO.setId(id);

        return parentNoteMapper.deleteComment(pDTO);
    }

    @Override
    public List<ReportDTO> getReports(String childId) throws Exception {
        log.info("Calling getReports");

        ReportDTO pDTO = new ReportDTO();
        pDTO.setChildId(childId);

        return reportMapper.selectList(pDTO);
    }

    @Override
    public ReportDTO getReportInfo(String childId, ReportDTO.Category category) throws Exception {
        log.info("Calling getReportInfo");

        ReportDTO pDTO = new ReportDTO();
        pDTO.setChildId(childId);
        pDTO.setCategory(category);

        return reportMapper.select(pDTO);
    }

    @Transactional
    @Override
    public int saveReport(ReportDTO pDTO) throws Exception {
        log.info("Calling saveReport");

        return reportMapper.upsert(pDTO);
    }

    @Override
    public List<FamilyDTO> getFamilies(String id) throws Exception {
        log.info("Calling GetFamilies");

        FamilyDTO pDTO = new FamilyDTO();
        pDTO.setParent(id);
        pDTO.setChild(id);

        return familyMapper.selectList(pDTO);
    }

    /**
     * The guardians of one child, with the name and phone number needed to actually reach them.
     * Nothing else is copied over: the caller here is a teacher looking at a pupil, not the
     * guardian looking at their own account, so the address and login details stay out of it.
     */
    @Override
    public List<UserDTO.PlainUserDTO> getGuardians(String childId) throws Exception {
        log.info("Calling getGuardians");

        FamilyDTO query = new FamilyDTO();
        query.setChild(childId);

        List<UserDTO.PlainUserDTO> result = new java.util.ArrayList<>();
        for (FamilyDTO row : familyMapper.selectParents(query)) {
            UserDTO parent = userMapper.getInfo(UserDTO.fromId(row.getParent()));
            // A row whose account has since been deleted is skipped rather than shown as a blank.
            if (parent == null) continue;
            result.add(new UserDTO.PlainUserDTO(parent.getId(), parent.getName(), null, parent.getPhone(),
                    null, null, null, parent.getAccountType(), null, null, null, null, null, null, null, null));
        }
        return result;
    }

    @Transactional
    @Override
    public int addFamily(String parent, String child) throws Exception {
        log.info("Calling addFamily");

        FamilyDTO pDTO = new FamilyDTO();
        pDTO.setParent(parent);
        pDTO.setChild(child);

        return familyMapper.insert(pDTO);
    }

    // ---- Family links by consent -------------------------------------------------------------

    @Override
    public List<FamilyInviteDTO> getFamilyInvites(String userId) throws Exception {
        log.info("Calling getFamilyInvites");

        List<FamilyInviteDTO> invites = familyInviteMapper.getListForUser(userId);
        for (FamilyInviteDTO invite : invites) {
            invite.setCanRespond(this.resolveFamilyApprovers(invite).contains(userId));
        }
        return invites;
    }

    /**
     * Who may accept or reject this request. The asker is never in the set — if they were, this
     * would be the old one-sided family/add wearing a costume.
     * <ul>
     *   <li>An adult claiming to be a parent: the child's existing parents decide, since they are
     *       the ones who would be sharing. If there are none, the request is the child's first and
     *       the child decides.</li>
     *   <li>A child asking an adult: that adult decides, because nobody is made a parent without
     *       agreeing to it.</li>
     *   <li>An existing parent proposing a co-parent: the proposed co-parent decides. Note this is
     *       also the answer when the proposer is the <em>only</em> parent — "existing parents minus
     *       the asker" would be empty, and a child too young to press a button must not be the only
     *       way to add a second guardian.</li>
     * </ul>
     */
    private Set<String> resolveFamilyApprovers(FamilyInviteDTO invite) throws Exception {
        String parent = invite.getParent();
        String child = invite.getChild();
        String requester = invite.getRequesterId();

        FamilyDTO query = new FamilyDTO();
        query.setChild(child);
        Set<String> parents = familyMapper.selectParents(query).stream()
                .map(FamilyDTO::getParent)
                .collect(Collectors.toSet());

        if (!requester.equals(parent)) {
            // Asked for on the parent's behalf — by the child, or by another guardian. Either way
            // the person being signed up as a parent is the one who has to agree.
            return Set.of(parent);
        }

        Set<String> approvers = new HashSet<>(parents);
        approvers.remove(requester);
        if (approvers.isEmpty()) approvers.add(child);
        return approvers;
    }

    @Transactional
    @Override
    public int requestFamilyLink(String requesterId, String parent, String child) throws Exception {
        log.info("Calling requestFamilyLink");

        if (parent.equals(child)) throw new IllegalArgumentException();

        UserDTO parentUser = userMapper.getInfo(UserDTO.fromId(parent));
        UserDTO childUser = userMapper.getInfo(UserDTO.fromId(child));
        if (parentUser == null || childUser == null) throw new IllegalArgumentException();
        // The old family/add let two adults link, which handed one of them write access to the
        // other's notes and reports. This is the only remaining door, so it is shut here.
        if (parentUser.getAccountType() != UserDTO.AccountType.ADULT) throw new IllegalArgumentException();
        if (childUser.getAccountType() != UserDTO.AccountType.CHILD) throw new IllegalArgumentException();

        FamilyDTO existing = new FamilyDTO();
        existing.setParent(parent);
        existing.setChild(child);
        if (familyMapper.select(existing) != null) throw new IllegalStateException("ALREADY_LINKED");

        FamilyInviteDTO pDTO = new FamilyInviteDTO();
        pDTO.setParent(parent);
        pDTO.setChild(child);
        pDTO.setRequesterId(requesterId);

        return familyInviteMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public int acceptFamilyLink(long id, String userId) throws Exception {
        log.info("Calling acceptFamilyLink");

        FamilyInviteDTO invite = this.pendingFamilyInvite(id);
        if (!this.resolveFamilyApprovers(invite).contains(userId)) throw new IllegalAccessException();

        // Mark it answered first. updateStatus only moves rows that are still pending, so if two
        // approvers press accept at once the loser writes nothing and never reaches the insert.
        invite.setStatus(FamilyInviteDTO.Status.ACCEPTED);
        int updated = familyInviteMapper.updateStatus(invite);
        if (updated == 0) throw new IllegalStateException();

        this.addFamily(invite.getParent(), invite.getChild());

        return updated;
    }

    @Transactional
    @Override
    public int rejectFamilyLink(long id, String userId) throws Exception {
        log.info("Calling rejectFamilyLink");

        FamilyInviteDTO invite = this.pendingFamilyInvite(id);
        if (!this.resolveFamilyApprovers(invite).contains(userId)) throw new IllegalAccessException();

        invite.setStatus(FamilyInviteDTO.Status.REJECTED);
        return familyInviteMapper.updateStatus(invite);
    }

    @Transactional
    @Override
    public int cancelFamilyLink(long id, String userId) throws Exception {
        log.info("Calling cancelFamilyLink");

        FamilyInviteDTO invite = this.pendingFamilyInvite(id);
        if (!userId.equals(invite.getRequesterId())) throw new IllegalAccessException();

        invite.setStatus(FamilyInviteDTO.Status.CANCELED);
        return familyInviteMapper.updateStatus(invite);
    }

    private FamilyInviteDTO pendingFamilyInvite(long id) throws Exception {
        FamilyInviteDTO invite = familyInviteMapper.getInfo(FamilyInviteDTO.fromId(id));
        if (invite == null || invite.getStatus() != FamilyInviteDTO.Status.PENDING)
            throw new IllegalStateException();
        return invite;
    }

    @Transactional
    @Override
    public int removeFamily(String parent, String child) throws Exception {
        log.info("Calling removeFamily");

        FamilyDTO pDTO = new FamilyDTO();
        pDTO.setParent(parent);
        pDTO.setChild(child);

        return familyMapper.delete(pDTO);
    }

    @Transactional
    @Override
    public int completeOnboarding(String id) throws Exception {
        log.info("Calling completeOnboarding");

        return userMapper.completeOnboarding(UserDTO.fromId(id));
    }
}