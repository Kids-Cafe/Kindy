package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.ClassDTO;
import org.kidscafe.kindy.dto.PhotoDTO;
import org.kidscafe.kindy.dto.SupplyDTO;
import org.kidscafe.kindy.service.IStorageService;
import org.kidscafe.kindy.util.ImageType;

import java.util.List;

public interface IClassService {
    List<ClassDTO> getList(long kindergartenId) throws Exception;
    ClassDTO getInfo(long id) throws Exception;
    int create(ClassDTO pDTO) throws Exception;
    int updateName(long id, String name) throws Exception;
    int delete(long id) throws Exception;
    List<PhotoDTO> getPhotos(long id) throws Exception;
    PhotoDTO getPhotoInfo(long photoId) throws Exception;
    /**
     * Stores the bytes and the row. Takes what the upload <i>is</i> rather than a {@code Resource},
     * because whether it is acceptable at all is decided by sniffing it, and that decision has to
     * produce an error code — which only a controller chooses in this codebase.
     */
    int addPhoto(PhotoDTO pDTO, byte[] content, ImageType type) throws Exception;
    /**
     * Opens a photo's bytes for streaming. Takes the row rather than an id because the caller has
     * already read it to check permission, and asking for the thumbnail of a photo that has none
     * quietly yields the original.
     */
    IStorageService.StoredObject openPhoto(PhotoDTO photo, boolean thumbnail) throws Exception;
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
