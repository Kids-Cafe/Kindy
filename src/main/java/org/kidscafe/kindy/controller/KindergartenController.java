package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.KindergartenDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.service.IKindergartenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping(value = "/api/kindergarten")
@RequiredArgsConstructor
@RestController
public class KindergartenController {
    private final IKindergartenService kindergartenService;

    @GetMapping(value = "list")
    public ResultDTO list() throws Exception {
        log.info("Calling list");

        return ResultDTO.success("QUERY_COMPLETE", kindergartenService.getList());
    }

    @GetMapping(value = "info")
    public ResultDTO info(HttpServletRequest request, HttpSession session) throws Exception {
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

        KindergartenDTO rDTO = kindergartenService.getInfo(id);

        return ResultDTO.success("QUERY_COMPLETE", rDTO);
    }

    @PostMapping(value = "create")
    public ResultDTO create(HttpServletRequest request, HttpSession session) throws Exception {
        log.info("Calling create");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        // TODO: BRN verification

        KindergartenDTO pDTO = new KindergartenDTO();
        pDTO.setName(request.getParameter("name"));
        if (pDTO.getName() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setBrn(request.getParameter("brn"));
        if (pDTO.getBrn() == null) return ResultDTO.error("MISSING_PARAMETER");
        if (pDTO.getBrn().length() != 10) return ResultDTO.error("INVALID_PARAMETER");
        pDTO.setAddress(request.getParameter("address"));
        if (pDTO.getAddress() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setAddressDetail(request.getParameter("addressDetail"));
        if (pDTO.getAddressDetail() == null) return ResultDTO.error("MISSING_PARAMETER");
        pDTO.setPostcode(request.getParameter("postcode"));
        if (pDTO.getPostcode() == null) return ResultDTO.error("MISSING_PARAMETER");
        try {
            int res = kindergartenService.create(pDTO);
            if (res == 1) {
                return ResultDTO.success("REGISTER_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (DuplicateKeyException e) {
            log.info("Duplicate BRN: {}", pDTO.getBrn());
            return ResultDTO.error("DUPLICATE_KEY");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @PostMapping(value = "update")
    public ResultDTO update() {
        log.info("Calling update");
        // TODO: BRN verification
        return ResultDTO.error("NOT_AVAILABLE");
    }

    @PostMapping(value = "transfer")
    public ResultDTO transfer(HttpServletRequest request, HttpSession session) {
        log.info("Calling transfer");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        }

        // TODO: Owner verification

        String owner = (request.getParameter("owner"));
        if (owner == null) return ResultDTO.error("MISSING_PARAMETER");

        try {
            int res = kindergartenService.transfer(id, owner);
            if (res == 1) {
                return ResultDTO.success("TRANSFER_COMPLETE");
            } else {
                return ResultDTO.error("UNKNOWN_ERROR");
            }
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }
}
