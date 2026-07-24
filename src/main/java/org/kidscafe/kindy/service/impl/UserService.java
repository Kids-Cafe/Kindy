package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IUserMapper;
import org.kidscafe.kindy.service.IUserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {

    private final IUserMapper userMapper;
    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @Override
    public UserDTO getUserIdExists(UserDTO pDTO) throws Exception {
        this.callLog("getUserIdExists");

        return userMapper.getUserIdExists(pDTO);
    }

    @Override
    public UserDTO getEmailExists(UserDTO pDTO) throws Exception {
        this.callLog("getEmailExists");

        UserDTO rDTO = Optional.ofNullable(userMapper.getEmailExists(pDTO)).orElseGet(UserDTO::new);

        log.info("rDTO : {}", rDTO);

        return rDTO;
    }

    @Override
    public int insertUserInfo(UserDTO pDTO) throws Exception {
        this.callLog("insertUserInfo");

        return 0;
    }

    @Override
    public UserDTO getLogin(UserDTO pDTO) throws Exception {
        this.callLog("getLogin");

        return Optional.ofNullable(userMapper.getLogin(pDTO)).orElseGet(UserDTO::new);
    }

    @Override
    public UserDTO searchUserIdOrPasswordProc(UserDTO pDTO) throws Exception {
        this.callLog("searchUserIdOrPasswordProc");

        return userMapper.getUserId(pDTO);
    }

    @Override
    public int newPasswordProc(UserDTO pDTO) throws Exception {
        this.callLog("newPasswordProc");

        return userMapper.updatePassword(pDTO);
    }
}