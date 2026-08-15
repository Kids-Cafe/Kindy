package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.DiaryDTO;
import org.kidscafe.kindy.dto.FamilyDTO;
import org.kidscafe.kindy.dto.UserDTO;

import java.util.List;

public interface IUserService {
    UserDTO getIdExists(String id) throws Exception;
    UserDTO getEmailExists(String email) throws Exception;
    String sendVerificationCode(String email) throws Exception;
    int create(UserDTO pDTO) throws Exception;
    UserDTO login(String id, String password) throws Exception;
    UserDTO getInfo(String id) throws Exception;
    int update(UserDTO pDTO) throws Exception;
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
}
