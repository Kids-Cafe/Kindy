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
import org.kidscafe.kindy.service.IStorageService;
import org.kidscafe.kindy.util.ImageType;
import org.kidscafe.kindy.util.Thumbnailer;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class ClassService implements IClassService {
    private final IClassMapper classMapper;
    private final IPhotoMapper photoMapper;
    private final ISupplyMapper supplyMapper;
    private final ISupplyCommentMapper supplyCommentMapper;
    private final IStorageService storageService;

    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    /** Where the browser fetches a photo. Not a storage address — see {@link #getPhotos}. */
    private static final String PHOTO_URL = "/api/class/photo/raw?id=";

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

    /**
     * The album, with every {@code url} rewritten from the stored object key into the address the
     * browser fetches it from.
     *
     * <p>The translation lives here rather than in the controller because its mirror image — bytes
     * to key, in {@link #addPhoto} — is here too, and splitting a pair like that across two layers
     * is how one of them ends up forgotten. Every other method in {@code ClassController} is a
     * permission gate and a parameter parser; none of them shape data, and this should not be the
     * one that starts.
     */
    @Override
    public List<PhotoDTO> getPhotos(long id) throws Exception {
        log.info("Calling getPhotos");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setClassId(id);

        List<PhotoDTO> photos = photoMapper.selectList(pDTO);
        for (PhotoDTO photo : photos) {
            photo.setUrl(PHOTO_URL + photo.getId());
            photo.setThumbUrl(PHOTO_URL + photo.getId() + "&size=thumb");
        }

        return photos;
    }

    /**
     * The row exactly as stored, so {@code url} is still the object key rather than something a
     * browser can fetch.
     *
     * <p>Both callers want it that way: the permission check reads only {@code classId} and
     * {@code author}, and {@link #removePhoto} and {@link #openPhoto} need the key to reach the
     * bytes. Anything headed for a screen must come from {@link #getPhotos} instead.
     */
    @Override
    public PhotoDTO getPhotoInfo(long photoId) throws Exception {
        log.info("Calling getPhotoInfo");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setId(photoId);

        return photoMapper.select(pDTO);
    }

    /**
     * Opens a photo's bytes for streaming back to a browser that has already been allowed to see
     * them.
     *
     * <p>Asking for the thumbnail and getting the original is a normal outcome, not a failure: some
     * formats have no thumbnail (WebP, which {@link Thumbnailer} cannot read), and falling back
     * here means neither the endpoint nor the album has to know which ones.
     */
    @Override
    public IStorageService.StoredObject openPhoto(PhotoDTO photo, boolean thumbnail) throws Exception {
        log.info("Calling openPhoto");

        if (thumbnail) {
            IStorageService.StoredObject thumb = storageService.open(thumbnailKey(photo.getUrl()));
            if (thumb != null) return thumb;
        }

        return storageService.open(photo.getUrl());
    }

    /**
     * The bytes go to storage first and the row second.
     *
     * <p>That order can leave an object nothing points at: if the insert fails, nothing on screen
     * changes and some bytes are simply unreachable. The other order cannot be made to work at all
     * — the URL column is not the file, it is the name the file is about to be written under, so a
     * row inserted before a failed upload is a photo the album shows as a permanently broken image
     * with a delete button that only removes half the problem. An orphan costs storage; a broken
     * row costs a user. The compensating delete below usually clears up even the orphan, and is
     * best effort because the exception the caller sees has to be the real one.
     */
    @Override
    public int addPhoto(PhotoDTO pDTO, byte[] content, ImageType type) throws Exception {
        log.info("Calling addPhoto");

        // Nothing the uploader chose appears in the key: a parsed class id, the server clock, a
        // random uuid and an extension from the sniffed bytes. Camera-roll filenames routinely
        // carry children's names, and CAPTION already exists for anything worth reading.
        String key = "class/" + pDTO.getClassId() + "/"
                + LocalDate.now().format(KEY_MONTH) + "/"
                + UUID.randomUUID() + "." + type.extension();

        storageService.put(key, type.contentType(), content);
        pDTO.setUrl(key);

        // Best effort, and deliberately after the original is safely stored. A photo without a
        // thumbnail costs the grid some bandwidth; a photo that failed to upload costs the photo.
        Thumbnailer.Result thumbnail = Thumbnailer.thumbnail(content, type);
        if (thumbnail != null) {
            try {
                storageService.put(thumbnailKey(key), thumbnail.type().contentType(), thumbnail.content());
            } catch (Exception e) {
                log.warn("Stored {} but not its thumbnail: {}", key, e.toString());
            }
        }

        try {
            return photoMapper.insert(pDTO);
        } catch (Exception e) {
            this.discard(key);
            throw e;
        }
    }

    @Override
    public int updatePhoto(PhotoDTO pDTO) throws Exception {
        log.info("Calling updatePhoto");

        return photoMapper.update(pDTO);
    }

    /**
     * The row goes first and the objects second.
     *
     * <p>The reverse order can show the album a photo whose bytes are already gone — a broken image
     * the user cannot get rid of, because for them the delete failed while for us it half
     * succeeded. This way a failed object delete leaves bytes nobody can reach, which is a storage
     * bill rather than something anyone can see, so it is logged and dropped rather than turned
     * into an error on a request that did what was asked.
     *
     * <p>No {@code @Transactional}: deleting an object cannot join a database transaction, so a
     * rollback would only be able to lie about half of this.
     */
    @Override
    public int removePhoto(long photoId) throws Exception {
        log.info("Calling removePhoto");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setId(photoId);

        PhotoDTO photo = photoMapper.select(pDTO);
        if (photo == null) return 0;

        int removed = photoMapper.delete(pDTO);
        if (removed == 0) return 0;

        this.discard(photo.getUrl());

        return removed;
    }

    /** Removes a photo's objects, both of them, without ever failing the caller. */
    private void discard(String key) {
        try {
            storageService.delete(key);
            storageService.delete(thumbnailKey(key));
        } catch (Exception e) {
            log.warn("Orphaned object {}: {}", key, e.toString());
        }
    }

    /**
     * The thumbnail that belongs to an original.
     *
     * <p>Derived rather than stored, so thumbnails need no column. The extension has to be worked
     * out rather than reused, because {@link Thumbnailer} writes PNGs as PNG to keep transparency
     * and everything else as JPEG — so a {@code .gif} original owns a {@code -thumb.jpg}.
     */
    private static String thumbnailKey(String key) {
        int dot = key.lastIndexOf('.');
        String base = dot < 0 ? key : key.substring(0, dot);
        String extension = dot < 0 ? "" : key.substring(dot + 1).toLowerCase();

        return base + "-thumb." + (extension.equals("png") ? "png" : "jpg");
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
