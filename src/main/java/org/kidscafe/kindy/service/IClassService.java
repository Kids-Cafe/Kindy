package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ClassDTO;
import org.kidscafe.kindy.dto.PhotoDTO;
import org.kidscafe.kindy.dto.SupplyDTO;
import org.springframework.core.io.Resource;

import java.util.List;

public interface IClassService {
    List<ClassDTO> getList(long kindergartenId) throws Exception;
    ClassDTO getInfo(long id) throws Exception;
    int create(ClassDTO pDTO) throws Exception;
    int updateName(long id, String name) throws Exception;
    int delete(long id) throws Exception;
    List<PhotoDTO> getPhotos(long id) throws Exception;
    int addPhoto(PhotoDTO pDTO, Resource resource) throws Exception;
    int updatePhoto(PhotoDTO pDTO) throws Exception;
    int removePhoto(long photoId) throws Exception;
    List<SupplyDTO> getSupplies(long id) throws Exception;
    SupplyDTO getSupplyInfo(long supplyId) throws Exception;
    int createSupply(SupplyDTO pDTO) throws Exception;
    int updateSupply(SupplyDTO pDTO) throws Exception;
    int deleteSupply(long id) throws Exception;
    List<SupplyDTO.CommentDTO> getSupplyComments(long supplyId) throws Exception;
    SupplyDTO.CommentDTO getSupplyComment(long id) throws Exception;
    int createSupplyComment(SupplyDTO.CommentDTO pDTO) throws Exception;
    int deleteSupplyComment(long id) throws Exception;
}
