package com.fashion.recommendation.technique;

import com.fashion.recommendation.recommendation.BailianImageGenerationClient;
import com.fashion.recommendation.storage.GeneratedImageDownloader;
import com.fashion.recommendation.storage.StoredImageData;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

@Component
public class TechniqueComparisonImageClient {
    static final int MAX_BYTES = GeneratedImageDownloader.MAX_BYTES;
    private final BailianImageGenerationClient images;
    private final GeneratedImageDownloader downloader;

    public TechniqueComparisonImageClient(BailianImageGenerationClient images, GeneratedImageDownloader downloader) {
        this.images = images;
        this.downloader = downloader;
    }

    public boolean available() { return images.comparisonAvailable(); }

    public StoredImageData generate(OutfitTechnique item, BooleanSupplier reserveBudget) {
        var result = images.generateTechniqueComparison(
                item.title(), item.example().before(), item.example().after(), reserveBudget);
        if (result == null || !result.succeeded()) throw new IllegalStateException("comparison-generation-failed");
        return downloader.download(result.imageUrl());
    }

    static boolean allowedHost(String host) { return GeneratedImageDownloader.allowedHost(host); }
    static StoredImageData validate(byte[] bytes) throws Exception { return GeneratedImageDownloader.validate(bytes); }
}
