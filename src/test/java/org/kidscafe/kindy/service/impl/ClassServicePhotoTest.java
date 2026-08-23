package org.kidscafe.kindy.service.impl;

import org.junit.jupiter.api.Test;
import org.kidscafe.kindy.dto.PhotoDTO;
import org.kidscafe.kindy.mapper.IPhotoMapper;
import org.kidscafe.kindy.service.IStorageService;
import org.kidscafe.kindy.util.ImageType;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the photo half of {@code ClassService} decides on its own: what an object is called, which
 * of the row and the bytes is written first, and which of them survives when the other fails.
 *
 * <p>The ordering tests are the ones worth having. Both directions leave something inconsistent
 * when half of a two-store write fails, and the choice is about <i>which</i> inconsistency a person
 * has to look at — an unreachable object nobody sees, or a broken image nobody can delete. That is
 * a decision, not an implementation detail, so it is pinned here.
 *
 * <p>Fakes rather than a mocking framework: the assertions are about what ended up stored, and a
 * map you can read beats {@code verify} for saying so. The three unrelated mappers are null because
 * nothing here reaches them, exactly as {@code DiaryServiceTest} does.
 */
class ClassServicePhotoTest {
    /** A JPEG header padded out to something ImageIO will refuse — no thumbnail, which is fine here. */
    private static final byte[] JPEG = jpeg();

    private static byte[] jpeg() {
        byte[] content = new byte[64];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        content[3] = (byte) 0xE0;
        return content;
    }

    /** Records what was stored and in what order, so ordering can be asserted directly. */
    private static final class FakeStorage implements IStorageService {
        final Map<String, byte[]> stored = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean refuseDelete;

        @Override
        public void put(String key, String contentType, byte[] content) {
            calls.add("put:" + key);
            stored.put(key, content);
        }

        @Override
        public StoredObject open(String key) {
            calls.add("open:" + key);
            byte[] content = stored.get(key);
            if (content == null) return null;
            return new StoredObject(new ByteArrayInputStream(content), "image/jpeg", content.length);
        }

        @Override
        public void delete(String key) {
            calls.add("delete:" + key);
            if (refuseDelete) throw new IllegalStateException("storage is down");
            stored.remove(key);
        }
    }

    private static final class FakePhotoMapper implements IPhotoMapper {
        final List<PhotoDTO> rows = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        boolean refuseInsert;

        @Override
        public List<PhotoDTO> selectList(PhotoDTO pDTO) {
            return rows.stream().filter(p -> p.getClassId().equals(pDTO.getClassId())).map(FakePhotoMapper::copy).toList();
        }

        @Override
        public PhotoDTO select(PhotoDTO pDTO) {
            return rows.stream().filter(p -> p.getId().equals(pDTO.getId())).findFirst().map(FakePhotoMapper::copy).orElse(null);
        }

        /**
         * MyBatis builds a fresh object per query, so the fake has to as well. Handing back the
         * stored instance would let {@code getPhotos} — which rewrites {@code url} in place — reach
         * into the table and overwrite the key it was reading.
         */
        private static PhotoDTO copy(PhotoDTO source) {
            PhotoDTO copy = new PhotoDTO();
            copy.setId(source.getId());
            copy.setClassId(source.getClassId());
            copy.setUrl(source.getUrl());
            copy.setCaption(source.getCaption());
            copy.setTheme(source.getTheme());
            copy.setAuthor(source.getAuthor());
            copy.setCreatedAt(source.getCreatedAt());
            return copy;
        }

        @Override
        public int insert(PhotoDTO pDTO) {
            calls.add("insert");
            if (refuseInsert) throw new IllegalStateException("the database is down");
            pDTO.setId((long) (rows.size() + 1));
            rows.add(pDTO);
            return 1;
        }

        @Override
        public int update(PhotoDTO pDTO) { return 1; }

        @Override
        public int delete(PhotoDTO pDTO) {
            calls.add("delete");
            return rows.removeIf(p -> p.getId().equals(pDTO.getId())) ? 1 : 0;
        }
    }

    private final FakeStorage storage = new FakeStorage();
    private final FakePhotoMapper photos = new FakePhotoMapper();
    private final ClassService service = new ClassService(null, photos, null, null, storage);

