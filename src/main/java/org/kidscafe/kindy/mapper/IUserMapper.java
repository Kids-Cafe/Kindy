package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.UserDTO;

@Mapper
public interface IUserMapper {

    int insertUser(UserDTO pDTO) throws Exception;

    UserDTO getIdExists(UserDTO pDTO) throws Exception;

    UserDTO getEmailExists(UserDTO pDTO) throws Exception;

    UserDTO getLogin(UserDTO pDTO) throws Exception;

    UserDTO getInfo(UserDTO pDTO) throws Exception;

    int updateInfo(UserDTO pDTO) throws Exception;

    UserDTO getId(UserDTO pDTO) throws Exception;

    int updatePassword(UserDTO pDTO) throws Exception;

    int updateEmail(UserDTO pDTO) throws Exception;
}
