package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.UserDTO;

public interface IUserService {

    UserDTO getUserIdExists(UserDTO pDTO) throws Exception;

    UserDTO getEmailExists(UserDTO pDTO) throws Exception;

    int insertUserInfo(UserDTO pDTO) throws Exception;

    UserDTO getLogin(UserDTO pDTO) throws Exception;

    UserDTO searchUserIdOrPasswordProc(UserDTO pDTO) throws Exception;

    int newPasswordProc(UserDTO pDTO) throws Exception;
}
