package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.NoticeDTO;

import java.util.List;

@Mapper
public interface INoticeMapper {
    List<NoticeDTO> getList(NoticeDTO pDTO) throws Exception;
    int insert(NoticeDTO pDTO) throws Exception;
    NoticeDTO getInfo(NoticeDTO pDTO) throws Exception;
    int update(NoticeDTO pDTO) throws Exception;
    int delete(NoticeDTO pDTO) throws Exception;
}
