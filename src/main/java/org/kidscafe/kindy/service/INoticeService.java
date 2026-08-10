package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

public interface INoticeService {
    List<NoticeDTO> getList(long kindergartenId) throws Exception;
    NoticeDTO getInfo(long kindergartenId, int num) throws Exception;
    void create(NoticeDTO pDTO) throws Exception;
    void update(NoticeDTO pDTO) throws Exception;
    void delete(long kindergartenId, int num) throws Exception;
    boolean hasAccess(String userId, long kindergartenId) throws Exception;
}
