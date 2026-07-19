package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.service.ITranscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Service
class TranscriptionService implements ITranscriptionService {
    @Value("${kindy.transcription.url}")
    private String API_URL;

    private final RestClient restClient;

    private final String CLASS_NAME = this.getClass().getName();
    private void callLog(String name) { log.info("Calling {}.{}", CLASS_NAME, name); }

    @Override
    public String transcribe(MultipartFile file) throws Exception {
        this.callLog("transcribe");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", file.getResource());
        parts.add("temperature", "0.0");
        parts.add("response_format", "text");
        parts.add("task", "transcribe");
        parts.add("language", "auto");
        return restClient.post().uri(API_URL).body(parts).retrieve().body(String.class);
    }
}
