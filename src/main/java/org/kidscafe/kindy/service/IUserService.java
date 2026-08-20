package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.FamilyInviteDTO;
import org.kidscafe.kindy.dto.ParentNoteDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.UserDTO;

import java.util.List;

public interface IUserService {
    UserDTO getIdExists(String id) throws Exception;
    UserDTO getEmailExists(String email) throws Exception;
    String sendVerificationCode(String email) throws Exception;
    int create(UserDTO pDTO) throws Exception;
    /** {@link #create} for a child account, plus the T_FAMILY row tying them to {@code parentId}. */
    int createChildFor(String parentId, UserDTO child) throws Exception;
    UserDTO login(String id, String password) throws Exception;
    UserDTO getInfo(String id) throws Exception;
    int update(UserDTO pDTO) throws Exception;
    /** Profile edit a parent may make on their child. Writes NAME, which {@link #update} never does. */
    int updateChildInfo(UserDTO pDTO) throws Exception;
    UserDTO getId(String name, String email) throws Exception;
    UserDTO getId(String name, String email, String id) throws Exception;
    int updatePassword(String id, String password) throws Exception;
    int updateEmail(String id, String email, String password) throws Exception;
    List<DiaryDTO> getDiaries(String id) throws Exception;
    DiaryDTO getDiaryInfo(String id, String date) throws Exception;
    int createDiary(DiaryDTO pDTO) throws Exception;
    int updateDiary(DiaryDTO pDTO) throws Exception;
    int deleteDiary(String id, String date) throws Exception;
    List<FamilyDTO> getFamilies(String id) throws Exception;
    int addFamily(String parent, String child) throws Exception;
    int removeFamily(String parent, String child) throws Exception;

    // Family links by consent. A T_FAMILY row is only ever created by createChildFor or by an
    // accepted request — never by one side declaring it.
    /** Pending requests this user has standing in, each flagged with whether they may answer it. */
    List<FamilyInviteDTO> getFamilyInvites(String userId) throws Exception;
    int requestFamilyLink(String requesterId, String parent, String child) throws Exception;
    int acceptFamilyLink(long id, String userId) throws Exception;
    int rejectFamilyLink(long id, String userId) throws Exception;
    int cancelFamilyLink(long id, String userId) throws Exception;
    int completeOnboarding(String id) throws Exception;
    List<UserDTO.PlainUserDTO> searchUsers(String q) throws Exception;
    List<ParentNoteDTO> getParentNotes(String childId) throws Exception;
    ParentNoteDTO getParentNote(long id) throws Exception;
    int createParentNote(ParentNoteDTO pDTO) throws Exception;
    int deleteParentNote(long id) throws Exception;
    List<ParentNoteDTO.CommentDTO> getParentNoteComments(long noteId) throws Exception;
    ParentNoteDTO.CommentDTO getParentNoteComment(long id) throws Exception;
    int createParentNoteComment(ParentNoteDTO.CommentDTO pDTO) throws Exception;
    int deleteParentNoteComment(long id) throws Exception;
    List<ReportDTO> getReports(String childId) throws Exception;
    int saveReport(ReportDTO pDTO) throws Exception;
}
