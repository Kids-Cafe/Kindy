package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.kidscafe.kindy.dto.UserDTO;

import java.util.List;

@Mapper
public interface IUserMapper {
    List<UserDTO> searchUsers(@Param("q") String q) throws Exception;
    int insertUser(UserDTO pDTO) throws Exception;
    UserDTO getIdExists(UserDTO pDTO) throws Exception;
    UserDTO getEmailExists(UserDTO pDTO) throws Exception;
    UserDTO getLogin(UserDTO pDTO) throws Exception;
    UserDTO getInfo(UserDTO pDTO) throws Exception;
    int updateInfo(UserDTO pDTO) throws Exception;
    int updateChildInfo(UserDTO pDTO) throws Exception;
    UserDTO getId(UserDTO pDTO) throws Exception;
    int updatePassword(UserDTO pDTO) throws Exception;
    int updateEmail(UserDTO pDTO) throws Exception;
    int completeOnboarding(UserDTO pDTO) throws Exception;
}
