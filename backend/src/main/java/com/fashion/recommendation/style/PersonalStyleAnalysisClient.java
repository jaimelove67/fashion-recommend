package com.fashion.recommendation.style;

import com.fashion.recommendation.storage.StoredImageData;

public interface PersonalStyleAnalysisClient {
    PersonalStyleAnalysisResult analyze(StyleProfile profile, StoredImageData photo);
}
