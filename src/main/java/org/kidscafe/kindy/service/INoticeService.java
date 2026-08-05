package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

public interface INoticeService {

    List<NoticeDTO> getNoticeList(NoticeDTO pDTO) throws Exception;

    NoticeDTO getNotice(NoticeDTO pDTO) throws Exception;

    void insertNotice(NoticeDTO pDTO) throws Exception;

    void updateNotice(NoticeDTO pDTO) throws Exception;

    void deleteNotice(NoticeDTO pDTO) throws Exception;

    boolean hasAccess(String userId, long kindergartenId) throws Exception;
}
