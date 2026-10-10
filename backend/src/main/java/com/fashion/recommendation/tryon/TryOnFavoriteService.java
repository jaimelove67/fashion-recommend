package com.fashion.recommendation.tryon;

import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TryOnFavoriteService {
    private final TryOnFavoriteRepository repository;
    private final ImageStorage storage;

    public TryOnFavoriteService(TryOnFavoriteRepository repository, ImageStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public List<TryOnFavorite> list(String userId) { return repository.list(userId); }

    public StoredImageData image(String userId, Long id) {
        var favorite = repository.find(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "收藏图片不存在"));
        return storage.read(favorite.imageObjectKey());
    }

    public TryOnFavorite save(String userId, String expectedUser, MultipartFile image,
            String name, String category, String sourceUrl, String sourceKind) {
        if (!userId.equals(expectedUser)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目账号已变更，请重新连接后收藏");
        }
        if (name == null || name.isBlank() || name.trim().length() > 100) throw invalid("单品名称需要为 1 至 100 字");
        if (category == null || !List.of("上装", "下装", "外套", "连体装", "连衣裙").contains(category)) throw invalid("请选择可试穿的服装类别");
        if (sourceKind == null || !List.of("SHOPPING", "TREND_ILLUSTRATION").contains(sourceKind)) throw invalid("收藏来源不受支持");
        String source = normalizeSource(sourceUrl);
        validateImage(image);
        String fingerprint = fingerprint(image, source);
        var existing = repository.findFingerprint(userId, fingerprint);
        if (existing.isPresent()) return existing.get();
        var stored = storage.store(userId, image);
        try {
            // Each insert commits independently: a duplicate retry must not leave
            // PostgreSQL's transaction in an aborted state before reading the row.
            return repository.create(userId, name.trim(), category, source, sourceKind, stored.objectKey(), fingerprint);
        } catch (RuntimeException cause) {
            try { storage.delete(stored.objectKey()); } catch (RuntimeException ignored) { /* Preserve original failure. */ }
            if (cause instanceof DuplicateKeyException) {
                var concurrent = repository.findFingerprint(userId, fingerprint);
                if (concurrent.isPresent()) return concurrent.get();
            }
            throw cause;
        }
    }

    private static String normalizeSource(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 2048) throw invalid("商品链接过长");
        try {
            URI url = URI.create(value.trim());
            if (url.getScheme() == null || !List.of("http", "https").contains(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null) {
                throw invalid("商品链接需要为 HTTP 或 HTTPS 地址");
            }
            if (url.toASCIIString().length() > 2048) throw invalid("商品链接过长");
            return url.toASCIIString();
        } catch (IllegalArgumentException cause) { throw invalid("商品链接不可用"); }
    }

    private static void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > 10 * 1024 * 1024) throw invalid("请选择不超过 10 MB 的单品图片");
        if (image.getContentType() == null || !List.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType())) throw invalid("仅支持 JPG、PNG 或 WebP 图片");
        try (var input = ImageIO.createImageInputStream(image.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("单品图片内容无效");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String type = format.equals("jpeg") || format.equals("jpg") ? "image/jpeg" : "image/" + format;
                if (!type.equals(image.getContentType())) throw invalid("单品图片格式与内容不一致");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 40_000_000) {
                    throw invalid("单品图片尺寸过大，请缩小后重试");
                }
            } finally { reader.dispose(); }
        } catch (java.io.IOException cause) { throw invalid("单品图片读取失败"); }
    }

    private static String fingerprint(MultipartFile image, String source) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(image.getBytes());
            digest.update((byte) 0);
            digest.update((source == null ? "" : source).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException cause) {
            throw invalid("单品图片读取失败");
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
