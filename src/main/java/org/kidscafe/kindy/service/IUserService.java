package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.UserDTO;

public interface IUserService {

    UserDTO getIdExists(UserDTO pDTO) throws Exception;

    UserDTO getEmailExists(UserDTO pDTO) throws Exception;

    int insertUser(UserDTO pDTO) throws Exception;

    UserDTO login(UserDTO pDTO) throws Exception;

    UserDTO searchIdOrPassword(UserDTO pDTO) throws Exception;

    int newPassword(UserDTO pDTO) throws Exception;

    int updateEmail(UserDTO pDTO) throws Exception;

    UserDTO getInfo(UserDTO pDTO) throws Exception;

    int updateInfo(UserDTO pDTO) throws Exception;
}
