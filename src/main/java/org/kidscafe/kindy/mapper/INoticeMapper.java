package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

@Mapper
public interface INoticeMapper {

    List<NoticeDTO> getNoticeList() throws Exception;

    void insertNoticeInfo(NoticeDTO pDTO) throws Exception;

    NoticeDTO getNoticeInfo(NoticeDTO pDTO) throws Exception;

    void updateNoticeReadCnt(NoticeDTO pDTO) throws Exception;

    void updateNoticeInfo(NoticeDTO pDTO) throws Exception;

    void deleteNoticeInfo(NoticeDTO pDTO) throws Exception;

}
