package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.DiaryDTO;

import java.util.List;

@Mapper
public interface IDiaryMapper {
    List<DiaryDTO> selectList(DiaryDTO pDTO) throws Exception;
    DiaryDTO select(DiaryDTO pDTO) throws Exception;
    int insert(DiaryDTO pDTO) throws Exception;
    int update(DiaryDTO pDTO) throws Exception;
    int delete(DiaryDTO pDTO) throws Exception;
}
