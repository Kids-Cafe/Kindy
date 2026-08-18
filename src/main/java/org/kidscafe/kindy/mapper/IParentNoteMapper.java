package org.kidscafe.kindy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.kidscafe.kindy.dto.ParentNoteDTO;

import java.util.List;

@Mapper
public interface IParentNoteMapper {
    List<ParentNoteDTO> selectList(ParentNoteDTO pDTO) throws Exception;
    ParentNoteDTO select(ParentNoteDTO pDTO) throws Exception;
    int insert(ParentNoteDTO pDTO) throws Exception;
    int delete(ParentNoteDTO pDTO) throws Exception;

    List<ParentNoteDTO.CommentDTO> selectCommentList(ParentNoteDTO.CommentDTO pDTO) throws Exception;
    ParentNoteDTO.CommentDTO selectComment(ParentNoteDTO.CommentDTO pDTO) throws Exception;
    int insertComment(ParentNoteDTO.CommentDTO pDTO) throws Exception;
    int deleteComment(ParentNoteDTO.CommentDTO pDTO) throws Exception;
}
