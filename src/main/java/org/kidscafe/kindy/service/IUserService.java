package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.UserDTO;

public interface IUserService {

    UserDTO getIdExists(String id) throws Exception;

    UserDTO getEmailExists(String email) throws Exception;

    int insertUser(UserDTO pDTO) throws Exception;

    UserDTO login(String id, String password) throws Exception;

    UserDTO searchIdOrPassword(UserDTO pDTO) throws Exception;

    int newPassword(String id, String password) throws Exception;

    int updateEmail(UserDTO pDTO) throws Exception;

    UserDTO getInfo(UserDTO pDTO) throws Exception;

    int updateInfo(UserDTO pDTO) throws Exception;
}
