package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.mapper.IUserMapper;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.EncryptUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements IUserService {
    private final IUserMapper userMapper;
    private final EncryptUtil encryptUtil;

    @Override
    public UserDTO getIdExists(String id) throws Exception {
        log.info("Calling getIdExists");

        return userMapper.getIdExists(UserDTO.fromId(id));
    }

    @Override
    public UserDTO getEmailExists(String email) throws Exception {
        log.info("Calling getEmailExists");

        UserDTO pDTO = new UserDTO();
        pDTO.setEmail(email);

        return userMapper.getEmailExists(pDTO);
    }

    @Transactional
    @Override
    public int insertUser(UserDTO pDTO) throws Exception {
        log.info("Calling insertUser");

        return userMapper.insertUser(pDTO);
    }

    @Override
    public UserDTO login(String id, String password) throws Exception {
        log.info("Calling login");

        UserDTO rDTO = userMapper.getLogin(UserDTO.fromId(id));

        if (rDTO == null || rDTO.getPassword() == null) return null;

        if (!Arrays.equals(encryptUtil.encHashSHA256(password, rDTO.getPasswordSalt()), rDTO.getPassword())) return null;

        rDTO.setPassword(null);
        rDTO.setPasswordSalt(null);

        return rDTO;
    }

    @Override
    public UserDTO searchIdOrPassword(UserDTO pDTO) throws Exception {
        log.info("Calling searchIdOrPassword");

        return userMapper.getId(pDTO);
    }

    @Transactional
    @Override
    public int newPassword(String id, String password) throws Exception {
        log.info("Calling newPassword");

        byte[] salt = encryptUtil.getSecureSalt();
        UserDTO pDTO = UserDTO.fromId(id);
        pDTO.setPassword(encryptUtil.encHashSHA256(password, salt));
        pDTO.setPasswordSalt(salt);

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