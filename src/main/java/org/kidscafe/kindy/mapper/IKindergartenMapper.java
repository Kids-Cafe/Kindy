package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.KindergartenDTO;

import java.util.List;

@Mapper
public interface IKindergartenMapper {
    List<KindergartenDTO> getList() throws Exception;
    KindergartenDTO getInfo(KindergartenDTO pDTO) throws Exception;
    int insert(KindergartenDTO pDTO) throws Exception;
    int update(KindergartenDTO pDTO) throws Exception;
    int updateOwner(KindergartenDTO pDTO) throws Exception;
}
