package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.SupplyDTO;

import java.util.List;

@Mapper
public interface ISupplyMapper {
    List<SupplyDTO> selectList(SupplyDTO pDTO) throws Exception;
    SupplyDTO select(SupplyDTO pDTO) throws Exception;
    int insert(SupplyDTO pDTO) throws Exception;
    int update(SupplyDTO pDTO) throws Exception;
    int delete(SupplyDTO pDTO) throws Exception;
}
