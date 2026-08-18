package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.SupplyDTO;

import java.util.List;

@Mapper
public interface ISupplyCommentMapper {
    List<SupplyDTO.CommentDTO> selectList(SupplyDTO.CommentDTO pDTO) throws Exception;
    SupplyDTO.CommentDTO select(SupplyDTO.CommentDTO pDTO) throws Exception;
    int insert(SupplyDTO.CommentDTO pDTO) throws Exception;
    int delete(SupplyDTO.CommentDTO pDTO) throws Exception;
}
