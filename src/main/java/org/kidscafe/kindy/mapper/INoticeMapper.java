package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

@Mapper
public interface INoticeMapper {
    List<NoticeDTO> getNoticeList(NoticeDTO pDTO) throws Exception;

    int insertNotice(NoticeDTO pDTO) throws Exception;

    NoticeDTO getNotice(NoticeDTO pDTO) throws Exception;

    int updateNotice(NoticeDTO pDTO) throws Exception;

    int deleteNotice(NoticeDTO pDTO) throws Exception;
}
