package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ScheduleDTO;

import java.util.List;

@Mapper
public interface IScheduleMapper {
    List<ScheduleDTO> selectList(ScheduleDTO pDTO) throws Exception;
    ScheduleDTO select(ScheduleDTO pDTO) throws Exception;
    int insert(ScheduleDTO pDTO) throws Exception;
    int update(ScheduleDTO pDTO) throws Exception;
    int delete(ScheduleDTO pDTO) throws Exception;
}
