package org.kidscafe.kindy.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.service.ITranscriptionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/test")
class TestController {
    private final ITranscriptionService transcriptionService;

    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @ResponseBody
    @PostMapping(value = "transcribe", produces = "text/plain")
    public String transcribe(@RequestParam(value = "file") MultipartFile multipartFile) throws Exception {
        this.callLog("transcribe");
        String result = transcriptionService.transcribe(multipartFile);
        log.info(result);
        return result;
    }
}
