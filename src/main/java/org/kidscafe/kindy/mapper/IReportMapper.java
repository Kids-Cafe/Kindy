package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ReportDTO;

import java.util.List;

@Mapper
public interface IReportMapper {
    List<ReportDTO> selectList(ReportDTO pDTO) throws Exception;
    ReportDTO select(ReportDTO pDTO) throws Exception;
    int upsert(ReportDTO pDTO) throws Exception;
    int delete(ReportDTO pDTO) throws Exception;
}
