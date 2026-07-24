package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.UserDTO;

@Mapper
public interface IUserMapper {

    int insertUserInfo(UserDTO pDTO) throws Exception;

    UserDTO getUserIdExists(UserDTO pDTO) throws Exception;

    UserDTO getEmailExists(UserDTO pDTO) throws Exception;

    UserDTO getLogin(UserDTO pDTO) throws Exception;

    UserDTO getUserId(UserDTO pDTO) throws Exception;

    int updatePassword(UserDTO pDTO) throws Exception;
}
