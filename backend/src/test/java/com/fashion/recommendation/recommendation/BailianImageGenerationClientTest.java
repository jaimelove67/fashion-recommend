package com.fashion.recommendation.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BailianImageGenerationClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsPrototypeAndWardrobePromptAndPollsWanTask() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        ImageStorage imageStorage = mock(ImageStorage.class);
        when(imageStorage.read("wardrobe/1.png")).thenReturn(new StoredImageData(new byte[] {1, 2, 3}, "image/jpeg"));
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                restClient,
                objectMapper,
                new DefaultResourceLoader(),
                imageStorage,
                "/generate",
                "/tasks",
                "secret-key",
                "wan2.6-image",
                true,
                "classpath:reference_photo/model-male.png",
                "classpath:reference_photo/model-female.png",
                "",
                Duration.ofSeconds(2),
                Duration.ofMillis(1));

        server.expect(requestTo("http://aliyun.test/generate"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("\"model\":\"wan2.6-image\"")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("\"enable_interleave\":false")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("data:image/png;base64,")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("data:image/jpeg;base64,AQID")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("请以参考图中的人物作为模特原型")))
                .andRespond(withSuccess(
                        "{\"request_id\":\"request-1\",\"output\":{\"task_id\":\"task-1\",\"task_status\":\"PENDING\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://aliyun.test/tasks/task-1"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andRespond(withSuccess(
                        "{\"request_id\":\"request-1\",\"output\":{\"task_id\":\"task-1\",\"task_status\":\"SUCCEEDED\",\"choices\":[{\"message\":{\"content\":[{\"type\":\"image\",\"image\":\"https://result.test/look.png\"}]}}]}}",
                        MediaType.APPLICATION_JSON));

        OutfitImageGenerationResult result = client.generate(
                "FEMALE",
                "通勤",
                "杭州",
                24.0,
                List.of(new WardrobeItem(1L, "雾蓝衬衫", "上装", "雾蓝", "极简", null, Instant.now(), "MANUAL", null, "wardrobe/1.png")));

        assertTrue(result.succeeded());
        assertEquals("https://result.test/look.png", result.imageUrl());
        assertEquals("request-1", result.requestId());
        server.verify();
    }

    @Test
    void doesNotCallProviderWhenImageGenerationIsDisabled() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                restClient,
                objectMapper,
                new DefaultResourceLoader(),
                mock(ImageStorage.class),
                "/generate",
                "/tasks",
                "secret-key",
                "wan2.6-image",
                false,
                "classpath:reference_photo/model-male.png",
                "classpath:reference_photo/model-female.png",
                "",
                Duration.ofSeconds(2),
                Duration.ofMillis(1));

        OutfitImageGenerationResult result = client.generate("MALE", "通勤", "杭州", 24.0, List.of());

        assertEquals("UNAVAILABLE", result.status());
        assertEquals("阿里云人物生图未启用", result.message());
        server.verify();
    }
}
