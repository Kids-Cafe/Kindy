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
    public UserDTO getInfo(String id) throws Exception {
        log.info("Calling getInfo");

        return userMapper.getInfo(UserDTO.fromId(id));
    }

    @Transactional
    @Override
    public int updateInfo(UserDTO pDTO) throws Exception {
        log.info("calling updateInfo");

        return userMapper.updateInfo(pDTO);
    }

    @Override
    public UserDTO getId(String name, String email) throws Exception {
        log.info("Calling getId");

        UserDTO pDTO = new UserDTO();
        pDTO.setName(name);
        pDTO.setEmail(email);

        return userMapper.getId(pDTO);
    }

    @Override
    public UserDTO getId(String name, String email, String id) throws Exception {
        log.info("Calling getId");

        UserDTO pDTO = UserDTO.fromId(id);
        pDTO.setName(name);
        pDTO.setEmail(email);

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

    @Transactional
    @Override
    public int updateEmail(String id, String email, String password) throws Exception {
        log.info("Calling updateEmail");

        UserDTO pDTO = this.login(id, password);
        if (pDTO == null) throw new IllegalArgumentException();

        pDTO.setEmail(email);

        return userMapper.updateEmail(pDTO);
    }
}