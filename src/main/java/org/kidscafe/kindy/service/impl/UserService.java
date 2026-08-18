package org.kidscafe.kindy.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.ParentNoteDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IDiaryMapper;
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {
    private final IUserMapper userMapper;
    private final IDiaryMapper diaryMapper;
    private final IFamilyMapper familyMapper;
    private final IParentNoteMapper parentNoteMapper;
    private final IReportMapper reportMapper;
    private final EncryptUtil encryptUtil;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String EMAIL_SENDER;
    private final String EMAIL_VERIFICATION_TITLE = "[Kindy] 인증 메일";
    private final String EMAIL_VERIFICATION_CONTENT = "Kindy 서비스 이용을 위한 인증 메일입니다. 인증 번호: ";

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

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));

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
        if (pDTO.getEmail() == null) throw new NullPointerException();
        if (pDTO.getPhone() == null) throw new NullPointerException();
        if (pDTO.getAccountType() == UserDTO.AccountType.CHILD) {
            if (pDTO.getBirthDate() == null) throw new NullPointerException();
        } else {
            if (pDTO.getAddress() == null) throw new NullPointerException();
            if (pDTO.getAddressDetail() == null) throw new NullPointerException();
            if (pDTO.getPostcode() == null) throw new NullPointerException();
        }

        return userMapper.insertUser(pDTO);
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

        return userMapper.updateInfo(pDTO);
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
                        u.getAccountType(), null, null, null, null, null, null, null))
                .toList();
    }

    @Override
    public List<ParentNoteDTO> getParentNotes(String childId) throws Exception {
        log.info("Calling getParentNotes");

        ParentNoteDTO pDTO = new ParentNoteDTO();
        pDTO.setChildId(childId);

        return parentNoteMapper.selectList(pDTO);
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

    @Transactional
    @Override
    public int addFamily(String parent, String child) throws Exception {
        log.info("Calling addFamily");

        FamilyDTO pDTO = new FamilyDTO();
        pDTO.setParent(parent);
        pDTO.setChild(child);

        return familyMapper.insert(pDTO);
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