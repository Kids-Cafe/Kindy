package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ClassDTO;
import org.kidscafe.kindy.dto.PhotoDTO;
import org.kidscafe.kindy.dto.SupplyDTO;
import org.kidscafe.kindy.mapper.IClassMapper;
import org.kidscafe.kindy.mapper.IPhotoMapper;
import org.kidscafe.kindy.mapper.ISupplyCommentMapper;
import org.kidscafe.kindy.mapper.ISupplyMapper;
import org.kidscafe.kindy.service.IClassService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ClassService implements IClassService {
    private final IClassMapper classMapper;
    private final IPhotoMapper photoMapper;
    private final ISupplyMapper supplyMapper;
    private final ISupplyCommentMapper supplyCommentMapper;

    @Override
    public List<ClassDTO> getList(long kindergartenId) throws Exception {
        log.info("Calling getList");

        ClassDTO pDTO = new ClassDTO();
        pDTO.setKindergartenId(kindergartenId);

        return classMapper.selectList(pDTO);
    }

    @Override
    public ClassDTO getInfo(long id) throws Exception {
        log.info("Calling getInfo");

        ClassDTO pDTO = new ClassDTO();
        pDTO.setId(id);

        return classMapper.select(pDTO);
    }

    @Override
    public int create(ClassDTO pDTO) throws Exception {
        log.info("Calling create");

        return classMapper.insert(pDTO);
    }

    @Override
    public int updateName(long id, String name) throws Exception {
        log.info("Calling updateName");

        ClassDTO pDTO = new ClassDTO();
        pDTO.setId(id);
        pDTO.setName(name);

        return classMapper.update(pDTO);
    }

    @Override
    public int delete(long id) throws Exception {
        log.info("Calling delete");

        ClassDTO pDTO = new ClassDTO();
        pDTO.setId(id);

        return classMapper.delete(pDTO);
    }

    @Override
    public List<PhotoDTO> getPhotos(long id) throws Exception {
        log.info("Calling getPhotos");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setClassId(id);

        return photoMapper.selectList(pDTO);
    }

    @Override
    public PhotoDTO getPhotoInfo(long photoId) throws Exception {
        log.info("Calling getPhotoInfo");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setId(photoId);

        return photoMapper.select(pDTO);
    }

    @Override
    public int addPhoto(PhotoDTO pDTO, Resource resource) throws Exception {
        log.info("Calling addPhoto");

        // TODO: save to cloud
        // pDTO.setUrl("URL");

        pDTO.setUrl(resource.getFilename()); // Temp

        return photoMapper.insert(pDTO);
    }

    @Override
    public int updatePhoto(PhotoDTO pDTO) throws Exception {
        log.info("Calling updatePhoto");

        return photoMapper.update(pDTO);
    }

    @Override
    public int removePhoto(long photoId) throws Exception {
        log.info("Calling removePhoto");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setId(photoId);

        return photoMapper.delete(pDTO);
    }

    @Override
    public List<SupplyDTO> getSupplies(long id) throws Exception {
        log.info("Calling getSupplies");

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setClassId(id);

        return supplyMapper.selectList(pDTO);
    }

    @Override
    public SupplyDTO getSupplyInfo(long supplyId) throws Exception {
        log.info("Calling getSupplyInfo");

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setId(supplyId);

        return supplyMapper.select(pDTO);
    }

    @Override
    public int createSupply(SupplyDTO pDTO) throws Exception {
        log.info("Calling createSupply");

        return supplyMapper.insert(pDTO);
    }

    @Override
    public int updateSupply(SupplyDTO pDTO) throws Exception {
        log.info("Calling updateSupply");

        return supplyMapper.update(pDTO);
    }

    @Override
    public int deleteSupply(long id) throws Exception {
        log.info("Calling deleteSupply");

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setId(id);

        return supplyMapper.delete(pDTO);
    }

    @Override
    public List<SupplyDTO.CommentDTO> getSupplyComments(long supplyId) throws Exception {
        log.info("Calling getSupplyComments");

        SupplyDTO.CommentDTO pDTO = new SupplyDTO.CommentDTO();
        pDTO.setSupplyId(supplyId);

        return supplyCommentMapper.selectList(pDTO);
    }

    @Override
    public SupplyDTO.CommentDTO getSupplyComment(long id) throws Exception {
        log.info("Calling getSupplyComment");

        SupplyDTO.CommentDTO pDTO = new SupplyDTO.CommentDTO();
        pDTO.setId(id);

        return supplyCommentMapper.select(pDTO);
    }

    @Override
    public int createSupplyComment(SupplyDTO.CommentDTO pDTO) throws Exception {
        log.info("Calling createSupplyComment");

        return supplyCommentMapper.insert(pDTO);
    }

    @Override
    public int deleteSupplyComment(long id) throws Exception {
        log.info("Calling deleteSupplyComment");

        SupplyDTO.CommentDTO pDTO = new SupplyDTO.CommentDTO();
        pDTO.setId(id);

        return supplyCommentMapper.delete(pDTO);
    }
}
