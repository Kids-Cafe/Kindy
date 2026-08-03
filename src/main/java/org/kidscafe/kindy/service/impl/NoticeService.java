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
    public List<NoticeDTO> getNoticeList() throws Exception {

        log.info("Calling getNoticeList");
        return noticeMapper.getNoticeList();
    }

    @Transactional
    @Override
    public NoticeDTO getNoticeInfo(NoticeDTO pDTO, boolean type) throws Exception {

        log.info("Calling getNoticeInfo");
        if (type) {
            log.info("Update ReadCNT");
            noticeMapper.updateNoticeReadCnt(pDTO);
        }

        return noticeMapper.getNoticeInfo(pDTO);

    }

    @Transactional
    @Override
    public void insertNoticeInfo(NoticeDTO pDTO) throws Exception {

        log.info("Calling insertNoticeInfo");

        noticeMapper.insertNoticeInfo(pDTO);
    }

    @Transactional
    @Override
    public void updateNoticeInfo(NoticeDTO pDTO) throws Exception {

        log.info("Calling updateNoticeInfo");

        noticeMapper.updateNoticeInfo(pDTO);
    }

    @Transactional
    @Override
    public void deleteNoticeInfo(NoticeDTO pDTO) throws Exception {

        log.info("Calling deleteNoticeInfo");

        noticeMapper.deleteNoticeInfo(pDTO);

    }
}
