package com.fashion.recommendation.technique;

import com.fashion.recommendation.recommendation.BailianImageGenerationClient;
import com.fashion.recommendation.storage.StoredImageData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

/** Downloads only the fixed provider's public result CDN, never an arbitrary proxy destination. */
@Component
public class TechniqueComparisonImageClient {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private final BailianImageGenerationClient images;

    public TechniqueComparisonImageClient(BailianImageGenerationClient images) { this.images = images; }
    public boolean available() { return images.comparisonAvailable(); }

    public StoredImageData generate(OutfitTechnique item, BooleanSupplier reserveBudget) {
        var result = images.generateTechniqueComparison(
                item.title(), item.example().before(), item.example().after(), reserveBudget);
        if (result == null || !result.succeeded()) throw new IllegalStateException("comparison-generation-failed");
        try {
            URI uri = URI.create(result.imageUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !allowedHost(uri.getHost()))
                throw new IllegalArgumentException("invalid-image-host");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) throw new IllegalArgumentException("private-image-host");
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(20000);
            try {
                if (connection.getResponseCode() != 200) throw new IllegalArgumentException("image-download-failed");
                String type = connection.getContentType();
                if (type == null || !(type.startsWith("image/png") || type.startsWith("image/jpeg") || type.startsWith("image/webp")))
                    throw new IllegalArgumentException("invalid-image-type");
                try (var input = connection.getInputStream()) { return validate(input.readNBytes(MAX_BYTES + 1)); }
            } finally { connection.disconnect(); }
        } catch (Exception exception) { throw new IllegalStateException("comparison-image-unavailable"); }
    }

    static boolean allowedHost(String host) {
        return host != null && host.matches("dashscope-result-[a-z0-9-]+\\.oss-[a-z0-9-]+\\.aliyuncs\\.com");
    }

    static StoredImageData validate(byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("invalid-image-size");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("invalid-image-magic");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!java.util.List.of("png", "jpeg", "jpg", "webp").contains(format) || width < 240 || height < 240
                        || width > 4096 || height > 4096 || (long) width * height > 8_000_000)
                    throw new IllegalArgumentException("invalid-image-dimensions");
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                if (output.size() > MAX_BYTES) throw new IllegalArgumentException("invalid-image-size");
                return new StoredImageData(output.toByteArray(), "image/png");
            } finally { reader.dispose(); }
        }
    }
}
