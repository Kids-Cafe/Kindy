package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IUserMapper;
import org.kidscafe.kindy.service.IUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {

    private final IUserMapper userMapper;
    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @Override
    public UserDTO getIdExists(UserDTO pDTO) throws Exception {
        this.callLog("getIdExists");

        return userMapper.getIdExists(pDTO);
    }

    @Override
    public UserDTO getEmailExists(UserDTO pDTO) throws Exception {
        this.callLog("getEmailExists");

        UserDTO rDTO = Optional.ofNullable(userMapper.getEmailExists(pDTO)).orElseGet(UserDTO::new);

        log.info("rDTO: {}", rDTO);

        return rDTO;
    }

    @Transactional
    @Override
    public int insertUser(UserDTO pDTO) throws Exception {
        this.callLog("insertUser");

        return userMapper.insertUser(pDTO);
    }

    @Override
    public UserDTO login(UserDTO pDTO) throws Exception {
        this.callLog("getLogin");

        return Optional.ofNullable(userMapper.getLogin(pDTO)).orElseGet(UserDTO::new);
    }

    @Override
    public UserDTO searchIdOrPassword(UserDTO pDTO) throws Exception {
        this.callLog("searchIdOrPassword");

        return userMapper.getId(pDTO);
    }

    @Transactional
    @Override
    public int newPassword(UserDTO pDTO) throws Exception {
        this.callLog("newPassword");

        return userMapper.updatePassword(pDTO);
    }
}