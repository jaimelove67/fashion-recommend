package com.fashion.recommendation.recognition;

import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

public interface GarmentRecognitionService {
    Optional<GarmentRecognitionResult> recognize(MultipartFile image);
}
