package org.kidscafe.kindy.service;

import org.springframework.web.multipart.MultipartFile;

public interface ITranscriptionService {
    String transcribe(MultipartFile file) throws Exception;
}
