package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

public interface INoticeService {

    List<NoticeDTO> getNoticeList() throws Exception;

    NoticeDTO getNoticeInfo(NoticeDTO pDTO, boolean type) throws Exception;

    void insertNoticeInfo(NoticeDTO pDTO) throws Exception;

    void updateNoticeInfo(NoticeDTO pDTO) throws Exception;

    void deleteNoticeInfo(NoticeDTO pDTO) throws Exception;

}
