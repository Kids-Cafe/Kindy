package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.kidscafe.kindy.dto.KindergartenDTO;

import java.util.List;

@Mapper
public interface IKindergartenMapper {
    List<KindergartenDTO> getList(@Param("q") String q) throws Exception;
    /** Kindergartens this user owns, whether or not they also hold a T_RELATIONSHIP row. */
    List<KindergartenDTO> getListByOwner(@Param("owner") String owner) throws Exception;
    KindergartenDTO getInfo(KindergartenDTO pDTO) throws Exception;
    int insert(KindergartenDTO pDTO) throws Exception;
    int update(KindergartenDTO pDTO) throws Exception;
    int updateOwner(KindergartenDTO pDTO) throws Exception;
}
