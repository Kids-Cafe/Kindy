package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.FamilyDTO;

import java.util.List;

@Mapper
public interface IFamilyMapper {
    /** Rows where the user is on <em>either</em> side — callers must filter by direction themselves. */
    List<FamilyDTO> selectList(FamilyDTO pDTO) throws Exception;
    /** The guardians of one child. Reads {@code child} only. */
    List<FamilyDTO> selectParents(FamilyDTO pDTO) throws Exception;
    FamilyDTO select(FamilyDTO pDTO) throws Exception;
    int insert(FamilyDTO pDTO) throws Exception;
    int delete(FamilyDTO pDTO) throws Exception;
}
