package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.service.IAccessService;
import org.kidscafe.kindy.service.IStorageService;
import org.kidscafe.kindy.service.impl.ClassService;
import org.kidscafe.kindy.util.ImageType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequestMapping(value = "/api/class")
@RequiredArgsConstructor
@RestController
public class ClassController {
    private final ClassService classService;
    private final IAccessService accessService;

    @GetMapping(value = "list")
    public ResultDTO<List<ClassDTO>> list(HttpServletRequest request, HttpSession session) {
        log.debug("Calling list");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canView(kindergartenId, sessionUserId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getList(kindergartenId));
        } catch (Exception e) {
            log.error("list failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<ClassDTO> info(HttpServletRequest request, HttpSession session) {
        log.debug("Calling info");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canViewClass(id, userId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getInfo(id));
        } catch (Exception e) {
            log.error("info failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "create")
    public ResultDTO<Void> create(HttpServletRequest request, HttpSession session) {
        log.debug("Calling create");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long kindergartenId;
        try {
            kindergartenId = Long.parseLong(request.getParameter("kindergartenId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.hasPermission(kindergartenId, userId, RoleDTO.Permission.MANAGE_CLASS))
            return ResultDTO.error("INVALID_ACCESS");

        ClassDTO pDTO = new ClassDTO();
        pDTO.setKindergartenId(kindergartenId);
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
        try {
            int res = classService.create(pDTO);
            if (res == 1) {
                log.info("Class created in kindergarten {}: {} by {}", kindergartenId, pDTO.getName(), userId);
                return ResultDTO.success("CREATE_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.error("create failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "rename")
    public ResultDTO<Void> rename(HttpServletRequest request, HttpSession session) {
        log.debug("Calling rename");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canManageClass(id, userId, RoleDTO.Permission.MANAGE_CLASS))
            return ResultDTO.error("INVALID_ACCESS");

        String name = request.getParameter("name");
        if (name == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            classService.updateName(id, name);
            return ResultDTO.success("UPDATE_COMPLETE");
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.error("rename failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "delete")
    public ResultDTO<Void> delete(HttpServletRequest request, HttpSession session) {
        log.debug("Calling delete");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canManageClass(id, userId, RoleDTO.Permission.MANAGE_CLASS))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            classService.delete(id);
            log.info("Class {} deleted by {}", id, userId);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.error("delete failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "photo/list")
    public ResultDTO<List<PhotoDTO>> photoList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling photoList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long classId;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canViewClass(classId, userId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getPhotos(classId));
        } catch (Exception e) {
            log.error("photoList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "photo/add")
    public ResultDTO<Void> addPhoto(HttpServletRequest request, HttpSession session, MultipartFile file) {
        log.debug("Calling addPhoto");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long classId;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_PHOTO))
            return ResultDTO.error("INVALID_ACCESS");
        if (file == null || file.isEmpty()) return ResultDTO.error("MISSING_PARAMETER");

        // What the browser called it and what it said it was are both claims, made inside a body
        // anything can construct. What the bytes actually are decides the extension, the type
        // stored on the object, and whether we take it at all.
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.warn("Could not read the uploaded file", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }

        ImageType type = ImageType.sniff(content);
        if (type == null) return ResultDTO.error("INVALID_IMAGE");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setClassId(classId);
        pDTO.setAuthor(userId);
        pDTO.setCaption(request.getParameter("caption"));
        pDTO.setTheme(request.getParameter("theme"));
        if (tooLong(pDTO.getCaption())) return ResultDTO.error("INVALID_PARAMETER");

        try {
            classService.addPhoto(pDTO, content, type);
            return ResultDTO.success("ADD_COMPLETE");
        } catch (Exception e) {
            log.error("addPhoto failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * A photo's bytes, for an {@code <img>} tag.
     *
     * <p>This is the only endpoint here that does not answer with a {@link ResultDTO}, and the only
     * one that uses HTTP status codes to say what happened. Both are forced rather than chosen: an
     * {@code <img>} cannot unwrap an envelope or read a code, so "you may not see this" has to be a
     * 403 and "there is nothing here" a 404. Binary answers are not new — {@code chat/speak} and
     * {@code chat/synthesize} already return bytes — but the status codes are, which is why this
     * says so out loud.
     *
     * <p>The permission check runs on <b>every</b> image request rather than once when the album is
     * listed. That is the whole reason photos are proxied instead of handed out as presigned URLs:
     * deleting a photo or losing access to a class takes effect on the next fetch, and a link
     * forwarded to somebody outside the kindergarten is worth nothing to them.
     *
     * <p>The bytes at a photo id never change — an edit only ever touches its caption and theme —
     * so the response is immutable and the ETag is answered before storage is opened at all. A
     * revisit costs one row read and one permission check, and moves no image data. {@code private}
     * on the cache header is load-bearing: this response was authorised for one session and must
     * never be held by a shared cache on the way back.
     */
    @GetMapping(value = "photo/raw")
    public void rawPhoto(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        log.debug("Calling rawPhoto");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        boolean thumbnail = "thumb".equals(request.getParameter("size"));

        try {
            PhotoDTO photo = classService.getPhotoInfo(id);
            if (photo == null || photo.getClassId() == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (!accessService.canViewClass(photo.getClassId(), userId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // The id is enough to tag these bytes: a row's key is written once at upload and never
            // rewritten (photo/edit touches only CAPTION and THEME), and a key is never reused. So
            // one id means one set of bytes, for good. The two sizes are tagged apart because they
            // are two different resources living at two different URLs.
            String etag = "\"" + id + (thumbnail ? "-thumb" : "") + "\"";
            response.setHeader("ETag", etag);
            response.setHeader("Cache-Control", "private, max-age=31536000, immutable");
            if (etag.equals(request.getHeader("If-None-Match"))) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            try (IStorageService.StoredObject object = classService.openPhoto(photo, thumbnail)) {
                if (object == null) {
                    // The row outlived its bytes — a delete that half succeeded, most likely.
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                response.setContentType(object.contentType());
                response.setContentLengthLong(object.length());
                // Nothing here is ever HTML, but these bytes come back from our own origin with the
                // session cookie attached, so a browser must not be left to guess otherwise.
                response.setHeader("X-Content-Type-Options", "nosniff");

                object.stream().transferTo(response.getOutputStream());
            }
        } catch (Exception e) {
            log.warn("Serving photo {} failed", id, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /** {@code T_PHOTO.CAPTION} is VARCHAR(256); past that the insert fails as an opaque error. */
    private static boolean tooLong(String caption) {
        return caption != null && caption.length() > 256;
    }

    // Caption and theme are set at upload time but editable afterwards; omitting one leaves it alone.
    @PostMapping(value = "photo/edit")
    public ResultDTO<Void> editPhoto(HttpServletRequest request, HttpSession session) {
        log.debug("Calling editPhoto");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setId(id);
        pDTO.setCaption(request.getParameter("caption"));
        pDTO.setTheme(request.getParameter("theme"));
        if (pDTO.getCaption() == null && pDTO.getTheme() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (tooLong(pDTO.getCaption())) return ResultDTO.error("INVALID_PARAMETER");

        try {
            if (!this.canEditPhoto(id, userId)) return ResultDTO.error("INVALID_ACCESS");

            classService.updatePhoto(pDTO);
            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.error("editPhoto failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "photo/remove")
    public ResultDTO<Void> removePhoto(HttpServletRequest request, HttpSession session) {
        log.debug("Calling removePhoto");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            if (!this.canEditPhoto(id, userId)) return ResultDTO.error("INVALID_ACCESS");

            classService.removePhoto(id);
            return ResultDTO.success("REMOVE_COMPLETE");
        } catch (Exception e) {
            log.error("removePhoto failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** A photo is the uploader's to change; anyone else needs MANAGE_PHOTO where it was posted. */
    private boolean canEditPhoto(long photoId, String userId) throws Exception {
        PhotoDTO photo = classService.getPhotoInfo(photoId);
        if (photo == null || photo.getClassId() == null) return false;
        if (userId.equals(photo.getAuthor())) return true;

        return accessService.canManageClass(photo.getClassId(), userId, RoleDTO.Permission.MANAGE_PHOTO);
    }

    @GetMapping(value = "supply/list")
    public ResultDTO<List<SupplyDTO>> supplyList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling supplyList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long classId;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canViewClass(classId, userId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getSupplies(classId));
        } catch (Exception e) {
            log.error("supplyList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "supply/info")
    public ResultDTO<SupplyDTO> supplyInfo(HttpServletRequest request, HttpSession session) {
        log.debug("Calling supplyInfo");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            SupplyDTO rDTO = classService.getSupplyInfo(id);
            if (rDTO == null) return ResultDTO.error("NOT_FOUND");
            if (!accessService.canViewClass(rDTO.getClassId(), userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (Exception e) {
            log.error("supplyInfo failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/create")
    public ResultDTO<Void> createSupply(HttpServletRequest request, HttpSession session) {
        log.debug("Calling createSupply");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long classId;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_SUPPLY))
            return ResultDTO.error("INVALID_ACCESS");

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setClassId(classId);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setTitle(request.getParameter("title"));
        if (pDTO.getTitle() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setContent(request.getParameter("content"));
        pDTO.setAuthor(userId);

        try {
            classService.createSupply(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.error("createSupply failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/edit")
    public ResultDTO<Void> editSupply(HttpServletRequest request, HttpSession session) {
        log.debug("Calling editSupply");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // The update is keyed on the supply's id, so the class has to come from the row itself —
        // a `classId` in the request would say nothing about where the supply actually lives.
        Long classId = accessService.getClassOfSupply(id);
        if (classId == null) return ResultDTO.error("NOT_FOUND");
        if (!accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_SUPPLY))
            return ResultDTO.error("INVALID_ACCESS");

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setId(id);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setTitle(request.getParameter("title"));
        if (pDTO.getTitle() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setContent(request.getParameter("content"));

        try {
            classService.updateSupply(pDTO);
            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.error("editSupply failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping("supply/delete")
    public ResultDTO<Void> deleteSupply(HttpServletRequest request, HttpSession session) {
        log.debug("Calling deleteSupply");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long classId = accessService.getClassOfSupply(id);
        if (classId == null) return ResultDTO.error("NOT_FOUND");
        if (!accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_SUPPLY))
            return ResultDTO.error("INVALID_ACCESS");

        try {
            classService.deleteSupply(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.error("deleteSupply failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "supply/comment/list")
    public ResultDTO<List<SupplyDTO.CommentDTO>> supplyCommentList(HttpServletRequest request, HttpSession session) {
        log.debug("Calling supplyCommentList");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long supplyId;
        try {
            supplyId = Long.parseLong(request.getParameter("supplyId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        Long classId = accessService.getClassOfSupply(supplyId);
        if (classId == null) return ResultDTO.error("NOT_FOUND");
        if (!accessService.canViewClass(classId, userId)) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getSupplyComments(supplyId));
        } catch (Exception e) {
            log.error("supplyCommentList failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/comment/create")
    public ResultDTO<Void> createSupplyComment(HttpServletRequest request, HttpSession session) {
        log.debug("Calling createSupplyComment");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long supplyId;
        try {
            supplyId = Long.parseLong(request.getParameter("supplyId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // Commenting is what parents do on a supply notice, so plain class access is enough.
        Long classId = accessService.getClassOfSupply(supplyId);
        if (classId == null) return ResultDTO.error("NOT_FOUND");
        if (!accessService.canViewClass(classId, userId)) return ResultDTO.error("INVALID_ACCESS");

        SupplyDTO.CommentDTO pDTO = new SupplyDTO.CommentDTO();
        pDTO.setSupplyId(supplyId);
        pDTO.setAuthor(userId);
        pDTO.setContent(request.getParameter("content"));
        if (pDTO.getContent() == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            classService.createSupplyComment(pDTO);
            return ResultDTO.success("CREATE_COMPLETE");
        } catch (Exception e) {
            log.error("createSupplyComment failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/comment/delete")
    public ResultDTO<Void> deleteSupplyComment(HttpServletRequest request, HttpSession session) {
        log.debug("Calling deleteSupplyComment");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        try {
            // The author removes their own comment; MANAGE_SUPPLY moderates anyone's.
            SupplyDTO.CommentDTO target = classService.getSupplyComment(id);
            if (target == null) return ResultDTO.error("NOT_FOUND");
            if (!userId.equals(target.getAuthor())) {
                Long classId = accessService.getClassOfSupply(target.getSupplyId());
                if (classId == null || !accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_SUPPLY))
                    return ResultDTO.error("INVALID_ACCESS");
            }

            classService.deleteSupplyComment(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.error("deleteSupplyComment failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
