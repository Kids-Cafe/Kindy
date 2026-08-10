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
    public List<NoticeDTO> getList(long kindergartenId) throws Exception {
        log.info("Calling getList");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(kindergartenId);

        return noticeMapper.getList(pDTO);
    }

    @Override
    public NoticeDTO getInfo(long kindergartenId, int num) throws Exception {
        log.info("Calling getInfo");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setNum(num);

        return noticeMapper.getInfo(pDTO);
    }

    @Transactional
    @Override
    public void create(NoticeDTO pDTO) throws Exception {
        log.info("Calling insert");

        noticeMapper.insert(pDTO);
    }

    @Transactional
    @Override
    public void update(NoticeDTO pDTO) throws Exception {
        log.info("Calling update");

        noticeMapper.update(pDTO);
    }

    @Transactional
    @Override
    public void delete(long kindergartenId, int num) throws Exception {
        log.info("Calling delete");

        NoticeDTO pDTO = new NoticeDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setNum(num);

        noticeMapper.delete(pDTO);
    }

    @Override
    public boolean hasAccess(String userId, long kindergartenId) throws Exception {
        log.info("Calling hasAccess");

        if (userId == null) return false;
        // TODO: Check if the user has access
        return true;
    }
}
