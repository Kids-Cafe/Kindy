package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.PhotoDTO;

import java.util.List;

@Mapper
public interface IPhotoMapper {
    List<PhotoDTO> selectList(PhotoDTO pDTO) throws Exception;
    PhotoDTO select(PhotoDTO pDTO) throws Exception;
    int insert(PhotoDTO pDTO) throws Exception;
    int update(PhotoDTO pDTO) throws Exception;
    int delete(PhotoDTO pDTO) throws Exception;
}
