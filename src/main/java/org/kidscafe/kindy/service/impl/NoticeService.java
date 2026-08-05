package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.NoticeDTO;
import org.kidscafe.kindy.mapper.INoticeMapper;
import org.kidscafe.kindy.service.INoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class NoticeService implements INoticeService {
    private final INoticeMapper noticeMapper;

    @Override
    public List<NoticeDTO> getNoticeList(NoticeDTO pDTO) throws Exception {
        log.info("Calling getNoticeList");

        return noticeMapper.getNoticeList(pDTO);
    }

    @Override
    public NoticeDTO getNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling getNotice");

        return noticeMapper.getNotice(pDTO);
    }

    @Transactional
    @Override
    public void insertNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling insertNotice");

        noticeMapper.insertNotice(pDTO);
    }

    @Transactional
    @Override
    public void updateNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling updateNotice");

        noticeMapper.updateNotice(pDTO);
    }

    @Transactional
    @Override
    public void deleteNotice(NoticeDTO pDTO) throws Exception {
        log.info("Calling deleteNotice");

        noticeMapper.deleteNotice(pDTO);
    }

    @Override
    public boolean hasAccess(String userId, long kindergartenId) throws Exception {
        if (userId == null) return false;
        // TODO: Check if the user has access
        return true;
    }
}
