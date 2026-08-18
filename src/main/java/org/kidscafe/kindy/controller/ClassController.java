package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
import org.kidscafe.kindy.service.IAccessService;
import org.kidscafe.kindy.service.impl.ClassService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
        log.info("Calling list");

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
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<ClassDTO> info(HttpServletRequest request, HttpSession session) {
        log.info("Calling info");

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
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "create")
    public ResultDTO<Void> create(HttpServletRequest request, HttpSession session) {
        log.info("Calling create");

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
                return ResultDTO.success("CREATE_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "rename")
    public ResultDTO<Void> rename(HttpServletRequest request, HttpSession session) {
        log.info("Calling rename");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "delete")
    public ResultDTO<Void> delete(HttpServletRequest request, HttpSession session) {
        log.info("Calling delete");

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
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "photo/list")
    public ResultDTO<List<PhotoDTO>> photoList(HttpServletRequest request, HttpSession session) {
        log.info("Calling photoList");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "photo/add")
    public ResultDTO<Void> addPhoto(HttpServletRequest request, HttpSession session, MultipartFile file) {
        log.info("Calling addPhoto");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long classId;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        if (!accessService.canManageClass(classId, userId, RoleDTO.Permission.MANAGE_CLASS))
            return ResultDTO.error("INVALID_ACCESS");
        if (file == null || file.isEmpty()) return ResultDTO.error("MISSING_PARAMETER");

        PhotoDTO pDTO = new PhotoDTO();
        pDTO.setClassId(classId);
        pDTO.setAuthor(userId);
        pDTO.setCaption(request.getParameter("caption"));
        pDTO.setTheme(request.getParameter("theme"));

        try {
            classService.addPhoto(pDTO, file.getResource());
            return ResultDTO.success("ADD_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // Caption and theme are set at upload time but editable afterwards; omitting one leaves it alone.
    @PostMapping(value = "photo/edit")
    public ResultDTO<Void> editPhoto(HttpServletRequest request, HttpSession session) {
        log.info("Calling editPhoto");

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

        try {
            if (!this.canEditPhoto(id, userId)) return ResultDTO.error("INVALID_ACCESS");

            classService.updatePhoto(pDTO);
            return ResultDTO.success("EDIT_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "photo/remove")
    public ResultDTO<Void> removePhoto(HttpServletRequest request, HttpSession session) {
        log.info("Calling removePhoto");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** A photo is the uploader's to change; anyone else needs MANAGE_CLASS where it was posted. */
    private boolean canEditPhoto(long photoId, String userId) throws Exception {
        PhotoDTO photo = classService.getPhotoInfo(photoId);
        if (photo == null || photo.getClassId() == null) return false;
        if (userId.equals(photo.getAuthor())) return true;

        return accessService.canManageClass(photo.getClassId(), userId, RoleDTO.Permission.MANAGE_CLASS);
    }

    @GetMapping(value = "supply/list")
    public ResultDTO<List<SupplyDTO>> supplyList(HttpServletRequest request, HttpSession session) {
        log.info("Calling supplyList");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "supply/info")
    public ResultDTO<SupplyDTO> supplyInfo(HttpServletRequest request, HttpSession session) {
        log.info("Calling supplyInfo");

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
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/create")
    public ResultDTO<Void> createSupply(HttpServletRequest request, HttpSession session) {
        log.info("Calling createSupply");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/edit")
    public ResultDTO<Void> editSupply(HttpServletRequest request, HttpSession session) {
        log.info("Calling editSupply");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping("supply/delete")
    public ResultDTO<Void> deleteSupply(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteSupply");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "supply/comment/list")
    public ResultDTO<List<SupplyDTO.CommentDTO>> supplyCommentList(HttpServletRequest request, HttpSession session) {
        log.info("Calling supplyCommentList");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/comment/create")
    public ResultDTO<Void> createSupplyComment(HttpServletRequest request, HttpSession session) {
        log.info("Calling createSupplyComment");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "supply/comment/delete")
    public ResultDTO<Void> deleteSupplyComment(HttpServletRequest request, HttpSession session) {
        log.info("Calling deleteSupplyComment");

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
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
