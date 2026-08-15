package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.*;
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

    @GetMapping(value = "list")
    public ResultDTO<List<ClassDTO>> list(HttpServletRequest request, HttpSession session) {
        log.info("Calling list");

        String sessionUserId = (String) session.getAttribute("SESSION_USER_ID");
        if (sessionUserId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Permission check

        try {
            return ResultDTO.success("QUERY_COMPLETE", classService.getList(id));
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

        // TODO: check if the user has access

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

        // TODO: check if the user has access

        ClassDTO pDTO = new ClassDTO();
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

        // TODO: check if the user has access

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

        // TODO: check if the user has access

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

        try {
            classService.addPhoto(classId, file.getResource());
            return ResultDTO.success("ADD_COMPLETE");
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
            classService.removePhoto(id);
            return ResultDTO.success("REMOVE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
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
            return ResultDTO.success("QUERY_COMPLETE", classService.getSupplyInfo(id));
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

        SupplyDTO pDTO = new SupplyDTO();
        pDTO.setClassId(classId);
        pDTO.setDate(request.getParameter("date"));
        if (pDTO.getDate() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setTitle(request.getParameter("title"));
        if (pDTO.getTitle() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setContent(request.getParameter("content"));

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

        long classId, id;
        try {
            classId = Long.parseLong(request.getParameter("classId"));
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

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

        try {
            classService.deleteSupply(id);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