    private PhotoDTO upload(long classId) throws Exception {
        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setClassId(classId);
        pDTO.setAuthor("teacher-1");
        service.addPhoto(pDTO, JPEG, ImageType.JPEG);
        return pDTO;
    }

    @Test
    void namesTheObjectAfterTheClassAndMonthAndNothingTheUserChose() throws Exception {
        PhotoDTO photo = upload(12);

        assertTrue(photo.getUrl().matches("^class/12/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg$"),
                "unexpected key: " + photo.getUrl());
        // Camera-roll filenames carry children's names, so none of the upload's own naming survives.
        assertFalse(photo.getUrl().contains("teacher-1"));
    }

    @Test
    void writesTheBytesBeforeTheRow() throws Exception {
        PhotoDTO photo = upload(12);

        assertEquals("put:" + photo.getUrl(), storage.calls.get(0));
        assertEquals(List.of("insert"), photos.calls);
    }

    @Test
    void keepsNoObjectWhenTheRowCannotBeWritten() {
        photos.refuseInsert = true;

        assertThrows(IllegalStateException.class, () -> upload(12));
        // The caller sees the real failure, and nothing is left behind that nothing points at.
        assertTrue(storage.stored.isEmpty(), "the object should have been cleaned up");
    }

    @Test
    void handsTheAlbumFetchableUrlsRatherThanKeys() throws Exception {
        PhotoDTO uploaded = upload(12);
        String key = uploaded.getUrl();

        PhotoDTO listed = service.getPhotos(12).get(0);
        assertEquals("/api/class/photo/raw?id=1", listed.getUrl());
        assertEquals("/api/class/photo/raw?id=1&size=thumb", listed.getThumbUrl());

        // The other half of the same decision: getPhotoInfo stays raw, because removePhoto and
        // openPhoto need the key. Someone will one day want to "fix" this inconsistency; both
        // halves are pinned so that attempt fails here rather than in production.
        assertEquals(key, service.getPhotoInfo(1).getUrl());
    }

    @Test
    void servesTheOriginalWhenAPhotoHasNoThumbnail() throws Exception {
        PhotoDTO photo = upload(12);

        // These bytes sniff as a JPEG but will not decode, so no thumbnail was ever stored — the
        // same situation a WebP upload is in permanently.
        assertEquals(1, storage.stored.size());
        try (IStorageService.StoredObject object = service.openPhoto(service.getPhotoInfo(1), true)) {
            assertNotNull(object, "asking for a missing thumbnail should fall back to the original");
            assertEquals(JPEG.length, object.length());
        }
        assertTrue(storage.calls.contains("open:" + photo.getUrl()));
    }

    @Test
    void removesBothObjectsWithTheRow() throws Exception {
        PhotoDTO photo = upload(12);
        String thumbnail = photo.getUrl().replaceFirst("\\.jpg$", "-thumb.jpg");

        assertEquals(1, service.removePhoto(1));

        assertTrue(storage.stored.isEmpty());
        assertTrue(storage.calls.contains("delete:" + photo.getUrl()));
        assertTrue(storage.calls.contains("delete:" + thumbnail));
        assertTrue(service.getPhotos(12).isEmpty());
    }

    @Test
    void keepsTheRowDeletedWhenTheObjectWillNotGo() throws Exception {
        upload(12);
        storage.refuseDelete = true;

        // Unreachable bytes are a storage bill; a row whose photo is gone is a broken image with a
        // delete button that does nothing. The request did what was asked, so it succeeds.
        assertEquals(1, service.removePhoto(1));
        assertTrue(service.getPhotos(12).isEmpty());
    }

    @Test
    void doesNothingWhenThePhotoIsAlreadyGone() throws Exception {
        assertEquals(0, service.removePhoto(404));
        assertTrue(storage.calls.isEmpty(), "storage should not have been asked about a missing row");
    }

    @Test
    void findsNothingAtAKeyThatWasNeverStored() throws Exception {
        PhotoDTO missing = new PhotoDTO();
        missing.setUrl("class/12/2026/08/never-written.jpg");

        assertNull(service.openPhoto(missing, false));
    }
}
