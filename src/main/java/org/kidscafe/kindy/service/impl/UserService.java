package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IUserMapper;
import org.kidscafe.kindy.service.IUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {
    private final IUserMapper userMapper;

    @Override
    public UserDTO getIdExists(UserDTO pDTO) throws Exception {
        log.info("Calling getIdExists");

        return userMapper.getIdExists(pDTO);
    }

    @Override
    public UserDTO getEmailExists(UserDTO pDTO) throws Exception {
        log.info("Calling getEmailExists");

        return userMapper.getEmailExists(pDTO);
    }

    @Transactional
    @Override
    public int insertUser(UserDTO pDTO) throws Exception {
        log.info("Calling insertUser");

        return userMapper.insertUser(pDTO);
    }

    @Override
    public UserDTO login(UserDTO pDTO) throws Exception {
        log.info("Calling login");

        return userMapper.getLogin(pDTO);
    }

    @Override
    public UserDTO searchIdOrPassword(UserDTO pDTO) throws Exception {
        log.info("Calling searchIdOrPassword");

        return userMapper.getId(pDTO);
    }

    @Transactional
    @Override
    public int newPassword(UserDTO pDTO) throws Exception {
        log.info("Calling newPassword");

        return userMapper.updatePassword(pDTO);
    }

    @Override
    public int updateEmail(UserDTO pDTO) throws Exception {
        log.info("Calling updateEmail");

        return userMapper.updateEmail(pDTO);
    }

    @Override
    public UserDTO getInfo(UserDTO pDTO) throws Exception {
        log.info("Calling getInfo");

        return userMapper.getInfo(pDTO);
    }

    @Override
    public int updateInfo(UserDTO pDTO) throws Exception {
        log.info("calling updateInfo");

        return userMapper.updateInfo(pDTO);
    }
}