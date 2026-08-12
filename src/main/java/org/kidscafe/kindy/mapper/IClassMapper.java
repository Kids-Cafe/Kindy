package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ClassDTO;

import java.util.List;

@Mapper
public interface IClassMapper {
    List<ClassDTO> selectList(ClassDTO pDTO) throws Exception;
    ClassDTO select(ClassDTO pDTO) throws Exception;
    int insert(ClassDTO pDTO) throws Exception;
    int update(ClassDTO pDTO) throws Exception;
    int delete(ClassDTO pDTO) throws Exception;
}
