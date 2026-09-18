package com.fashion.recommendation.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Separate endpoints allow a failure on one platform without losing the others.
 *
 * <p>Precedence per platform: an imported file beats a configured endpoint, and a configured
 * endpoint beats the platform's public hot board. The built-in hot board is only consulted when
 * nothing is configured, so explicit imports and endpoints always keep full control. Xiaohongshu
 * has no anonymously readable board and stays unconfigured until a file or endpoint is provided.
 */
@Configuration
public class SocialTrendConfiguration {
    @Bean TrendSourceAdapter douyinFeed(ObjectMapper mapper, Environment env) { return source("douyin", mapper, env); }
    @Bean TrendSourceAdapter xiaohongshuFeed(ObjectMapper mapper, Environment env) { return source("xiaohongshu", mapper, env); }
    @Bean TrendSourceAdapter weiboFeed(ObjectMapper mapper, Environment env) { return source("weibo", mapper, env); }
    private TrendSourceAdapter source(String platform, ObjectMapper mapper, Environment env) {
        String url = env.getProperty("app.trends." + platform + "-endpoint", "");
        var adapter = new ConfiguredJsonTrendSourceAdapter(mapper, url, platform,
                Duration.ofSeconds(3), Duration.ofSeconds(8), Duration.ofMinutes(5));
        String directory = env.getProperty("app.trends.import-directory", "");
        boolean hotBoard = env.getProperty("app.trends.hot-boards-enabled", Boolean.class, true)
                && PublicHotBoards.supports(platform);
        return new TrendSourceAdapter() {
            public String platform() { return platform; }
            public boolean emptyResultIsHealthy() { return hotBoard; }
            public java.util.List<TrendItem> fetchPublicSnapshots() {
                java.util.List<TrendItem> items;
                var file = directory.isBlank() ? null : java.nio.file.Path.of(directory).resolve(platform + ".json");
                boolean hasFile = file != null && java.nio.file.Files.isRegularFile(file);
                if (url.isBlank() && hasFile) {
                    try {
                        if (java.nio.file.Files.size(file) > 2_000_000) throw new TrendSourceException("导入文件过大");
                        items = adapter.parseFeed(java.nio.file.Files.readString(file));
                    } catch (java.io.IOException e) { throw new TrendSourceException("无法读取采集文件"); }
                } else items = adapter.fetchPublicSnapshots();
                if (items.isEmpty() && hotBoard && url.isBlank() && !hasFile) {
                    items = PublicHotBoards.fetch(platform, Instant.now());
                }
                if (items.stream().anyMatch(i -> !i.platform().equals(platform)))
                    throw new TrendSourceException("数据源平台与配置不符");
                return items;
            }
        };
    }
}
