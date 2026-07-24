package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.UserInfoDTO;
import org.kidscafe.kindy.mapper.IUserInfoMapper;
import org.kidscafe.kindy.service.IUserInfoService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserInfoService implements IUserInfoService {

    private final IUserInfoMapper userInfoMapper;
    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @Override
    public UserInfoDTO getUserIdExists(UserInfoDTO pDTO) throws Exception {
        this.callLog("getUserIdExists");

        return userInfoMapper.getUserIdExists(pDTO);
    }

    @Override
    public UserInfoDTO getEmailExists(UserInfoDTO pDTO) throws Exception {
        this.callLog("getEmailExists");

        UserInfoDTO rDTO = Optional.ofNullable(userInfoMapper.getEmailExists(pDTO)).orElseGet(UserInfoDTO::new);

        log.info("rDTO : {}", rDTO);

        return rDTO;
    }

    @Override
    public int insertUserInfo(UserInfoDTO pDTO) throws Exception {
        this.callLog("insertUserInfo");

        return 0;
    }

    @Override
    public UserInfoDTO getLogin(UserInfoDTO pDTO) throws Exception {
        this.callLog("getLogin");

        return Optional.ofNullable(userInfoMapper.getLogin(pDTO)).orElseGet(UserInfoDTO::new);
    }

    @Override
    public UserInfoDTO searchUserIdOrPasswordProc(UserInfoDTO pDTO) throws Exception {
        this.callLog("searchUserIdOrPasswordProc");

        return userInfoMapper.getUserId(pDTO);
    }

    @Override
    public int newPasswordProc(UserInfoDTO pDTO) throws Exception {
        this.callLog("newPasswordProc");

        return userInfoMapper.updatePassword(pDTO);
    }
}